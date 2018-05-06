package com.yidian.wordvec2docvec.filter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hipu.news.dynamic.NewsDocument;
import com.hipu.news.dynamic.Scope;
import com.hipu.news.dynamic.dao.NewsDocumentDAO;
import com.yidian.wordvec2docvec.data.DocumentFeature;
import com.yidian.wordvec2docvec.utils.HttpUtils;
import com.yidian.serving.metrics.MetricsFactoryUtil;
import lombok.extern.log4j.Log4j;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Log4j
public class FastTextFilter implements DocumentFilter {
    private static String dedupServiceAddr = "http://10.126.40.12:9080/simdoc?minScore=0.4&minOutScore=2.0&fmt=json&newsid=";
    private static Gson gson = new Gson();
    private static HashSet<Integer> tpcBlackList = new HashSet<>();
    private static HashSet<String> srcBlackList = new HashSet<>();
    private static String metricPrefix = "usercluster-ee.docCollect";
    private static ObjectMapper mapper = new ObjectMapper();
    private static volatile FastTextFilter instance = null;
    private static String stopWords = "/ 。 ~ | ） （ 。。。 ... 「 」 + - — #. ： : 丨 【 】 、 \" 的 了 是 着 在 ， , ! ！ “ ” 《 》 ( ) … ? ？ # . ？· ' ?· 。。。　．．．　「　」　＋　－　—　＃　．　：　：　丨　【　】　、　的　了　是　着　在　，　，　！　！　“　”　《　》　（　）　…　？　？·　＂　＇";
    private static Set<String> stopWordsSet = new HashSet<>();

    static {
        tpcBlackList.add(5625);
        tpcBlackList.add(5);
        tpcBlackList.add(522);
        tpcBlackList.add(5219);
        tpcBlackList.add(6205);
        tpcBlackList.add(3435);
        tpcBlackList.add(1120);
        tpcBlackList.add(7902);
        tpcBlackList.add(8903);
        srcBlackList.add("北青网");
        for (String str : stopWords.split(" ")) {
            if (str.length() > 0) {
                stopWordsSet.add(str);
            }
        }
    }

    public static FastTextFilter getInstance() {
        if (instance == null) {
            synchronized (FastTextFilter.class) {
                if (instance == null) {
                    instance = new FastTextFilter();
                }
            }
        }
        return instance;
    }

    private FastTextFilter() {

    }

