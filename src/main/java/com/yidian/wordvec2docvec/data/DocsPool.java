package com.yidian.wordvec2docvec.data;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hipu.news.dynamic.NewsDocument;
import com.yidian.wordvec2docvec.utils.NewsDocumentCache;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import org.codehaus.jackson.JsonNode;
import scala.Int;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by admin on 2018/5/4.
 */
@Log4j
@Data
public class DocsPool {
    protected volatile Map<String, String[]> documentInfoMap = Maps.newConcurrentMap();
    protected volatile Map<String, Integer> wordFreMap = Maps.newConcurrentMap();
    protected volatile HashSet<String> wordSet = new HashSet();


    //    protected volatile LoadingCache<String, List<DocumentInfo>> fetchCache = CacheBuilder.newBuilder()
//            .maximumSize(200000)
//            .recordStats()
//            .build(new CacheLoader<String, List<DocumentInfo>>() {
//                @Override
//                public List<DocumentInfo> load(String fid){
//                    try{
//                        return processFetch(fid);
//                    }catch (NumberFormatException e){
//                        e.printStackTrace();
//                        log.error(e);
//                    }
//                    return processFetch(fid);
//                }
//            });
    private volatile Map<String, Integer> fidCnt = Maps.newHashMap();
    protected volatile ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private static volatile DocsPool instance;


    public static DocsPool defaultInstance() {
        if (instance == null) {
            synchronized (DocsPool.class) {
                if (instance == null) {
                    instance = new DocsPool();
                }
            }
        }
        return instance;
    }

    public DocsPool() {
        init("../data/doc_2018_04.txt", "../data/word_idf.txt","../data/lda.dict.filter.v2");
    }

    private void init(String docFile, String wordFreq, String wordDict) {
        if (docFile == null) {
            System.exit(-1);
        }
        try {
            log.info("load word frequency file:" + wordFreq);
            loadIdfFromFile(wordFreq);
            log.info("word frequency file loaded.");
            log.info("load word frequency file:" + wordDict);
            getDict(wordDict);
            log.info("word frequency file loaded.");
            log.info("load doc file:" + docFile);
            loadDataFromFile(docFile);
            log.info("doc file loaded.");
        } catch (Exception e) {
            log.error(e);
            System.exit(-1);
        }
        if (documentInfoMap.size() == 0) {
            log.error("doc is empty");
            System.exit(-1);
        }
    }


    public boolean loadDataFromFile(String fname) {
        try {
            FileInputStream fis = new FileInputStream(fname);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String data;
            while ((data = br.readLine()) != null) {
                if (data.split("\t").length == 3) {
                    String docid = data.split("\t")[1];
                    String docWords = data.split("\t")[2];
                    String[] docWordsList = docWords.split("\\s+");
                    documentInfoMap.put(docid, docWordsList);
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
        return documentInfoMap.keySet();
    }

    public String[] getPoolWordsByDocid(String docid) {
        return documentInfoMap.get(docid);
    }

    public int getWordFreq(String word) {
        return wordFreMap.get(word);
    }

    public HashSet<String> getWordsDict() {
        return wordSet;
    }
}
