package com.shuo;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    //收到的相应数，收到response后增加
    private static volatile int responseNum = 0;
    //发出的请求数，手动输入
    private static volatile int requestNum = 0;
    //第一个请求发出时间戳
    private static volatile long startTimeStamp;
    //第一个请求发出时间(年:月:日 时:分:秒 毫秒)
    private static volatile String startTime;

    //日志记录
    private static Logger log = Logger.getLogger(Client.class.getClass());

    //传入请求序号，封装request
    public static HttpPost getRequest(String sequence) {
        //请求地址
        String url = "http://localhost:8000/server/request";
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
        List<NameValuePair> list = new LinkedList<>();
        //sequence为第几个请求，requestTimeStamp为请求发送时间戳
        list.add(new BasicNameValuePair("sequence", sequence));
        list.add(new BasicNameValuePair("requestTimeStamp", String.valueOf(System.currentTimeMillis())));
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(list));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return httpPost;
    }

    //判断第n个请求是否在第n秒内返回
    private static boolean outOfTime(int sequence, long startTime, long endTime) {
        if ((endTime - startTime) <= (sequence * 1000)) {
            return false;
        } else {
            return true;
        }
    }


    public static void main(String[] args) throws InterruptedException {
        //o用作于锁定对象
        Object o = new Object();
        //输入获取要发送的请求数
        int sendRequestNum = -1;
        //开启latch个线程，当所有线程执行完后停止主线程
        final CountDownLatch latch;

        CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
        httpclient.start();

        Scanner scan = new Scanner(System.in);
        //时间戳转化为日期时间
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS");


        System.out.print("请输入需要发送的请求数:");
        sendRequestNum = scan.nextInt();

        if (sendRequestNum > 0) {

            latch = new CountDownLatch(sendRequestNum);

            //每个请求开启一个线程
            for (int i = 1; i <= sendRequestNum; i++) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //加锁以确保请求发送的顺序性
                        synchronized (o) {
                            requestNum++;
                            HttpPost request = Client.getRequest(requestNum + "");
                            httpclient.execute(request, new FutureCallback<HttpResponse>() {
                                @Override
                                public void completed(HttpResponse httpResponse) {
                                    HttpEntity entity = httpResponse.getEntity();
                                    long responseTimeStamp = System.currentTimeMillis();
                                    String endTime = df.format(Long.valueOf(responseTimeStamp));
                                    try {
                                        JSONObject message = JSONObject.parseObject(EntityUtils.toString(entity, "UTF-8"));
                                        int sequence = Integer.valueOf((String) message.get("sequence"));
                                        long requestTimeStamp = Long.valueOf((String) message.get("requestTimeStamp"));
                                        if (sequence == 1) {
                                            startTimeStamp = requestTimeStamp;
                                            startTime = df.format(Long.valueOf(startTimeStamp));
                                        }
                                        //若还未获取到第一个请求的发出时间，则阻塞等待
                                        while(startTime == null){}
                                        long timeSpending = responseTimeStamp - startTimeStamp;
                                        //检验当前请求收到时间是否超过期望时间
                                        boolean flag = outOfTime(sequence, startTimeStamp, responseTimeStamp);
                                        if(flag == true){
                                            log.error("响应失败，请求" + message.get("sequence") + " 在第一个请求发出后" + timeSpending + "ms后返回；超过期望时间；  请求发送时间：" + startTime + "；请求返回时间：" + endTime);
                                        }
//                                        System.out.println("请求 " + message.get("sequence") + " 在第一个请求发出后" + timeSpending + "ms后返回；请求发送时间：" + startTime + "；请求返回时间：" + endTime);
                                        log.info("请求 " + message.get("sequence") + " 在第一个请求发出后" + timeSpending + "ms后返回；请求发送时间：" + startTime + "；请求返回时间：" + endTime);
                                        latch.countDown();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void failed(Exception e) {
                                    log.error("请求响应回调failed");
                                }

                                @Override
                                public void cancelled() {
                                    log.error("请求响应回调cancelled");
                                }
                            });
                        }
                    }
                }).start();
            }
            //开启定时器，如果n秒后主线程未结束，则触发定时器，表面返回失败,并记录日志
            new Timer("timer").schedule(new TimerTask() {
                @Override
                public void run() {
                   if(responseNum != requestNum){
                        log.error("发送请求数：" + requestNum + "；返回请求数：" + responseNum + "；未在" + requestNum + "秒内收到所有相应，返回失败");
                    }
                }
            }, sendRequestNum * 1000);


        }else{
            latch = new CountDownLatch(0);
            System.out.println("输入错误！");
        }

        latch.await();
        System.exit(0);
    }

}