    private static boolean needTpcFilter(JsonNode jsonNode) {
        if (jsonNode == null) {
            return false;
        }
        try {
            JsonNode ltpcNode = jsonNode.get("ltpc");
            for (int i = 0; i < ltpcNode.size(); ++i) {
                int ltpc = ltpcNode.get(i).asInt();
                if (tpcBlackList.contains(ltpc)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error(e);
            e.printStackTrace();
        }
        return false;
    }

    private static boolean isValidDocument(NewsDocument nd){
        Scope scope = nd.getScope();
        com.google.common.base.Optional<Integer> srcAuthority = nd.getSrcAuthority();
        if(scope == null || !srcAuthority.isPresent()){
            return false;
        }
        if(scope != null && (scope.equals(Scope.notrecommend) || scope.equals(Scope.hide) || scope.equals(Scope.notserve) || scope.equals(Scope.removed))) {
            return false;
        }
        if(srcAuthority.isPresent() && srcAuthority.get() <= 2){
            return false;
        }
        return true;
    }

    private static boolean needDedup(String docid, Date pubTime) {
        if (docid == null) {
            return false;
        }
        try {
            String url  = dedupServiceAddr + docid;
            Optional<String> retOpt = HttpUtils.get(url, 500, 3, 5);
            if(!retOpt.isPresent()){
                return false;
            }
            Map<String, Object> retMap = gson.fromJson(retOpt.get(), new TypeToken<Map<String, Object>>() {
            }.getType());

            if (retMap.containsKey("simnews") && retMap.get("simnews") != null) {
                Map<String, Object> simNewsMap = (Map<String, Object>) retMap.get("simnews");
                if (simNewsMap.size() > 0) {
                    Map<String, NewsDocument> NewsDocumentMap = NewsDocumentDAO.defaultNewsDocumentDAO().getNewsDocumentMap(simNewsMap.keySet());
                    for(NewsDocument nd: NewsDocumentMap.values()){
                        long delt = pubTime.getTime() - nd.getPublishDate().getTime();
                        int minutes = (int) (delt / (1000 * 60));
                        if(minutes > 4320){
                            continue;
                        }
                        if(isValidDocument(nd)){
                            log.info("dedup docid " + docid + " with docid: " + nd.getDocid());
                            return true;
                        }
                    }
                }

            }
        } catch (Exception e) {
            log.error(e);
            e.printStackTrace();
        }
        return false;
    }


    private static String getOrDefault(JsonNode root, String field, String def) {
        if (root.has(field)) {
            return root.get(field).asText();
        }
        return def;
    }

    @Override
    public Optional<DocumentFeature> filter(String data) {
        SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            JsonNode root = mapper.readTree(data);
            String docid = getOrDefault(root, "_id", "null");
            String segTitle = getOrDefault(root, "seg_title", "null");
            String source = getOrDefault(root, "source", "null");
            String date = getOrDefault(root, "date", "null");
            String insert_time = getOrDefault(root, "insert_time", "null");
            String sig = getOrDefault(root, "signature", "null");
            String ctype = getOrDefault(root, "ctype", "news");

            if (!ctype.equals("news") && !ctype.equals("wenda") && !ctype.equals("opp_article")) {
                return Optional.empty();
            }

            int tier = -1;
            if (root.has("source_tier")) {
                tier = root.get("source_tier").asInt(-1);
            }

            int image_count = 8;
            if (root.has("image_count")) {
                image_count = root.get("image_count").asInt(8);
            }

            String docDetail = " doc docid: " + docid + " title: " + segTitle + " source: " + source + " date: " + date;

            if(docid.startsWith("O_")){
                log.info("safefilter" + docDetail);
                return Optional.empty();
            }

            if (ctype.equals("wenda")) {
                if (!docid.startsWith("K_")) {
                    return Optional.empty();
                }
                if (segTitle.replaceAll("\\s", "").length() > 20) {
                    log.info("titleLengthfilter" + docDetail);
                    return Optional.empty();
                }
                if (root.has("score_up")) {
                    tier = root.get("score_up").asInt(0);
                }
                return Optional.empty();//filter zhihu
            }
            Date now = new Date();
            Date pubTime = now;
            Date insertTime = now;
            try {
                insertTime = simpleFormat.parse(insert_time);
            } catch (ParseException e) {
                log.error(e);
                e.printStackTrace();
            }

            long deltCPP = now.getTime() - insertTime.getTime();
            MetricsFactoryUtil.getRegisteredFactory().getHistogram(metricPrefix + ".time.delta").update(deltCPP / 1000);

            if (srcBlackList.contains(source)) {
                log.info("srcBlacklistfilter" + docDetail);
                return Optional.empty();
            }
            try {
                pubTime = simpleFormat.parse(date);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            long delt = now.getTime() - pubTime.getTime();
            int minutes = (int) (delt / (1000 * 60));
            if (minutes > 4320 && !docid.startsWith("K_")) {
                log.info("longtimefilter" + docDetail);
                return Optional.empty();
            }
            if (tier < 3) {
                log.info("tierfilter" + docDetail);
                return Optional.empty();
            }
            int bait = 0;
            if (root.has("bait")) {
                bait = root.get("bait").asInt(0);
            }
            if (bait > 1) {
                log.info("baitfilter" + docDetail);
                return Optional.empty();
            }
            double sc_local = 0;
            if (root.has("sc_local")) {
                sc_local = root.get("sc_local").asDouble(0);
            }
            if (sc_local > 0.5) {
                log.info("localfilter" + docDetail);
                return Optional.empty();
            }
            if (root.has("cat_class")) {
                JsonNode cat_class = root.get("cat_class");
                boolean flag = false;
                for (JsonNode node : cat_class) {
                    if (node.asText().equals("搞笑")) {
                        flag = true;
                        break;
                    }
                }
                if (flag) {
                    log.info("catclassfilter" + docDetail);
                    return Optional.empty();
                }
            }
            if (docid.equals("null") || data.equals("null")) {
                log.info(data + " missing key field!");
                log.info("keyfieldfilter" + docDetail);
                return Optional.empty();
            }
            if (needTpcFilter(root)) {
                log.info("tpcfilter" + docDetail);
                return Optional.empty();
            }
            if (tier <= 4 && needDedup(docid, pubTime)) {
                log.info("dedup" + docDetail);
                return Optional.empty();
            }
            double sc_indepth = 1;
            if (root.has("sc_indepth")) {
                sc_indepth = root.get("sc_indepth").asDouble(1.0);
            }
            if(docid.startsWith("K_")){
                sc_indepth = 1.0;
            }
            String seg_content = "";
            if (root.has("seg_content")) {
                seg_content = root.get("seg_content").asText();
            }
            if (sc_indepth < 0.5 && image_count < 8 && !source.equals("知乎专栏") ) {
                log.info("fastTextFilter" + docDetail);
                return Optional.empty();
            }

            ImmutableMap<String, Double> htopics = ImmutableMap.of();
            ImmutableMap<String, Double> hclusters = ImmutableMap.of();
            if (root.has("htck") && root.has("htcv")) {
                List<String> htck = mapper.readValue(root.get("htck"), new TypeReference<List<String>>(){});
                List<String> htcv = mapper.readValue(root.get("htcv"), new TypeReference<List<String>>(){});
                Map<String, Double> topicTemp = Maps.newHashMap();
                Map<String, Double> clusterTemp = Maps.newHashMap();
                if(htck != null && htcv != null && htck.size() == htcv.size() && htck.size() >= 2){
                    String topic_keys = htck.get(0);
                    String topic_vals = htcv.get(0);
                    if(topic_keys != null && topic_vals != null && topic_keys.length() != 0){
                        String[] key_parts = topic_keys.split(",");
                        String[] val_parts = topic_vals.split(",");
                        if(key_parts.length == val_parts.length){
                            for(int i = 0; i < key_parts.length; i++){
                                try{
                                    topicTemp.put(key_parts[i], Double.parseDouble(val_parts[i]));
                                }catch (NumberFormatException e){
                                    e.printStackTrace();
                                    log.error("docid: " + docid +  " htc topic score format error");
                                }
                            }
                        }
                    }

                    String cluster_keys = htck.get(1);
                    String cluster_vals = htcv.get(1);
                    if(cluster_keys != null && cluster_vals != null && cluster_keys.length() != 0){
                        String[] key_parts = cluster_keys.split(",");
                        String[] val_parts = cluster_vals.split(",");
                        if(key_parts.length == val_parts.length){
                            for(int i = 0; i < key_parts.length; i++){
                                try{
                                    clusterTemp.put(key_parts[i], Double.parseDouble(val_parts[i]));
                                }catch (NumberFormatException e){
                                    e.printStackTrace();
                                    log.error("docid: " + docid +  " htc cluster score format error");
                                }
                            }
                        }
                    }
                    htopics = ImmutableMap.copyOf(topicTemp);
                    hclusters = ImmutableMap.copyOf(clusterTemp);
                }
            }
            DocumentFeature documentFeature = new DocumentFeature(docid, segTitle, source, insert_time, "pipline");
            documentFeature.setSig(sig);
            documentFeature.setTier(tier);
            documentFeature.setHclusters(hclusters);
            documentFeature.setHtopics(htopics);
            return Optional.of(documentFeature);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("explore doc collect log parse error " + data);
        }
        return Optional.empty();
    }

    public static void main(String[] args) {

    }
}
