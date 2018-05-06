package com.yidian.wordvec2docvec.utils;

/**
 * Created by admin on 2018/5/6.
 */
public class BM25 {
    private float k = 2.0f;
    private float b = 0.75f;
    /*
 N total number of documents
 nqi  number of documents containing qi
 dqi number of qi in document D
 D length of the document D in words
 avgdl the average document length
 IDF = log (N-nqi+0.5)/(nqi+0.5)
 f(qi,D) dqi/D
 f*(k+1)/(f+k(1-b+b*|D|/avgdl))
  */
    public float getBM25(float N, float nqi, float dqi, float D, float avgdl) {
        float f = dqi / D;
        float IDF = (float) Math.log((N - nqi + 0.5f) / (nqi + 0.5f));
        float tftd = f * (k + 1) / (f + k * (1 - b + b * D / avgdl));
        return IDF * tftd;
    }
}
