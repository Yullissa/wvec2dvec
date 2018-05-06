package com.yidian.wordvec2docvec.core;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jsoup.nodes.Document;
import org.apache.log4j.PropertyConfigurator;
import org.jsoup.Jsoup;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import com.google.common.collect.Lists;
import com.yidian.wordvec2docvec.data.DocsPool;
import com.yidian.wordvec2docvec.utils.DocEmbedding;
import com.yidian.wordvec2docvec.utils.HttpUtils;
import com.yidian.wordvec2docvec.utils.StringTools;

import javax.print.Doc;


/**
 * Created by wangyuqin on 2018/5/3.
 */
@Log4j
@Data
public class WordVec2DocVec {
    private static volatile WordVec2DocVec instance = null;
    private static volatile DocsPool pool = DocsPool.defaultInstance();
    private static volatile DocEmbedding docEmb = DocEmbedding.defaultInstance();
    private static ObjectMapper mapper = new ObjectMapper();
    private Pattern patIneerPun = Pattern.compile("[`~!@#$^&*()=|{}':;',\\\\[\\\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？]");
    private Pattern patNum = Pattern.compile("([0-9]\\d*\\.?\\d*)|(0\\.\\d*[1-9])");
    private String posString = "DEC DEV AD DEG DT PU P LC DER VC ETC AS SP IJ MSP CC BA LB UH";
    private String[] myArray = posString.split(" ");
    private HashSet posSet = new HashSet();
    private float k = 2.0f;
    private float b = 0.75f;

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
        //Todo
//        DocsPool.defaultInstance();
//        DocEmbedding.defaultInstance();
    }


    /*
     N total number of documents
     nqi  number of documents containing qi
     dqi number of qi in document D
     D length of the document D in words
     avgdl the average document length
     IDF = log (N-nqi+0.5)/(nqi+0.5)
     f(qi,D) dqi/D
     f*(k+1)/(f+k(1-b+b*|D|/avgdl))
      */
    public float getBM25(float N, float nqi, float dqi, float D, float avgdl) {
        float f = dqi / D;
        float IDF = (float) Math.log((N - nqi + 0.5f) / (nqi + 0.5f));
        float tftd = f * (k + 1) / (f + k * (1 - b + b * D / avgdl));
        return IDF * tftd;
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
        Map<String, Float> sortedMap = new LinkedHashMap<String, Float>();
        for (Map.Entry<String, Float> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    private List<String> getWordsByDocid(String docid) {
        System.out.println("http://cl-k8s.ha.in.yidian.com/apis/docenter/yidian/ids/" + docid + "/fields/url,pos_title,seg_title,pos_content,source,signature,_id,kws,sc_kws'");
        Optional<String> retOpt = HttpUtils.get("http://cl-k8s.ha.in.yidian.com/apis/docenter/yidian/ids/" + docid + "/fields/url,pos_title,seg_title,pos_content,source,signature,_id,kws,sc_kws'", 300, 3, 5);
        System.out.println(retOpt);
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

    public List<Map<String, String>> recommend(String doc, int DocNum, float avgle) {
        log.warn("Use DocNum = " + DocNum);
        log.warn("Use avgle = " + avgle);
        for (String docid : pool.getPoolAllDocids()) {
            List<String> wordsList = getWordsByDocid(docid);
            HashMap<String, float[]> docWordVecMap = new HashMap();
            HashMap<String, Integer> docWordCountMap = new HashMap();
            HashMap<String, Float> docWordWeiMap = new HashMap();
            List<Map<String, String>> recommendForDocid = Lists.newArrayList();
            HashMap<String, Float> recommendSim = new HashMap<>();
            for (String word : wordsList) {
                float[] vec = docEmb.getContextVec(word);
                if (vec == null) {
                    continue;
                }
                if (!docWordVecMap.containsKey(word)) {
                    docWordVecMap.put(word, vec);
                    docWordCountMap.put(word, 1);
                } else {
                    docWordCountMap.put(word, docWordCountMap.get(word) + 1);
                }
            }

            Iterator iter = docWordVecMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                // 获取key
                String word = (String) entry.getKey();
                int scale = 14858382 / DocNum;
                float wordFreq = pool.getWordFreq(word) / scale;
                float wordWei = (float) getBM25((float) DocNum, wordFreq, (float) docWordCountMap.get(word), wordsList.size(), avgle);
                docWordWeiMap.put((String) entry.getKey(), wordWei);
            }

            float[] docidVec = new float[300];
            for (String word : wordsList) {
                if (docWordVecMap.containsKey(word)) {
                    for (int j = 0; j < 300; j++) {
                        docidVec[j] += docWordWeiMap.get(word) * docWordVecMap.get(word)[j];
                    }
                }
            }
        }



//        for (String doc : pool.getPoolAllDocids()) {
//            if (!doc.equals(docid)) {
//                float[] tempDocVec = new float[300];
//                log.info(doc);
//                String temp = "";
//                for (String word : pool.getPoolWordsByDocid(doc)) {
//                    if (docWordVecMap.containsKey(word)) {
//                        temp += word;
//                        temp += " ";
//                        temp += docWordWeiMap.get(word);
//                        temp += "\t";
//                        for (int j = 0; j < 300; j++) {
//                            tempDocVec[j] += docWordWeiMap.get(word) * docWordVecMap.get(word)[j];
//                        }
//                    }
//                }
//                log.info(temp);
//
//                int counttemp = 0;
//                String temp1 = "";
//                for (float docv : docidVec) {
//                    counttemp++;
//                    if (counttemp >= 10) {
//                        break;
//                    }
//                    temp1 += docv;
//                    temp1 += "\t";
//                }
//                log.info(temp1);
//
//                counttemp = 0;
//                String temp2 = "";
//                for (float docv : docidVec) {
//                    counttemp++;
//                    if (counttemp >= 10) {
//                        break;
//                    }
//                    temp2 += docv;
//                    temp2 += "\t";
//                }
//                log.info(temp2);
//
//                float sim = new CosineSim().cossim(docidVec, tempDocVec);
//                log.info(sim);
//                log.info("\n");
//                if (!Double.isNaN(sim)) {
//                    recommendSim.put(doc, sim);
//                }
//            }
//        }

//        log.info("recdocs have been selected");
//
//        Map<String, Float> sortedSimMap = sortByValue(recommendSim);
//        Map<String, String> temp = new HashMap<>();
//        temp.put("docid", docid);
//        temp.put("selfScore", docid);
//        temp.put("words", StringUtils.join(wordsList, " "));
//        try {
//            Document docText = Jsoup.connect("https://www.yidianzixun.com/article/" + docid).get();
//            temp.put("doctitle", docText.title());
//        } catch (IOException e) {
//            e.printStackTrace();
//            log.error(e);
//        }
//        recommendForDocid.add(temp);
//
//        int recCount = 0;
//        for (String docRec : sortedSimMap.keySet()) {
//            recCount++;
//            if (recCount >= 100) {
//                break;
//            } else {
//                Map<String, String> tmp = new HashMap<>();
//                tmp.put("docid", docRec);
//                tmp.put("score", String.valueOf(sortedSimMap.get(docRec)));
//                tmp.put("words", StringUtils.join(pool.getPoolWordsByDocid(docRec), " "));
//                try {
//                    Document docText = Jsoup.connect("https://www.yidianzixun.com/article/" + docRec).get();
//                    tmp.put("doctitle", docText.title());
//                } catch (IOException e) {
//                    log.error(e);
//                }
//                recommendForDocid.add(tmp);
//            }
//        }
        return recommendForDocid;
    }

    public static void main(String[] args) {
        File logConfig = new File("log4j.properties");
        if (logConfig.exists()) {
            System.out.println("User config " + logConfig.toString());
            PropertyConfigurator.configure(logConfig.toString());
        }
        String docid = "0IuMEjsk";
//        String posContent = "近年来#AD ,#PU 有#VE 一部#CD 科幻小说#NN 《#PU 三体#NN 》#PU 受到#VV 读者#NN 的#DEC 热烈#AD 追捧#VV 。#PU 甚至#AD facebook#NR 创办人#NN 马克·扎克伯格#NR (#PU markzuckerberg#NR )#PU 的#DEG 阅读#NN 书单#NN ,#PU 2015年#NT 选#VV 的#DEC 是#VC 正是#AD 《#PU 三体#NN 》#PU (#PU the#DT three-body#NN problem#NN )#PU 。#PU 《#PU 三体#NN 》#PU 不#AD 但是#AD 华文#NN 科幻#JJ 的#DEG 最热#JJ 话题#NN ,#PU 作家#NN 刘慈欣#NR 更#AD 成为#VV 第一个#CD 被#SB 好莱坞#NR 买下#VV 电影#NN 改编权#NN 的#DEG 华文#NN 科幻#JJ 作家#NN !#PU 刘慈欣#NR ,#PU 男#JJ ,#PU 汉族#NN ,#PU 1963年#NT 6月#NT 出生#VV ,#PU 1985年#NT 10月#NT 参加#VV 工作#NN ,#PU 山西#NR 阳泉#NR 人#NN ,#PU 本科学历#VV ,#PU 高级工程师#NN ,#PU 科幻#JJ 作家#NN ,#PU 主要#AD 作品#NN 包括#VV 7#CD 部#M 长篇小说#VV ,#PU 9#CD 部#M 作品集#NN ,#PU 16#CD 篇#M 中篇小说#NN";
        WordVec2DocVec wv = new WordVec2DocVec();
        List pos_content = wv.recommend(docid, 14858382, 302.3f);
        System.out.println(pos_content);

    }
}
