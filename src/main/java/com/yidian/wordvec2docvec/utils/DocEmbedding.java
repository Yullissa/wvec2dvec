package com.yidian.wordvec2docvec.utils;

import com.google.common.collect.Sets;
import lombok.extern.log4j.Log4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by wangyuqin on 2018/5/3.
 */
@Log4j
public class DocEmbedding {
    private static String word_blacklist = "爆笑 搞笑 趣图 gif 笑趣图 内涵图 囧 笑话 笑抽 内涵趣 动态图 动图 笑死 内涵图 内涵集锦 无节操 段子 糗事 恶搞 恶作剧 坑爹";
    private static Map<String, Integer> word_blacklist_map = new HashMap<>();

    private static String stopwords = "。。。 ... 「 」 + - — # . ： : 丨 【 】 、 的 了 是 着 在 ， , ! ！ “ ” 《 》 ( ) … ? ？· \" ' ?· 。。。　．．．　「　」　＋　－　—　＃　．　：　：　丨　【　】　、　的　了　是　着　在　，　，　！　！　“　”　《　》　（　）　…　？　？·　＂　＇";
    private static Map<String, Integer> stopwords_map = new HashMap<>();

    private static VecInfo target_vec_info = new VecInfo();
    private static VecInfo context_vec_info = new VecInfo();

    private static volatile DocEmbedding instance;
    public static DocEmbedding defaultInstance() {
        if (instance == null) {
            synchronized (DocEmbedding.class) {
                if (instance == null) {
                    instance = new DocEmbedding();
                }
            }
        }
        return instance;
    }
    static class VecInfo {
        int num;
        int size;
        HashMap<String, float[]> vec;
    }

    static {
        for (String str : word_blacklist.split(" ")) {
            if (str.length() > 0) {
                word_blacklist_map.put(str, 1);
            }
        }

        for (String str : stopwords.split(" ")) {
            if (str.length() > 0) {
                stopwords_map.put(str, 1);
            }
        }
    }

    public DocEmbedding(){
//        init("../data/target.vec", target_vec_info);
//        init("../data/context.vec", context_vec_info);
        init("../data/word.vec", context_vec_info);
    }

    private void init(String word_vec_file, VecInfo word_vec_info) {
        if (word_vec_file == null) {
            System.exit(-1);
        }
        try {
            log.info("load word vec:" + word_vec_file);
            load_vec(word_vec_file, word_vec_info);
            log.info("word vec loaded.");
        } catch (Exception e) {
            log.error(e);
            System.exit(-1);
        }

        if (word_vec_info.size == 0) {
            log.error("model formate error.");
            System.exit(-1);
        }
    }

    private static void load_vec(String file_name, VecInfo word_vec_info) throws IOException {
        if (file_name == null || word_vec_info == null) {
            return;
        }

        DataInputStream dis = null;
        BufferedInputStream bis = null;
        float value;

        word_vec_info.num = 0;
        word_vec_info.size = 0;
        try {
            log.info("load vec from " + file_name);
            bis = new BufferedInputStream(new FileInputStream(file_name));
            dis = new DataInputStream(bis);

            word_vec_info.num = Integer.parseInt(readString(dis));
            log.info("num:" + word_vec_info.num);
            word_vec_info.size = Integer.parseInt(readString(dis));
            log.info("size:" + word_vec_info.size);

            word_vec_info.vec = new HashMap<>((int)(word_vec_info.num / 0.75 + 10));

            for (int i = 0; i < word_vec_info.num; ++i) {
                String id = readString(dis);
                float[] vec = new float[word_vec_info.size];
                int j;
                for (j = 0; j < word_vec_info.size; ++j) {
                    value = readFloat(dis);
                    vec[j] = value;
                }
                if (i % 10000 == 0) {
                    log.info("load line " + i + ":" + id);
                }
                word_vec_info.vec.put(id, vec);
                dis.read();
            }
        } catch (Exception e) {
            log.error(e);
        } finally {
            if (bis != null) {
                bis.close();
            }
            if (dis != null) {
                dis.close();
            }
        }
    }

