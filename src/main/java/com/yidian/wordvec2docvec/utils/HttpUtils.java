package com.yidian.wordvec2docvec.utils;

import com.google.common.collect.Lists;
import lombok.extern.log4j.Log4j;
import org.apache.hadoop.ha.HAServiceProtocol;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONObject;


import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j
public class HttpUtils {
    private static HttpClient client = HttpClients.custom().setMaxConnTotal(100).setMaxConnPerRoute(100).build();
    public static Optional<String> post(String url, Map<String, String> data, int timeout, int retry, int delay){
        HttpPost post = new HttpPost(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(1000).setSocketTimeout(timeout).build();
        post.setConfig(requestConfig);
        List<NameValuePair> parameters = Lists.newArrayList();
        for(Map.Entry<String, String> entry: data.entrySet()){
            parameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        HttpEntity entity = EntityBuilder.create().setParameters(parameters).setContentType(ContentType.APPLICATION_FORM_URLENCODED).build();
        post.setEntity(entity);
        while(retry > 0){
            retry -= 1;
            try {
                HttpResponse response = client.execute(post);
                String ret = EntityUtils.toString(response.getEntity(), "UTF-8");
                return Optional.of(ret);
            } catch (IOException e) {
                log.error(StringTools.LogExceptionStack(e));
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                log.error(StringTools.LogExceptionStack(e));
            }
        }
        return Optional.empty();
    }

    public static Optional<String> get(String url, int timeout, int retry, int delay){
        HttpGet get = new HttpGet(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(1000).setSocketTimeout(timeout).build();
        get.setConfig(requestConfig);
        while(retry > 0){
            retry -= 1;
            try {
                HttpResponse response = client.execute(get);
                String ret = EntityUtils.toString(response.getEntity(), "UTF-8");
                return Optional.of(ret);
            } catch (IOException e) {
                log.error(StringTools.LogExceptionStack(e));
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                log.error(StringTools.LogExceptionStack(e));
            }
        }
        return Optional.empty();
    }

    public static Optional<String> post(String url, String data, int timeout, int retry, int delay){
        HttpPost post = new HttpPost(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(1000).setSocketTimeout(timeout).build();
        post.setConfig(requestConfig);
        HttpEntity entity = EntityBuilder.create().setText(data).setContentType(ContentType.APPLICATION_JSON).build();
        post.setEntity(entity);
        while(retry > 0){
            retry -= 1;
            try {
                HttpResponse response = client.execute(post);
                String ret = EntityUtils.toString(response.getEntity(), "UTF-8");
                return Optional.of(ret);
            } catch (IOException e) {
                log.error(StringTools.LogExceptionStack(e));
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                log.error(StringTools.LogExceptionStack(e));
            }
        }
        return Optional.empty();
    }

    public static void main(String [] args){
        File logConfig = new File("log4j.properties");
        if (logConfig.exists()) {
            System.out.println("User config " + logConfig.toString());
            PropertyConfigurator.configure(logConfig.toString());
        }
        for(int i = 0; i < 1; i++){
//            Optional<String> retOpt = get("http://10.111.0.20:9080/simdoc?minScore=0.4&minOutScore=2.0&fmt=json&newsid=0HpcHV2L", 150, 3, 5);
            String docid = "0IxTGPFK";
            Optional<String> retOpt = get("http://cl-k8s.ha.in.yidian.com/apis/docenter/yidian/ids/"+docid+"/fields/url,pos_title,seg_title,pos_content,source,signature,_id,kws,sc_kws'", 150, 3, 5);
            JSONObject jsonObj = new JSONObject(retOpt.get().toString());
            String result = jsonObj.get("result").toString();
            JSONObject posObj = new JSONObject(result.substring(1,result.length()-1));
//            return posObj.get("pos_content");
            System.out.println(posObj.get("pos_content"));
        }
    }
}
