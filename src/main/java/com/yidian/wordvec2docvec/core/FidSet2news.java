package com.yidian.wordvec2docvec.core;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hipu.news.dynamic.NewsDocument;
import com.yidian.wordvec2docvec.data.DocumentFeature;
import com.yidian.wordvec2docvec.data.DocumentInfo;
import com.yidian.wordvec2docvec.data.DocumentsPool;
import com.yidian.wordvec2docvec.utils.DocEmbedding;
import com.yidian.wordvec2docvec.utils.NewsDocumentCache;
import com.yidian.wordvec2docvec.utils.UserActionTool;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.*;
import java.util.List;
import java.util.Map;

@Data
public class FidSet2news {
    private static volatile FidSet2news instance = null;
    private static volatile DocumentsPool pool = new DocumentsPool();
    private static ObjectMapper mapper = new ObjectMapper();

    public static FidSet2news getInstance() {
        if (instance == null) {
            synchronized (FidSet2news.class) {
                if (instance == null) {
                    instance = new FidSet2news();
                }
            }
        }
        return instance;
    }

    private FidSet2news(){
        String fname = "dump.txt";
        File defFile = new File("../data/" + fname);
        if(defFile.exists()){
            loadDataFromFile(fname);
        }
    }

    public boolean addDocument(DocumentFeature doc){
        return pool.addDocument(doc);
    }

    public boolean explire(){
        pool.expire();
        return true;
    }

    public Map<String, Integer> getFidCnt(){
        return pool.getFidCnt();
    }

    public List<DocumentInfo> getDocumentsByFid(String fid){
        return pool.fetch(fid);
    }

    public List<Map<String, String>> recommend(String uid){
        List<String> history = UserActionTool.getInstance().getUserRecentClicks(uid);
        Map<String, NewsDocument> mp = NewsDocumentCache.defaultInstance().getAll(history);
        List<String> fidSet = Lists.newArrayList();
        for(String docid: history){
            if(!mp.containsKey(docid)){
                continue;
            }
            NewsDocument nd = mp.get(docid);
            Map<String, Double> htopics = nd.getHtopics();
            String pTopic = null;
            double pval = 0;
            for(String topic: htopics.keySet()){
                if(!topic.startsWith("t100k")){
                    continue;
                }
                double val = htopics.get(topic);  //need to do this replace?[change]
                if(val > pval){
                    pval = val;
                    pTopic = topic;
                }
            }
            if(pTopic != null){
                fidSet.add(pTopic);
            }
        }

        float [] contexts_vec = new float[256];
        for(int i = 0; i < contexts_vec.length; i++){
            contexts_vec[i] = 0;
        }

        for(String fid: fidSet){
            float [] vec = DocEmbedding.defaultInstance().getContextVec(fid);
            if(vec == null){
                continue;
            }
            for(int i = 0; i < vec.length; i++){
                contexts_vec[i] += vec[i];
            }
        }
        List<Pair<String, Double>> cidSore = Lists.newArrayList();

        for(String fid: FidSet2news.getInstance().getFidCnt().keySet()){  //keySet()??
            float [] vec = DocEmbedding.defaultInstance().getTargetVec(fid);
            if(vec == null){
                continue;
            }
            double tsum = 0;
            for(int i = 0; i < vec.length; i++){
                tsum += contexts_vec[i] * vec[i];
            }
            cidSore.add(Pair.of(fid, tsum));
        }
        cidSore.sort((o1, o2) -> o2.getRight().compareTo(o1.getRight()));
        List<Map<String, String>> lst = Lists.newArrayList();
        for(Pair<String, Double> it: cidSore){
            if(lst.size() > 200){
                break;
            }
            int ct = 0;
            String pfid = it.getKey();
            List<DocumentInfo> clusterDocuments = FidSet2news.getInstance().getDocumentsByFid(pfid);
            for(DocumentInfo one: clusterDocuments){
                ct += 1;
                if(ct > 3){
                    break;
                }
                Map<String, String> tmp = Maps.newHashMap();
                tmp.put("docid", one.getDocid());
                tmp.put("title", one.getTitleWords());
                tmp.put("date",one.getDate().toString()); //[change]
                tmp.put("fid", pfid);
                tmp.put("score", "" + one.getRankScore());
                tmp.put("center_cross", "" + it.getRight());
                lst.add(tmp);
            }
        }
        return lst;
    }

    public Map<String, Integer> findFid(String prefix){
        Map<String, Integer> ret = Maps.newLinkedHashMap();
        List<Pair<String, Integer>> lst = Lists.newArrayList();
        for(Map.Entry<String, Integer> entry: getFidCnt().entrySet()){
            if(entry.getKey().startsWith(prefix)){
                lst.add(Pair.of(entry.getKey(), entry.getValue()));
            }
        }
        lst.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
        for(Pair<String, Integer> pair: lst){
            ret.put(pair.getKey(), pair.getValue());
        }
        return ret;
    }

    public boolean loadDataFromFile(String fname) {
        Map<String, String> ret = Maps.newLinkedHashMap();
        try {
            FileInputStream fis = new FileInputStream("../data/" + fname);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String data = br.readLine();
            JsonNode root = mapper.readTree(data);
            pool.deserialize(root);
            br.close();
            isr.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean invalidCache(){
        return pool.invalidateAll();
    }

    public boolean dumpDataToFile(String fname) {
        try {
            FileOutputStream fos = new FileOutputStream("../data/" + fname);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter bw = new BufferedWriter(osw);
            bw.write(mapper.writeValueAsString(pool.serialize()));
            bw.close();
            osw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

}
