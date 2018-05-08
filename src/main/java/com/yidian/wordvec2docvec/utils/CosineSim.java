package com.yidian.wordvec2docvec.utils;

/**
 * Created by admin on 2018/5/5.
 */
public class CosineSim {

    public float dot(float[] a, float[] b) {
        float sum = 0.0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public float norm(float[] a) {
        float sum = 0.0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * a[i];
        }
        return (float) Math.sqrt(sum);
    }

    public float cossim(float[] a, float[] b) {
        return dot(a, b) / (norm(a) * norm(b));
    }
}
