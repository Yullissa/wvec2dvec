package com.yidian.wordvec2docvec.data;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hipu.news.dynamic.NewsDocument;
import com.hipu.news.dynamic.Scope;
import com.hipu.news.dynamic.dao.NewsDocumentDAO;
import lombok.Data;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Data
public class DocumentInfo implements Cloneable{
    private double rankScore = 0;
    private String docid = "null";
    private String sig = "null";
    private String titleWords = "null";
    private String refer = "pipline";
    private int tier = 0;
    private Map<String, Double> htopics = ImmutableMap.of();
    private Map<String, Double> hclusters = ImmutableMap.of();
    private Date date = new Date();
    private static ObjectMapper mapper = new ObjectMapper();

    public DocumentInfo(){

    }

    public DocumentInfo(String docid, String titleWords, String sig, int tier, Date date, String refer){
        this.docid = docid;
        this.titleWords = titleWords;
        this.sig = sig;
        this.tier = tier;
        this.date = date;
        this.refer = refer;
    }



    public boolean deserialize(JsonNode root) {
        try{
            if(root.has("docid")){
                docid = root.get("docid").asText();
            }
            if(root.has("sig")){
                sig = root.get("sig").asText();
            }
            if(root.has("titleWords")){
                titleWords = root.get("titleWords").asText();
            }
            if(root.has("refer")){
                refer = root.get("refer").asText();
            }
            if(root.has("tier")){
                tier = root.get("tier").asInt(0);
            }
            if(root.has("date")){
                String dateString = root.get("date").asText();
                SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                date = new Date();
                try {
                    date = simpleFormat.parse(dateString);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            if(root.has("htopics")){
                htopics = mapper.readValue(root.get("htopics"), new TypeReference<Map<String, Double>>(){});
            }
            if(root.has("hclusters")){
                hclusters = mapper.readValue(root.get("hclusters"), new TypeReference<Map<String, Double>>(){});
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> ret = Maps.newLinkedHashMap();
        ret.put("docid", docid);
        ret.put("sig", sig);
        ret.put("titleWords", titleWords);
        ret.put("refer", refer);
        ret.put("tier", tier);
        SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ret.put("date", simpleFormat.format(date));
        ret.put("htopics", htopics);
        ret.put("hclusters", hclusters);
        return ret;
    }
    @Override
    public Object clone(){
        DocumentInfo documentInfo = null;
        try {
            documentInfo = (DocumentInfo)super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return documentInfo;
    }

    public Map<String, Object> getDebugInfo(){
        Map<String, Object> ret = Maps.newLinkedHashMap();
        SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Optional<NewsDocument> ndOpt = NewsDocumentDAO.defaultNewsDocumentDAO().getDocumentOpt(docid);
        ret.put("docid", docid);
        if(ndOpt.isPresent()){
            NewsDocument nd = ndOpt.get();
            ret.put("title", nd.getTitle());
            ret.put("source", nd.getSource());
            Scope scope = nd.getScope();
            if(scope != null) {
                ret.put("scope", scope.name());
            }
            double dwell = nd.getDwellClickRate();
            double dw_punish = 1 / (1 + Math.exp((dwell - 50) * 0.1)) + 0.73;
            ret.put("dwell", dwell);
            ret.put("punish", 1.0 / dw_punish);
            ret.put("rankScore", rankScore);
        }
        ret.put("tier", tier);
        ret.put("sig", sig);
        String ts = "null";
        if(date != null){
            ts = simpleFormat.format(date);
        }
        ret.put("date", ts);
        ret.put("refer", refer);
        ret.put("htopics", htopics);
        ret.put("hclusters", hclusters);
        return ret;
    }
}

