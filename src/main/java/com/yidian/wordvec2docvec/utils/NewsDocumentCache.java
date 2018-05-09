package com.yidian.wordvec2docvec.utils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.hipu.news.dynamic.NewsDocument;
import com.hipu.news.dynamic.dao.NewsDocumentDAO;
import lombok.extern.log4j.Log4j;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Log4j
public class NewsDocumentCache {
    private static volatile NewsDocumentCache instance = null;
    private volatile LoadingCache<String, Optional<NewsDocument>> cache = CacheBuilder.newBuilder()
            .maximumSize(70000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build(new CacheLoader<String, Optional<NewsDocument>>() {
                @Override
                public Optional<NewsDocument> load(String key) throws Exception {
                    try{
                        com.google.common.base.Optional<NewsDocument> ndOpt = NewsDocumentDAO.defaultNewsDocumentDAO().getDocumentOpt(key);
                        if(ndOpt.isPresent()){
                            return Optional.of(ndOpt.get());
                        }
                    }catch (NumberFormatException e){
                        e.printStackTrace();
                        log.error(e);
                    }
                    return Optional.empty();
                }
            });

    public static NewsDocumentCache defaultInstance() {
        if (instance == null) {
            synchronized (NewsDocumentCache.class) {
                if (instance == null) {
                    instance = new NewsDocumentCache();
                }
            }
        }
        return instance;
    }

    private NewsDocumentCache(){
    }

    public Optional<NewsDocument> get(String docid){
        try {
            return cache.get(docid);
        } catch (ExecutionException e) {
            e.printStackTrace();
            log.error("get failed docid: " + docid);
        }
        return Optional.empty();
    }

    public Map<String, NewsDocument> getAll(Collection<String> docids){
        Map<String, NewsDocument> ret = Maps.newHashMap();
        try {
            for(Map.Entry<String, Optional<NewsDocument>> it: cache.getAll(docids).entrySet()){
                if(it.getValue().isPresent()){
                    ret.put(it.getKey(), it.getValue().get());
                }
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public Map<String, Object> getCahcheStats() {
        CacheStats stats = cache.stats();
        Map<String, Object> result = Maps.newHashMap();
        result.put("stat", stats.toString());
        result.put("avgLoadPenalty", stats.averageLoadPenalty());
        result.put("hitRate", stats.hitRate());
        result.put("missRate", stats.missRate());
        result.put("size", cache.size());
        return result;
    }
}
