package com.yidian.wordvec2docvec.data;

import com.yidian.wordvec2docvec.utils.DocEmbedding;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.yidian.wordvec2docvec.utils.BM25;
import com.yidian.wordvec2docvec.utils.CosineSim;

import java.io.FileOutputStream;
import java.io.DataOutputStream;

import lombok.extern.log4j.Log4j;

import java.time.LocalTime;


/**
 * Created by admin on 2018/5/6.
 */
@Log4j
public class DocsVecCal {
    private static volatile DocsPool pool;
    private static volatile DocEmbedding docEmb = DocEmbedding.defaultInstance("trainDocVecs");
    private static volatile DocsVecCal instance;

    public static DocsVecCal defaultInstance(String priDocFile, String docVecsFile, int docNum, float avgle) {
        if (instance == null) {
            synchronized (DocsVecCal.class) {
                if (instance == null) {
                    instance = new DocsVecCal(priDocFile, docVecsFile, docNum, avgle);
                }
            }
        }
        return instance;
    }

    // task = trainDocVecs or getRecommend
    public DocsVecCal(String priDocFile, String docVecsFile, int docNum, float avgle) {
        pool = DocsPool.defaultInstance();
        gainDocsVec(priDocFile, docVecsFile, docNum, avgle);
    }

    private void gainDocsVec(String priDocFile, String docVecsFile, int DocNum, float avgle) {
        BM25 bm = new BM25();
        pool.loadDataFromFile(priDocFile);
        String fileName = docVecsFile;
        FileOutputStream fos = null;
        DataOutputStream dos = null;
        try {
            fos = new FileOutputStream(docVecsFile);
            // create data output stream
            dos = new DataOutputStream(fos);

            int i = 0;
            String[] wordsList;
            HashMap<String, float[]> docWordVecMap = new HashMap();
            HashMap<String, Integer> docWordCountMap = new HashMap();
            HashMap<String, Float> docWordWeiMap = new HashMap();
            Iterator iter;
            float[] docidVecTemp = new float[300];
            float[] docidVec = new float[300];

            CosineSim csim = new CosineSim();

            for (String docid : pool.getPoolAllDocids()) {
                LocalTime din = LocalTime.now();
                System.out.println("inthis " + din);
                wordsList = pool.getPoolWordsByDocid(docid).split(" ");
                docWordVecMap.clear();
                docWordCountMap.clear();
                docWordWeiMap.clear();
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
                iter = docWordVecMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();
                    // 获取key
                    String word = (String) entry.getKey();
                    float wordFreq = pool.getWordFreq(word);
                    float wordWei = (float) bm.getBM25((float) DocNum, wordFreq, (float) docWordCountMap.get(word), wordsList.length, avgle);
                    docWordWeiMap.put((String) entry.getKey(), wordWei);
                }

                for (int k = 0; k < 300; k++) {
                    docidVecTemp[k] = 0.0f;
                }
                for (String word : wordsList) {
                    if (docWordVecMap.containsKey(word)) {
                        for (int j = 0; j < 300; j++) {
                            docidVecTemp[j] += docWordWeiMap.get(word) * docWordVecMap.get(word)[j];
                        }
                    }
                }
                float norm = csim.norm(docidVecTemp);
                for (int k = 0; k < 300; k++) {
                    docidVec[k] = docidVecTemp[k] / norm;
                }

                dos.writeBytes(docid + "\t");
                for (float dv : docidVec) {
                    dos.writeFloat(dv);
                    dos.write(' ');
                }
                dos.write('\n');
                i++;
                if (i % 10000 == 0) {
                    log.info("gain doc vec data : line " + i + ":" + docid);
                    dos.flush();
                }
                LocalTime dout = LocalTime.now();
//                System.out.println((dout - din));
                System.out.println("outthis " + dout);
            }
        } catch (Exception e) {
            log.error(e);
        }
        try {
            dos.flush();
            dos.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        private int docNum = 14858382;

//            public DocsVecCal(String task, String priDocFile, String docVecsFile, int docNum, float avgle) {

        String docid = "0IuMEjsk";
//        String posContent = "近年来#AD ,#PU 有#VE 一部#CD 科幻小说#NN 《#PU 三体#NN 》#PU 受到#VV 读者#NN 的#DEC 热烈#AD 追捧#VV 。#PU 甚至#AD facebook#NR 创办人#NN 马克·扎克伯格#NR (#PU markzuckerberg#NR )#PU 的#DEG 阅读#NN 书单#NN ,#PU 2015年#NT 选#VV 的#DEC 是#VC 正是#AD 《#PU 三体#NN 》#PU (#PU the#DT three-body#NN problem#NN )#PU 。#PU 《#PU 三体#NN 》#PU 不#AD 但是#AD 华文#NN 科幻#JJ 的#DEG 最热#JJ 话题#NN ,#PU 作家#NN 刘慈欣#NR 更#AD 成为#VV 第一个#CD 被#SB 好莱坞#NR 买下#VV 电影#NN 改编权#NN 的#DEG 华文#NN 科幻#JJ 作家#NN !#PU 刘慈欣#NR ,#PU 男#JJ ,#PU 汉族#NN ,#PU 1963年#NT 6月#NT 出生#VV ,#PU 1985年#NT 10月#NT 参加#VV 工作#NN ,#PU 山西#NR 阳泉#NR 人#NN ,#PU 本科学历#VV ,#PU 高级工程师#NN ,#PU 科幻#JJ 作家#NN ,#PU 主要#AD 作品#NN 包括#VV 7#CD 部#M 长篇小说#VV ,#PU 9#CD 部#M 作品集#NN ,#PU 16#CD 篇#M 中篇小说#NN";
        DocsVecCal wv = new DocsVecCal( "../data/doc_2018_04.txt", "../data/docsVec.txt", 14858382, 302.3f);
//        System.out.println(pos_content);

    }
}
