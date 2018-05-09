package com.yidian.wordvec2docvec.core;

import java.io.*;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hipu.news.dynamic.NewsDocument;
import com.sun.org.apache.bcel.internal.generic.NEW;
import com.yidian.wordvec2docvec.utils.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.PropertyConfigurator;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONObject;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import com.google.common.collect.Lists;
import com.yidian.wordvec2docvec.data.DocsPool;
import com.yidian.wordvec2docvec.utils.CosineSim;


/**
 * Created by wangyuqin on 2018/5/3.
 */
@Log4j
@Data
public class WordVec2DocVec {
    private static volatile WordVec2DocVec instance = null;
    private static volatile DocsPool pool = DocsPool.defaultInstance();
    private static volatile DocEmbedding docEmb = DocEmbedding.defaultInstance("getRecommend");
    private Pattern patIneerPun = Pattern.compile("[`~!@#$^&*()=|{}':;',\\\\[\\\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？]");
    private Pattern patNum = Pattern.compile("([0-9]\\d*\\.?\\d*)|(0\\.\\d*[1-9])");
    private String posString = "DEC DEV AD DEG DT PU P LC DER VC ETC AS SP IJ MSP CC BA LB UH";
    private String[] myArray = posString.split(" ");
    private HashSet posSet = new HashSet();

    public static WordVec2DocVec getInstance() {
        if (instance == null) {
            synchronized (WordVec2DocVec.class) {
                if (instance == null) {
                    instance = new WordVec2DocVec();
                }
            }
        }
        return instance;
    }

    private WordVec2DocVec() {
        for (String pos : myArray) {
            posSet.add(pos);
        }
    }

    Comparator<Pair<String, Double>> OrderIsdn = new Comparator<Pair<String, Double>>() {
        public int compare(Pair<String, Double> o1, Pair<String, Double> o2) {
            // TODO Auto-generated method stub
            double numbera = o1.getRight();
            double numberb = o2.getRight();
            if (numberb < numbera) {
                return 1;
            } else if (numberb > numbera) {
                return -1;
            } else {
                return 0;
            }
        }
    };

