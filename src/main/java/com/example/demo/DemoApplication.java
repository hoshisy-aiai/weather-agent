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
    @Value("${gemini.api.key}")
    private String apiKey;
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
    public void executeWeatherTask() throws Exception {
        String calendarId = "f8dde1c135ea04b76936966936cee136189c1636245c1ba57845f815f7510f1a@group.calendar.google.com"; 

        try {
            System.out.println("Geminiに相談中...");
            String geminiUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=" + apiKey;
            String prompt = "明日の15時に『明日の天気予報を確認する』という予定を入れたいです。短い説明文を1行で考えて。";
            String jsonBody = "{\"contents\": [{\"parts\":[{\"text\": \"" + prompt + "\"}]}]}";

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(geminiUrl, new HttpEntity<>(jsonBody, headers), String.class);
            
            System.out.println("カレンダーに書き込み中...");
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    DemoApplication.class.getResourceAsStream("/credentials.json"))
                    .createScoped(Collections.singleton(CalendarScopes.CALENDAR));

            Calendar service = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Weather Agent")
                    .build();

            Event event = new Event()
                    .setSummary("AIエージェント：明日の天気確認")
                    .setDescription("復旧後に自動登録されました。");

            java.util.Calendar now = java.util.Calendar.getInstance();
            now.add(java.util.Calendar.DATE, 1);
            now.set(java.util.Calendar.HOUR_OF_DAY, 15);
            now.set(java.util.Calendar.MINUTE, 0);

            com.google.api.client.util.DateTime startDateTime = new com.google.api.client.util.DateTime(now.getTime());
            event.setStart(new EventDateTime().setDateTime(startDateTime));
            event.setEnd(new EventDateTime().setDateTime(startDateTime));

            service.events().insert(calendarId, event).execute();
            System.out.println("--- 成功！カレンダーを確認してください ---");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("エラー: " + e.getMessage());
        }
    }
}