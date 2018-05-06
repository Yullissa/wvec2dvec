package com.yidian.wordvec2docvec.data;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.extern.log4j.Log4j;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by admin on 2018/5/4.
 */
@Log4j
@Data
public class DocsPool {
    protected volatile Map<String, String[]> documentInfoMap = Maps.newConcurrentMap();
    protected volatile Map<String, String[]> docVecInfoMap = Maps.newConcurrentMap();
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
        init(task, "../data/word_idf.txt", "../data/lda.dict.filter.v2", "../data/docsVec.txt");
    }

    private void init(String task, String wordFreq, String wordDict, String docVecFile) {
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
        } catch (Exception e) {
            log.error(e);
            System.exit(-1);
        }
    }


    public boolean loadDataFromFile(String fname) {
        try {
            docVecInfoMap.clear();
            FileInputStream fis = new FileInputStream(fname);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String data;
            int i=0;
            while ((data = br.readLine()) != null) {
                if (data.split("\t").length == 3) {
                    i++;
                    String docid = data.split("\t")[1];
                    String docWords = data.split("\t")[2];
                    String[] docWordsList = docWords.split("\\s+");
                    documentInfoMap.put(docid, docWordsList);
                    //14858382
                    if (i % 100000 == 0) {
                        log.info("load prim data : line " + i + ":" + docid);
                    }
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
            docVecInfoMap.clear();
            FileInputStream fis = new FileInputStream(fname);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String data;
            while ((data = br.readLine()) != null) {
                if (data.split("\t").length == 2) {
                    String docid = data.split("\t")[0];
                    String docVec = data.split("\t")[1];
                    String[] docVecList = docVec.split("\\s+");
                    docVecInfoMap.put(docid, docVecList);
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

    public boolean loadIdfFromFile(String fname) {
        try {
            wordFreMap.clear();
            FileInputStream fis = new FileInputStream(fname);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String data;
            while ((data = br.readLine()) != null) {
                if (data.split("\t").length == 2) {
                    String word = data.split("\t")[0];
                    String wordFrequence = data.split("\t")[1];
                    wordFreMap.put(word, Integer.parseInt(wordFrequence));
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


    public String[] getPoolWordsByDocid(String docid) {
        return documentInfoMap.get(docid);
    }

    public String[] getDocVecByDocid(String docid) {
        return docVecInfoMap.get(docid);
    }

    public int getWordFreq(String word) {
        return wordFreMap.get(word);
    }

    public HashSet<String> getWordsDict() {
        return wordSet;
    }
}
