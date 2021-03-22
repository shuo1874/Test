package com.shuo.server.controller;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Controller
public class TestController {
    @PostMapping("/request")
    @ResponseBody
    public String request(String sequence, String requestTimeStamp) throws InterruptedException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS");
        String date = df.format(Long.valueOf(requestTimeStamp));
        System.out.println(requestTimeStamp +" " + date + ":" + sequence);

        //返回json数据
        Map<String,Object> map = new HashMap<>();
        map.put("sequence",sequence);
        map.put("requestTimeStamp",requestTimeStamp);

//        Thread.sleep(800);        //调整sleep时间测试响应超时情况
        //返回数据转化为json字符串
        return getJSONString(200,"响应请求"+sequence,map);
    }

    public String getJSONString(int code, String msg, Map<String, Object> map) {
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", msg);
        if (map != null) {
            for (String key : map.keySet()) {
                json.put(key, map.get(key));
            }
        }
        return json.toJSONString();
    }
}
