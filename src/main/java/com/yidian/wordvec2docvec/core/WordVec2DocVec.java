package com.yidian.wordvec2docvec.core;

import java.io.*;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yidian.wordvec2docvec.utils.*;
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
    //TODO
//    private static volatile DocsVecCal dovc = DocsVecCal.defaultInstance(14858382, 302.3f);
    private static volatile DocsPool pool = DocsPool.defaultInstance("getRecommend");
    private static volatile DocEmbedding docEmb = DocEmbedding.defaultInstance();
    private static ObjectMapper mapper = new ObjectMapper();
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


    private static Map<String, Float> sortByValue(Map<String, Float> unsortMap) {
        List<Map.Entry<String, Float>> list =
                new LinkedList<Map.Entry<String, Float>>(unsortMap.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Float>>() {
            public int compare(Map.Entry<String, Float> o1,
                               Map.Entry<String, Float> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });
//        Map<String, Float> sortedMap = new LinkedHashMap<String, Float>();
        HashMap<String, Float> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    private List<String> getWordsByDocid(String docid) {
//        System.out.println("http://cl-k8s.ha.in.yidian.com/apis/docenter/yidian/ids/" + docid + "/fields/url,pos_title,seg_title,pos_content,source,signature,_id,kws,sc_kws'");
        Optional<String> retOpt = HttpUtils.get("http://cl-k8s.ha.in.yidian.com/apis/docenter/yidian/ids/" + docid + "/fields/url,pos_title,seg_title,pos_content,source,signature,_id,kws,sc_kws'", 300, 3, 5);
//        System.out.println(retOpt);
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
        log.warn("Use DocNum = " + DocNum);
        log.warn("Use avgle = " + avgle);
        BM25 bm = new BM25();
        CosineSim csim = new CosineSim();

        List<Map<String, String>> recommendForDocid = Lists.newArrayList();
        HashMap<String, float[]> docWordVecMap = new HashMap();
        HashMap<String, Integer> docWordCountMap = new HashMap();
        HashMap<String, Float> docWordWeiMap = new HashMap();
        float[] docidVec = new float[300];
        log.info("get doid's words start");
        System.out.println(docid);
        List<String> wordsList = getWordsByDocid(docid);
        log.info("get doid's words end");

        if (pool.getDocVecsDocids().contains(docid)) {
            log.info("in find  vec " + LocalTime.now());
            docidVec = pool.getDocVecByDocid(docid);
            log.info("out find  vec " + LocalTime.now());

        } else {
            log.info("in cal vec " + LocalTime.now());
            float[] vec = new float[300];

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

            Iterator iter = docWordVecMap.entrySet().iterator();
            Map.Entry entry;
            String word;
            while (iter.hasNext()) {
                entry = (Map.Entry) iter.next();
                // 获取key
                word = (String) entry.getKey();
                float wordWei = (float) bm.getBM25((float) DocNum, pool.getWordFreq(word), (float) docWordCountMap.get(word), wordsList.size(), avgle);
                docWordWeiMap.put((String) entry.getKey(), wordWei);
            }

            float[] docidVecTemp = new float[300];
            for (int k = 0; k < 300; k++) {
                docidVecTemp[k] = 0;
            }

            for (String words : wordsList) {
                if (docWordVecMap.containsKey(words)) {
                    for (int j = 0; j < 300; j++) {
                        docidVecTemp[j] += docWordWeiMap.get(words) * docWordVecMap.get(words)[j];
                    }
                }
            }

            for (int j = 0; j < 300; j++) {
                docidVec[j] = docidVecTemp[j] / csim.norm(docidVecTemp);
            }
            log.info("out cal vec " + LocalTime.now());
        }


        log.info("in cal sim  " + LocalTime.now());

        float[] tempDocVecFloat = new float[300];
        float sim = 0.0f;
        float[] docSim = new float[100];
        for (int k = 0; k < 100; k++) {
            docSim[k] = -1;
        }
        String[] recVec = new String[100];
        for (String doc : pool.getDocVecsDocids()) {
            if (!doc.equals(docid)) {
                tempDocVecFloat = pool.getDocVecByDocid(doc);
                sim = csim.dot(docidVec, tempDocVecFloat);
                if (!Double.isNaN(sim) && sim <= 1 && sim >= -1 && sim > docSim[99]) {
                    int j = 99;
                    while (j >= 0 && sim > docSim[j]) {
                        j--;
                    }
                    j = j + 1;
                    for (int i = 98; i >= j; i--) {
                        docSim[i + 1] = docSim[i];
                        recVec[i + 1] = recVec[i];
                    }
                    docSim[j] = sim;
                    recVec[j] = doc;
                }
            }
        }

        for (int l = 0; l < 100; l++) {
            System.out.println(recVec[l]);
            System.out.println(docSim[l]);
        }

        log.info("out cal sim " + LocalTime.now());

        log.info("recdocs have been selected" + LocalTime.now());
//        Map<String, Float> sortedSimMap = sortByValue(recommendSim);
        log.info("sorted by sim value" + LocalTime.now());
        Map<String, String> temp = new HashMap<>();
        temp.put("docid", docid);
        temp.put("selfScore", docid);
        temp.put("words", StringUtils.join(wordsList, " "));
        try {
            Document docText = Jsoup.connect("https://www.yidianzixun.com/article/" + docid).get();
            temp.put("doctitle", docText.title());
            System.out.println(docText.title());
            System.out.println(StringUtils.join(wordsList, " "));

        } catch (IOException e) {
            e.printStackTrace();
            log.error(e);
        }
        recommendForDocid.add(temp);

        log.info("in find words " + LocalTime.now());

        for (int k = 0; k < 100; k++) {
            String docRec = recVec[k];

            Map<String, String> tmp = new HashMap<>();

            tmp.put("docid", docRec);
            tmp.put("score", String.valueOf(docSim[k]));
            tmp.put("words", StringUtils.join(getWordsByDocid(docRec), " "));
            try {
                Document docText = Jsoup.connect("https://www.yidianzixun.com/article/" + docRec).get();
                tmp.put("doctitle", docText.title());
                System.out.println(docText.title());
                //                System.out.println(String.valueOf(sortedSimMap.get(docRec)));
                System.out.println(StringUtils.join(getWordsByDocid(docRec), " "));
            } catch (IOException e) {
                log.error(e);
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
