package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("--- AIエージェント再起動完了！ ---");
    }

    @Bean
    public CommandLineRunner testOnStart() {
        return args -> {
            System.out.println("【復旧テスト】カレンダー書き込みを開始します...");
            executeWeatherTask();
        };
    }

    @Scheduled(cron = "0 0 15 * * *", zone = "Asia/Tokyo")
    public void executeWeatherTask() {
        // 修正ポイント：直接 GitHub の Secrets から読み込む
            String apiKey = System.getenv("GEMINI_API_KEY"); 
    
    // ログに出して確認（最初の数文字だけ表示して安心する）
    if (apiKey != null && apiKey.length() > 5) {
        System.out.println("APIキーの読み込みに成功しました。");
    } else {
        System.out.println("エラー：APIキーが空っぽです！");
        return; 
    }
    
        String calendarId = "f8dde1c135ea04b76936966936cee136189c1636245c1ba57845f815f7510f1a@group.calendar.google.com"; 

        try {
            // 日本時間を基準にする
            java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Asia/Tokyo");
            java.util.Calendar targetDate = java.util.Calendar.getInstance(tz);
            
            // 1. まず「明日」の日付にする
            targetDate.add(java.util.Calendar.DAY_OF_MONTH, 1);
            
            // 2. 時間を「15時00分」に固定する
            targetDate.set(java.util.Calendar.HOUR_OF_DAY, 15);
            targetDate.set(java.util.Calendar.MINUTE, 0);
            targetDate.set(java.util.Calendar.SECOND, 0);
            targetDate.set(java.util.Calendar.MILLISECOND, 0);
    
            // 3. 終了時刻を「15時30分」にする（PCで見やすくするため）
            java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
            endDate.add(java.util.Calendar.MINUTE, 30);
    
            Event event = new Event()
                .setSummary("★テスト書き込み★ " + new java.util.Date().toString())
                .setDescription("日本時間15:00に固定して書き込むテストです。");
    
            // 開始時間をセット（タイムゾーンも明示）
            EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(targetDate.getTime()))
                .setTimeZone("Asia/Tokyo");
            event.setStart(start);
    
            // 終了時間をセット
            EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(endDate.getTime()))
                .setTimeZone("Asia/Tokyo");
            event.setEnd(end);
    
            service.events().insert(calendarId, event).execute();
            System.out.println("--- 成功！カレンダーの【明日】の15:00を確認してください ---");
    
        } catch (Exception e) {
            e.printStackTrace();
        } 
        // ここにあった 96-99行目の別の catch は消す！
    }