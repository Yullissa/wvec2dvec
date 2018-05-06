package com.yidian.wordvec2docvec.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hipu.util.HttpUtil;
import com.yidian.wordvec2docvec.core.FidSet2news;
import org.apache.commons.lang3.tuple.Pair;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Cluster2NewsServlet extends HttpServlet {
    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        log.info("ACCESS:" + request.getQueryString());
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        request.setCharacterEncoding("utf-8");
        String uid = request.getParameter("uid");

        Map<String, Object> retMap = Maps.newHashMap();
        retMap.put("status", "success");
        retMap.put("code", 0);
        retMap.put("result", FidSet2news.getInstance().recommend(uid));
        HttpUtil.setResponse(response, gson.toJson(retMap));
    }
    public static void main(String [] args){
        List<Pair<String, Double>> cidSore = Lists.newArrayList();
        cidSore.add(Pair.of("c0_47834", 1.0589));
        cidSore.add(Pair.of("c0_2921", 6.79896));
        cidSore.add(Pair.of("c0_15963", 6.46899));
        cidSore.add(Pair.of("c0_34134", 4.8624));
        cidSore.sort((o1, o2) -> o2.getRight().compareTo(o1.getRight()));
        for(Pair<String, Double> it: cidSore){
            System.out.println("cid: " + it.getKey() + " dis: " + it.getRight());
        }
    }
}