    private List<String> getWordsByDocid(String docid) {
        Optional<String> retOpt = HttpUtils.get("http://cl-k8s.ha.in.yidian.com/apis/docenter/yidian/ids/" + docid + "/fields/url,pos_title,seg_title,pos_content,source,signature,_id,kws,sc_kws'", 300, 3, 5);
        JSONObject jsonObj = new JSONObject(retOpt.get().toString());
        try {
            if (jsonObj.has("result")) {
                String result = jsonObj.get("result").toString();
                JSONObject posObj = new JSONObject(result.substring(1, result.length() - 1));
                if (posObj.has("pos_content")) {
                    String posContent = posObj.get("pos_content").toString();
                    return getArticleFeature(posContent);
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
    }

    private List<String> getArticleFeature(String posContent) {
        String[] posSplited = posContent.split("\\s+");
        List<String> ngramList = new ArrayList();
        if (posSplited.length < 50) {
            return null;
        } else {
            for (int i = 0; i < posSplited.length; ++i) {
                String wordStr;
                String posStr = null;
                String posWord = posSplited[i];
                if (posWord.length() == 0)
                    continue;
                if (posWord.indexOf("#") >= 0) {
                    wordStr = posWord.split("#", 2)[0];
                    posStr = posWord.split("#", 2)[1];
                } else {
                    wordStr = posWord;
                }
                if (posWord.equals("##PU")) {
                    wordStr = "#";
                    posStr = "PU";
                }
                wordStr = StringTools.ToDBC(wordStr);
                Matcher matcherIneerPun = patIneerPun.matcher(wordStr);
                if (matcherIneerPun.find()) {
                    continue;
                }
                Matcher matcherNum = patNum.matcher(wordStr);
                if (matcherNum.find()) {
                    wordStr = matcherNum.replaceAll("\\\\d");
                }
                if (wordStr.length() == 0 || posStr.length() == 0) {
                    continue;
                }
                if (!pool.getWordsDict().contains(wordStr) || posSet.contains(posStr)) {
                    continue;
                }
                ngramList.add(wordStr);
            }
        }
        return ngramList;
    }

    public List<Map<String, String>> recommend(String docid, int DocNum, float avgle) {
        BM25 bm = new BM25();
        CosineSim csim = new CosineSim();

        List<Map<String, String>> recommendForDocid = Lists.newArrayList();
        HashMap<String, float[]> docWordVecMap = new HashMap();
        HashMap<String, Integer> docWordCountMap = new HashMap();
        HashMap<String, Float> docWordWeiMap = new HashMap();
        float[] docidVec = new float[300];
        log.info("get doid's words start");
        List<String> wordsList = getWordsByDocid(docid);
        log.info("get doid's words end");

        if (docEmb.getTargetKey().contains(docid)) {
            log.info("in find  vec " + LocalTime.now());
            docidVec = docEmb.getTargetVec(docid);
            log.info("out find  vec " + LocalTime.now());
        } else {
            log.info("in cal vec " + LocalTime.now());
            float[] vec = new float[300];
            float[] docidVecTemp = new float[300];

            for (String word : wordsList) {
                vec = docEmb.getContextVec(word);
                if (vec == null) {
                    continue;
                }
                if (!docWordVecMap.containsKey(word)) {
                    docWordVecMap.put(word, vec);
                    docWordCountMap.put(word, 1);
                }
            }
            for (String word : docWordCountMap.keySet()) {
                float wordWei = bm.getBM25((float) DocNum, pool.getWordFreq(word), (float) docWordCountMap.get(word), wordsList.size(), avgle);
                docWordWeiMap.put(word, wordWei);
            }
            for (String words : wordsList) {
                if (docWordVecMap.containsKey(words)) {
                    for (int j = 0; j < 300; j++) {
                        docidVecTemp[j] += docWordWeiMap.get(words) * docWordVecMap.get(words)[j];
                    }
                }
            }
            float docidNorm = csim.norm(docidVecTemp);
            for (int j = 0; j < 300; j++) {
                docidVec[j] = docidVecTemp[j] / docidNorm;
            }
            log.info("out cal vec " + LocalTime.now());
        }

        log.info("in cal sim  " + LocalTime.now());
        List<Pair<String, Double>> ret = docEmb.calcContextTargetCross(docidVec);
        log.info("out cal sim " + LocalTime.now());

        log.info("start sort by sim value" + LocalTime.now());
        Queue<Pair<String, Double>> priorityQueue = new PriorityQueue<>(100, OrderIsdn);

        for (Pair<String, Double> re : ret) {
            if (re.getRight() < 1 && re.getRight() > -1) {
                if (priorityQueue.size() < 100) {
                    priorityQueue.add(re);
                } else if (priorityQueue.peek().getRight() < re.getRight()) {
                    priorityQueue.poll();
                    priorityQueue.add(re);
                }
            }
        }

        Set<String> recDocs = new HashSet<>(101);
        List<Pair<String, Double>> recList = Lists.newArrayList();

        for (int k = 0; k < 100; k++) {
            Pair<String, Double> pq = priorityQueue.poll();
            recList.add(pq);
            recDocs.add(pq.getKey());
            System.out.println(pq.getRight());
        }
        log.info("end sort by sim value" + LocalTime.now());
        recDocs.add(docid);

        log.info("start get docs from newsDocumentMap" + LocalTime.now());
        Map<String, NewsDocument> newsDocumentMap = NewsDocumentCache.defaultInstance().getAll(recDocs);
        log.info("end get docs from newsDocumentMap" + LocalTime.now());

        Map<String, String> temp = new HashMap<>();
        temp.put("docid", docid);
        temp.put("selfScore", docid);
        if (!newsDocumentMap.get(docid).equals(Optional.empty())) {
            temp.put("doctitle", newsDocumentMap.get(docid).getTitle());
        }
        recommendForDocid.add(temp);

        log.info("in find words " + LocalTime.now());
        ListIterator<Pair<String, Double>> lit = recList.listIterator();
        while (lit.hasNext()) {
             lit.next();
        }
        while (lit.hasPrevious()) {
            Pair<String, Double> litCur = lit.previous();
            Map<String, String> tmp = new HashMap<>();
            String docCur = litCur.getKey();
            tmp.put("docid", docCur);
            tmp.put("simScore", litCur.getRight().toString());
            if (!newsDocumentMap.get(docCur).equals(Optional.empty())) {
                tmp.put("docTile", newsDocumentMap.get(docCur).getTitle());
            }
            recommendForDocid.add(tmp);
        }
        log.info("out find words " + LocalTime.now());
        return recommendForDocid;
    }

    public static void main(String[] args) {
        File logConfig = new File("log4j.properties");
        if (logConfig.exists()) {
            System.out.println("User config " + logConfig.toString());
            PropertyConfigurator.configure(logConfig.toString());
        }
        String docid = "0IzyShbN";
//        String posContent = "近年来#AD ,#PU 有#VE 一部#CD 科幻小说#NN 《#PU 三体#NN 》#PU 受到#VV 读者#NN 的#DEC 热烈#AD 追捧#VV 。#PU 甚至#AD facebook#NR 创办人#NN 马克·扎克伯格#NR (#PU markzuckerberg#NR )#PU 的#DEG 阅读#NN 书单#NN ,#PU 2015年#NT 选#VV 的#DEC 是#VC 正是#AD 《#PU 三体#NN 》#PU (#PU the#DT three-body#NN problem#NN )#PU 。#PU 《#PU 三体#NN 》#PU 不#AD 但是#AD 华文#NN 科幻#JJ 的#DEG 最热#JJ 话题#NN ,#PU 作家#NN 刘慈欣#NR 更#AD 成为#VV 第一个#CD 被#SB 好莱坞#NR 买下#VV 电影#NN 改编权#NN 的#DEG 华文#NN 科幻#JJ 作家#NN !#PU 刘慈欣#NR ,#PU 男#JJ ,#PU 汉族#NN ,#PU 1963年#NT 6月#NT 出生#VV ,#PU 1985年#NT 10月#NT 参加#VV 工作#NN ,#PU 山西#NR 阳泉#NR 人#NN ,#PU 本科学历#VV ,#PU 高级工程师#NN ,#PU 科幻#JJ 作家#NN ,#PU 主要#AD 作品#NN 包括#VV 7#CD 部#M 长篇小说#VV ,#PU 9#CD 部#M 作品集#NN ,#PU 16#CD 篇#M 中篇小说#NN";
        WordVec2DocVec wv = WordVec2DocVec.getInstance();
        List pos_content = wv.recommend(docid, 14858382, 302.3f);
        System.out.println(pos_content);
    }
}
