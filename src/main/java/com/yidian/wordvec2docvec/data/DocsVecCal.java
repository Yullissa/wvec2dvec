package com.yidian.wordvec2docvec.data;

import com.yidian.wordvec2docvec.utils.DocEmbedding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.yidian.wordvec2docvec.utils.BM25;

/**
 * Created by admin on 2018/5/6.
 */
public class DocsVecCal {
    private static volatile DocsPool pool;
    private static volatile DocEmbedding docEmb = DocEmbedding.defaultInstance();
    private static volatile DocsVecCal instance;

    public static DocsVecCal defaultInstance(String task,String priDocFile, String docVecsFile,int docNum,float avgle) {
        if (instance == null) {
            synchronized (DocsVecCal.class) {
                if (instance == null) {
                    instance = new DocsVecCal(task,priDocFile,docVecsFile,docNum,avgle);
                }
            }
        }
        return instance;
    }

    public DocsVecCal(String task,String priDocFile,String docVecsFile,int docNum,float avgle) {
         pool = DocsPool.defaultInstance(task);
        getDocsVec(priDocFile,docVecsFile,docNum,avgle);
    }

    public void getDocsVec(String priDocFile,String docVecsFile,int DocNum, float avgle) {
        BM25 bm = new BM25();
        pool.loadDataFromFile(priDocFile);
        for (String docid : pool.getPoolAllDocids()) {
            String[] wordsList = pool.getPoolWordsByDocid(docid);
            HashMap<String, float[]> docWordVecMap = new HashMap();
            HashMap<String, Integer> docWordCountMap = new HashMap();
            HashMap<String, Float> docWordWeiMap = new HashMap();
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
                float wordWei = (float) bm.getBM25((float) DocNum, wordFreq, (float) docWordCountMap.get(word), wordsList.length, avgle);
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
            String fileName = docVecsFile;
            FileWriter fw = null;
            try {
                //如果文件存在，则追加内容；如果文件不存在，则创建文件
                File f = new File(fileName);
                fw = new FileWriter(f, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
            PrintWriter pw = new PrintWriter(fw);
            pw.print(docid + "\t");
            for (float dv : docidVec) {
                pw.print(dv + " ");
            }
            pw.println();
            pw.flush();
            try {
                fw.flush();
                pw.close();
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
