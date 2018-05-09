package com.yidian.wordvec2docvec.data;

import com.google.common.collect.Maps;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import lombok.Data;
import lombok.extern.log4j.Log4j;

import java.io.*;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by admin on 2018/5/4.
 */
@Log4j
@Data
public class DocsPool {
    protected volatile Map<String, String> documentInfoMap = Maps.newConcurrentMap();
    protected volatile Map<String, float[]> docVecInfoMap = Maps.newConcurrentMap();
    protected volatile Map<String, Integer> wordFreMap = Maps.newConcurrentMap();
    protected volatile HashSet<String> wordSet = new HashSet();

    private static volatile DocsPool instance;

    public static DocsPool defaultInstance(String task) {
        if (instance == null) {
            synchronized (DocsPool.class) {
                if (instance == null) {
                    instance = new DocsPool(task);
                }
            }
        }
        return instance;
    }

    public DocsPool(String task) {
        init(task,"../data/word_idf.txt", "../data/lda.dict.filter.v2", "../data/docsVec.txt");
    }

    private void init(String task ,String wordFreq, String wordDict, String docVecFile) {
        try {
            log.info("load word frequency file:" + wordFreq);
            loadIdfFromFile(wordFreq);
            log.info("word frequency file loaded.");
            log.info("load word dict file:" + wordDict);
            getDict(wordDict);
            log.info("word dict file loaded.");
            if (!task.equals("trainDocVecs")) {
                log.info("in DocsPool init" + task);
                log.info("load docVec file:" + docVecFile);
                loadDocsVecFromFile(docVecFile);
                log.info("docVec file loaded.");
            }

            log.info("in DocsPool init" + task);

        } catch (Exception e) {
            log.error(e);
            System.exit(-1);
        }
    }


    public boolean loadDataFromFile(String fname) {
        try {
            FileInputStream fis = new FileInputStream(fname);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String data;
            int i = 0;
            while ((data = br.readLine()) != null) {
                try {
                    i++;
                    String[] data1 = data.split("\t");
                    documentInfoMap.put(data1[1], data1[2]);
                    //14858382
                    if (i % 100000 == 0) {
                        log.info("load prim data : line " + i + ":" + data1[1]);
                    }
                } catch (Exception e) {
                    log.error(e);
                }
            }
            br.close();
            isr.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean loadDocsVecFromFile(String fname) {
        try {
//            docVecInfoMap.clear();
            FileInputStream fis = new FileInputStream(fname);
            DataInputStream dis = new DataInputStream(fis);
            int i = 0;
            byte docidTemp = '0';
            byte[] docid = new byte[15];
            String docId = null;
            byte[] vecFloatTemp = new byte[1500];
            byte[] vecCur = new byte[4];
            while (dis.available() > 0) {
                float[] docVec = new float[300];
                for (int j = 0; j < 15; j++) {
                    if (docidTemp == '\t') {
                        docidTemp = '0';
                        docId = new String(docid, "UTF-8").substring(0, j - 1);
                        break;
                    } else {
                        docidTemp = dis.readByte();
                        docid[j] = docidTemp;
                    }
                }


                dis.read(vecFloatTemp, 0, 1500);

                for (int k = 0; k < 300; k++) {
                    for(int j=0;j<4;j++){
                        vecCur[j] = vecFloatTemp[k*5+j];
                    }
                    docVec[k] = ByteBuffer.wrap(vecCur).getFloat();
                }
                docVecInfoMap.put(docId, docVec);
                dis.skipBytes(1);
                i++;
                if (i % 500000 == 0) {
                    log.info("load doc vec data : line " + i + ":" + docId);
                }
            }
            dis.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        LocalTime dout = LocalTime.now();
        return true;
    }

    public boolean loadIdfFromFile(String fname) {
        try {
            wordFreMap.clear();
            FileInputStream fis = new FileInputStream(fname);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String data;
            while ((data = br.readLine()) != null) {
                if (data.split("\t").length == 2) {
                    wordFreMap.put(data.split("\t")[0], Integer.parseInt(data.split("\t")[1]));
                }
            }
            br.close();
            isr.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private void getDict(String fileName) {
        try {
            wordSet.clear();
            log.info("load Dict...");
            File file = new File(fileName);
            // 读取文件，并且以utf-8的形式写出去
            BufferedReader bufread;
            String word;
            bufread = new BufferedReader(new FileReader(file));
            while ((word = bufread.readLine()) != null) {
                wordSet.add(word);
            }
            bufread.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Set<String> getPoolAllDocids() {
        if (docVecInfoMap.size() != 0) {
            return docVecInfoMap.keySet();
        } else if (documentInfoMap.size() != 0) {
            return documentInfoMap.keySet();
        }
        return null;
    }

    public Set<String> getDocVecsDocids() {
        if (docVecInfoMap.size() != 0) {
            return docVecInfoMap.keySet();
        }
        return null;
    }


    public String getPoolWordsByDocid(String docid) {
        return documentInfoMap.get(docid);
    }

    public float[] getDocVecByDocid(String docid) {
        return docVecInfoMap.get(docid);
    }

    public int getWordFreq(String word) {
        return wordFreMap.get(word);
    }

    public HashSet<String> getWordsDict() {
        return wordSet;
    }

    public static void main(String[] args) {
//        private int docNum = 14858382;

//            public DocsVecCal(String task, String priDocFile, String docVecsFile, int docNum, float avgle) {

        String docid = "0IuMEjsk";
//        String posContent = "近年来#AD ,#PU 有#VE 一部#CD 科幻小说#NN 《#PU 三体#NN 》#PU 受到#VV 读者#NN 的#DEC 热烈#AD 追捧#VV 。#PU 甚至#AD facebook#NR 创办人#NN 马克·扎克伯格#NR (#PU markzuckerberg#NR )#PU 的#DEG 阅读#NN 书单#NN ,#PU 2015年#NT 选#VV 的#DEC 是#VC 正是#AD 《#PU 三体#NN 》#PU (#PU the#DT three-body#NN problem#NN )#PU 。#PU 《#PU 三体#NN 》#PU 不#AD 但是#AD 华文#NN 科幻#JJ 的#DEG 最热#JJ 话题#NN ,#PU 作家#NN 刘慈欣#NR 更#AD 成为#VV 第一个#CD 被#SB 好莱坞#NR 买下#VV 电影#NN 改编权#NN 的#DEG 华文#NN 科幻#JJ 作家#NN !#PU 刘慈欣#NR ,#PU 男#JJ ,#PU 汉族#NN ,#PU 1963年#NT 6月#NT 出生#VV ,#PU 1985年#NT 10月#NT 参加#VV 工作#NN ,#PU 山西#NR 阳泉#NR 人#NN ,#PU 本科学历#VV ,#PU 高级工程师#NN ,#PU 科幻#JJ 作家#NN ,#PU 主要#AD 作品#NN 包括#VV 7#CD 部#M 长篇小说#VV ,#PU 9#CD 部#M 作品集#NN ,#PU 16#CD 篇#M 中篇小说#NN";
        DocsPool wv = new DocsPool("getRecommend");
//        System.out.println(pos_content);

    }
}