    public  float [] getTargetVec(String key){
        return target_vec_info.vec.getOrDefault(key, null);
    }

    public  float [] getContextVec(String key){
        return context_vec_info.vec.getOrDefault(key, null);
    }

    private static float readFloat(InputStream is) throws IOException {
        byte[] bytes = new byte[4];
        is.read(bytes);
        return getFloat(bytes);
    }

    private static float getFloat(byte[] b) {
        byte accum = 0;
        int accum1 = accum | (b[0] & 255) << 0;
        accum1 |= (b[1] & 255) << 8;
        accum1 |= (b[2] & 255) << 16;
        accum1 |= (b[3] & 255) << 24;
        return Float.intBitsToFloat(accum1);
    }

    private static String readString(DataInputStream dis) throws IOException {
        byte[] bytes = new byte[50];
        byte b = dis.readByte();
        int i = -1;
        StringBuilder sb = new StringBuilder();

        while(b != 32 && b != 10) {
            ++i;
            bytes[i] = b;
            b = dis.readByte();
            if(i == 49) {
                sb.append(new String(bytes));
                i = -1;
                bytes = new byte[50];
            }
        }
        sb.append(new String(bytes, 0, i + 1));//i = -1 is ok?
        return sb.toString();
    }

    public static String getSegTitleWords(String segTitle){
        StringBuilder sb = new StringBuilder();
        for(char ch: segTitle.toCharArray()){
            if(stopwords_map.containsKey(String.valueOf(ch))){
                continue;
            }
            if(ch == ' '){
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    public static Set<String> getJaccardSet(String txt, int window){
        Set<String> ret = Sets.newHashSet();
//        for(int i = window; i <= txt.length(); i++){
//            ret.add(txt.substring(i - window, i));
//        }
        for(char ch: txt.toCharArray()){
            ret.add(String.valueOf(ch));
        }
        return ret;
    }
    public static double JaccardScore(String words1, String words2){
        Set<String> set1 = getJaccardSet(words1, 3);
        Set<String> set2 = getJaccardSet(words2, 3);
        int cross_num = 0;
        for(String str: set1){
            if(set2.contains(str)){
                cross_num += 1;
            }
        }
        set1.addAll(set2);
        int union_num = set1.size();
        return cross_num * 1.0 / union_num;
    }


    public static byte[] float2cbyte(float val){
        int intBits =  Float.floatToIntBits(val);
        byte[] bytes = new byte[4];
        bytes[3] = (byte) (intBits >> 24);
        bytes[2] = (byte) (intBits >> 16);
        bytes[1] = (byte) (intBits >> 8);
        bytes[0] = (byte) (intBits & 0xff);
        return bytes;
    }

    public static byte[] int2cbyte(int val){
        byte[] bytes = new byte[4];
        bytes[3] = (byte) (val >> 24);
        bytes[2] = (byte) (val >> 16);
        bytes[1] = (byte) (val >> 8);
        bytes[0] = (byte) (val & 0xff);
        return bytes;
    }


    public static void main(String [] args){
//        float [] vec = DocEmbedding.defaultInstance().getContextVec("中国");
//        for(int i = 0; i < vec.length; i++){
//            System.out.println(vec[i]);
//        }
        String words1 = "腾讯 隐藏 秘密 : 活跃 用户 与 毛利 增速 放缓 , 社交 发展 空间 有限";
        String words2 = "腾讯 财报 隐藏 秘密 : 用户 与 毛利 增速 放缓 , 社交 发展 空间 有限";
        double score = JaccardScore(getSegTitleWords(words1), getSegTitleWords(words2));
        System.out.println(score);
        byte[] bytes = float2cbyte(-0.52f);
        float val = getFloat(bytes);
        System.out.println(val);
    }
}
