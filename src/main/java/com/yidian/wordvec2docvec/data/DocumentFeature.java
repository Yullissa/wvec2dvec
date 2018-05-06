package com.yidian.wordvec2docvec.data;

import com.google.common.collect.ImmutableMap;
import lombok.Data;

@Data
public class DocumentFeature implements Cloneable{
    private String docid;
    private String seg_title;
    private String source;
    private String date;
    private String refer = "pipline";
    private String sig = "null";
    private int tier = 0;
    private float score;
    private ImmutableMap<String, Double> htopics = ImmutableMap.of();
    private ImmutableMap<String, Double> hclusters = ImmutableMap.of();
    public DocumentFeature(String docid, String seg_title, String source, String date, String refer){
        this.docid = docid;
        this.seg_title = seg_title;
        this.source = source;
        this.date = date;
        this.refer = refer;
    }
    @Override
    public Object clone(){
        DocumentFeature documentFeature = null;
        try {
            documentFeature = (DocumentFeature)super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return documentFeature;
    }
    public static void main(String [] args){
        DocumentFeature a = new DocumentFeature("a", "as", "asrc", "12312", "pip");
        a.setScore(0.1f);
        DocumentFeature b = (DocumentFeature) a.clone();
        b.setDocid("b");
        b.setScore(0.2f);
        System.out.println(a.toString());
        System.out.println(b.toString());
    }
}
