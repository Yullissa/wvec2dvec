package com.yidian.wordvec2docvec.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hipu.util.HttpUtil;
import com.yidian.wordvec2docvec.core.WordVec2DocVec;
import org.apache.commons.lang3.tuple.Pair;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by admin on 2018/5/5.
 */
public class Word2DocVecServlet extends HttpServlet {
    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private int docNum;
    private float avgle;

    public Word2DocVecServlet(int docNum,float avgle){
        this.docNum = docNum;
        this.avgle = avgle;
    }
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        log.info("ACCESS:" + request.getQueryString());
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        request.setCharacterEncoding("utf-8");
        String docid = request.getParameter("docid");

        Map<String, Object> retMap = Maps.newHashMap();
        retMap.put("status", "success");
        retMap.put("code", 0);
        retMap.put("result", WordVec2DocVec.getInstance().recommend(docid,docNum,avgle));
        HttpUtil.setResponse(response, gson.toJson(retMap));
    }

    public static void main(String[] args) {
        List<Pair<String, Double>> cidSore = Lists.newArrayList();
    }
}


