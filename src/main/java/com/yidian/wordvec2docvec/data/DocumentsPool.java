package com.yidian.wordvec2docvec.data;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hipu.news.dynamic.ClickFeedbacks;
import com.hipu.news.dynamic.NewsDocument;
import com.hipu.news.dynamic.Scope;
import com.hipu.news.dynamic.dao.NewsDocumentDAO;
import com.yidian.wordvec2docvec.utils.DocEmbedding;
import com.yidian.wordvec2docvec.utils.NewsDocumentCache;
import com.yidian.wordvec2docvec.utils.StringTools;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.jackson.JsonNode;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Log4j
@Data
public class DocumentsPool {
    protected volatile Map<String, DocumentInfo> documentInfoMap = Maps.newConcurrentMap();
    protected volatile LoadingCache<String, List<DocumentInfo>> fetchCache = CacheBuilder.newBuilder()
            .maximumSize(200000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build(new CacheLoader<String, List<DocumentInfo>>() {
                @Override
                public List<DocumentInfo> load(String fid){
                    try{
                        return processFetch(fid);
                    }catch (NumberFormatException e){
                        e.printStackTrace();
                        log.error(e);
                    }
                    return processFetch(fid);
                }
            });
    private volatile Map<String, Integer> fidCnt = Maps.newHashMap();
    protected volatile ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    protected static final int oneDay = 1440; //min
    protected static final int threeDay = 4320;
    protected static final int sevenDay = 10080;

    public DocumentsPool(){
    }

    public boolean addDocument(DocumentFeature doc) {
        readWriteLock.readLock().lock();
        try {
            int tier = doc.getTier();
            String docid = doc.getDocid();
            String sig = doc.getSig();
            String title_words = DocEmbedding.getSegTitleWords(doc.getSeg_title());
            String refer = doc.getRefer();
            Date date = new Date();
            SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            if (doc.getDate() != null) {
                try {
                    date = simpleFormat.parse(doc.getDate());
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            if (!documentInfoMap.containsKey(docid)) {
                DocumentInfo pdoc = new DocumentInfo(docid, title_words, sig, tier, date, refer);
                pdoc.setHtopics(doc.getHtopics());
                pdoc.setHclusters(doc.getHclusters());
                if(docid.startsWith("O_")){
                    return false;
                }
                List<DocumentInfo> dupDocuments = Lists.newArrayList();
                dupDocuments.add(pdoc);
                for (DocumentInfo documentInfo : documentInfoMap.values()) {
                    String key = documentInfo.getDocid();
                    java.util.Optional<Boolean> val;
                    double sigScore = StringTools.signatureScore(sig, documentInfo.getSig());
                    double jaccardScore = DocEmbedding.JaccardScore(title_words, documentInfo.getTitleWords());
                    if (sigScore > 0.95 || jaccardScore > 0.85) {
                        val = java.util.Optional.of(true);
                    } else {
                        val = java.util.Optional.of(false);
                    }
                    if(val.isPresent() && val.get()){
                        dupDocuments.add(documentInfo);
                    }
                }
                dupDocuments.sort((o1, o2) -> {
                    if (o1.getTier() != o2.getTier()) {
                        return o2.getTier() - o1.getTier();
                    }
                    return 1;
                });

                for (DocumentInfo documentInfo : dupDocuments) {
                    documentInfoMap.remove(documentInfo.getDocid());
                }
                for (DocumentInfo documentInfo : dupDocuments.subList(1, dupDocuments.size())) {
                    log.info("pool: "  + " d " + documentInfo.getDocid() + " r " + dupDocuments.get(0).getDocid());
                }
                DocumentInfo documentInfo = dupDocuments.get(0);
                documentInfoMap.put(documentInfo.getDocid(), documentInfo);
                return true;
            } else {
                if (!doc.getRefer().equals("pipline")) {
                    DocumentInfo pdoc = documentInfoMap.get(doc.getDocid());
                    pdoc.setRefer(doc.getRefer());
                }else {
                    log.info("doc " + doc.getDocid() + " already in pool");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            readWriteLock.readLock().unlock();
        }
        return false;
    }



    public List<DocumentInfo> expire() {
        readWriteLock.writeLock().lock();
        Map<String, Integer> fct = Maps.newHashMap();
        List<DocumentInfo> ret = Lists.newArrayList();
        try {
            Date now = new Date();
            Map<String, NewsDocument> newsDocumentMap = NewsDocumentDAO.defaultNewsDocumentDAO().getNewsDocumentMap(documentInfoMap.keySet());
            List<Pair<String, String>> delDocs = Lists.newArrayList();
            for (DocumentInfo documentInfo : documentInfoMap.values()) {
                for(Map.Entry<String, Double> entry: documentInfo.getHtopics().entrySet()){
                    if(entry.getValue() < 0.15){  // 0.15??
                        continue;
                    }
                    if(!fct.containsKey(entry.getKey())){
                        fct.put(entry.getKey(), 0);
                    }
                    fct.put(entry.getKey(), fct.get(entry.getKey()) + 1);
                }
                for(Map.Entry<String, Double> entry: documentInfo.getHclusters().entrySet()){
                    if(entry.getValue() < 0.15){
                        continue;
                    }
                    if(!fct.containsKey(entry.getKey())){
                        fct.put(entry.getKey(), 0);
                    }
                    fct.put(entry.getKey(), fct.get(entry.getKey()) + 1);
                }
                if (newsDocumentMap.containsKey(documentInfo.getDocid())) {
                    NewsDocument nd = newsDocumentMap.get(documentInfo.getDocid());
                    Scope scope = nd.getScope();
                    if (scope != null && (scope.equals(Scope.notrecommend) || scope.equals(Scope.hide) || scope.equals(Scope.notserve) || scope.equals(Scope.removed))) {
                        delDocs.add(Pair.of(documentInfo.getDocid(), "scope: " + scope.name()));
                        continue;
                    }
                    if (nd.isLocalDoc()) {
                        delDocs.add(Pair.of(documentInfo.getDocid(), "is local doc"));
                        continue;
                    }
                    if (now.getTime() > nd.getTimeOfDeath()) {
                        delDocs.add(Pair.of(documentInfo.getDocid(), "time of death now: " + now.getTime() + " death: " + nd.getTimeOfDeath()));
                        continue;
                    }
                    long delt = now.getTime() - nd.getPublishDate().getTime();
                    long minutes = (int) (delt / (1000 * 60));

                    if (minutes > threeDay && !documentInfo.getDocid().startsWith("K_")) {
                        delDocs.add(Pair.of(documentInfo.getDocid(), "pub time beyond 3 days"));
                        continue;
                    }
                    if (nd.getCType().equals("video")) {
                        delDocs.add(Pair.of(documentInfo.getDocid(), "ctype video"));
                        continue;
                    }
                    Optional<Integer> tierOpt = nd.getSrcAuthority();
                    int tier = tierOpt.or(4); //?
                    if(documentInfo.getDocid().startsWith("O_")){
                        tier = documentInfo.getTier();
                    }
                    if (tier < 3) {
                        delDocs.add(Pair.of(documentInfo.getDocid(), "tier " + tier));
                        continue;
                    }
                    String source = nd.getSource();
                    if (source != null && source.equals("北青网")) {
                        delDocs.add(Pair.of(documentInfo.getDocid(), "source " + source));
                        continue;
                    }
                    if (documentInfo.getDocid().startsWith("K_")) {
                        int imageCount = nd.getImageCount();
                        if (imageCount <= 0) {
                            delDocs.add(Pair.of(documentInfo.getDocid(), "zhihu image count " + imageCount));
                            continue;
                        }
                    }

                    Optional<ClickFeedbacks> cfOpt = nd.getClickFeedbacksOpt();
                    if(cfOpt.isPresent()){
                        double click = cfOpt.get().getClickDoc().or(0.0);
                        double dwell = nd.getDwellClickRate();
                        if(click > 10 && dwell < 40){
                            delDocs.add(Pair.of(documentInfo.getDocid(), "dwell is " + dwell));
                            continue;
                        }
                    }
                } else {
                    log.warn(" get doc failed from NewsDocumentDAO docid: " + documentInfo.getDocid());
                }
                if(documentInfo.getDocid().startsWith("O_")){
                    delDocs.add(Pair.of(documentInfo.getDocid(), "not safe"));
                    continue;
                }
                if(documentInfo.getDocid().startsWith("K_")){
                    delDocs.add(Pair.of(documentInfo.getDocid(), "filter zhihu"));
                    continue;
                }
                long delt = now.getTime() - documentInfo.getDate().getTime();
                long minutes = (int) (delt / (1000 * 60));
                int lifeTime = threeDay;
                if (minutes > lifeTime) {
                    delDocs.add(Pair.of(documentInfo.getDocid(), "insert time beyond lifeTime: " + lifeTime));
                }
            }

            for (Pair<String, String> pair : delDocs) {
                DocumentInfo delDoc = documentInfoMap.get(pair.getLeft());
                if (delDoc == null) {
                    log.info("doc " + pair.getLeft() + " removed by other thread");
                    continue;
                }
                log.info(" removed doc " + delDoc.toString() + " reason: " + pair.getRight());
                ret.add(delDoc);
                documentInfoMap.remove(pair.getLeft());
            }
            fidCnt = fct;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            readWriteLock.writeLock().unlock();
        }
        return ret;
    }

    public List<DocumentInfo> fetch(String fid) {
        try {
            return fetchCache.get(fid);
        } catch (ExecutionException e) {
            e.printStackTrace();
            log.error("get explore result failed fid: " + fid);
        }
        return Lists.newArrayList();
    }

    public List<DocumentInfo> processFetch(String fid){
        Date now = new Date();
        List<DocumentInfo> ret = Lists.newArrayList();
        List<String> docids = Lists.newArrayList();
        for (DocumentInfo doc : documentInfoMap.values()) {
            long delt = now.getTime() - doc.getDate().getTime();
            long minutes = (int) (delt / (1000 * 60));
            if(minutes < 2){
                continue;
            }
            docids.add(doc.getDocid());
        }
        Map<String, NewsDocument> newsDocumentMap = NewsDocumentCache.defaultInstance().getAll(docids);
        for (DocumentInfo doc : documentInfoMap.values()) {
            NewsDocument nd = newsDocumentMap.get(doc.getDocid());
            if(nd == null){
                continue;
            }
            double rankScore = -1;
            if(doc.getHtopics().containsKey(fid)){
                rankScore = doc.getHtopics().get(fid);
            }else if(doc.getHclusters().containsKey(fid)){
                rankScore = doc.getHclusters().get(fid);
            }
            if(rankScore < 0){
                continue;
            }
            DocumentInfo ndoc = (DocumentInfo)doc.clone();
            ndoc.setRankScore(rankScore);
            ret.add(ndoc);
        }
        ret.sort(Comparator.comparingDouble(d -> -d.getRankScore()));
        return ret;
    }


    public boolean contain(String docid) {
        return documentInfoMap.containsKey(docid);
    }

    public Map<String, Integer> getFidCnt(){
        return fidCnt;
    }

    public boolean invalidateAll(){
        fetchCache.invalidateAll();
        return true;
    }


    public boolean deserialize(JsonNode root) {
        if (root.has("documentInfoMap")) {
            JsonNode documentInfoMapNode = root.get("documentInfoMap");
            Iterator<String> fieldNames = documentInfoMapNode.getFieldNames();
            while (fieldNames.hasNext()) {
                String docid = fieldNames.next();
                JsonNode documentInfoNode = documentInfoMapNode.get(docid);
                DocumentInfo documentInfo = new DocumentInfo();
                documentInfo.deserialize(documentInfoNode);
                documentInfoMap.put(docid, documentInfo);
            }
        }
        return true;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> ret = Maps.newLinkedHashMap();
        Map<String, Object> documentInfoMapInfo = Maps.newLinkedHashMap();
        for (Map.Entry<String, DocumentInfo> entry : documentInfoMap.entrySet()) {
            documentInfoMapInfo.put(entry.getKey(), entry.getValue().serialize());
        }
        ret.put("documentInfoMap", documentInfoMapInfo);
        return ret;
    }

    public Map<String, Object> getDebugInfo(String fid) {
        List<DocumentInfo> fetchResult = fetch(fid);
        Map<String, Object> ret = Maps.newLinkedHashMap();
        ret.put("fid", fid);
        ret.put("cacheStats", cahcheStats());
        ret.put("docSize", documentInfoMap.size());
        List<Map<String, Object>> lst = Lists.newArrayList();
        for (DocumentInfo documentInfo : fetchResult) {
            if (documentInfo == null) {
                continue;
            }
            Map<String, Object> debugInfo = documentInfo.getDebugInfo();
            lst.add(debugInfo);
        }
        ret.put("docs", lst);
        return ret;
    }

    protected Map<String, Object> cahcheStats() {
        CacheStats stats = fetchCache.stats();
        Map<String, Object> result = Maps.newHashMap();
        result.put("stat", stats.toString());
        result.put("avgLoadPenalty", stats.averageLoadPenalty());
        result.put("hitRate", stats.hitRate());
        result.put("missRate", stats.missRate());
        result.put("size", fetchCache.size());
        return result;
    }
}
//1/(1+exp( (x - 50) * 0.1)) + 0.73