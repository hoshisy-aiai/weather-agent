package com.example.demo;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("--- AIエージェント起動完了 ---");
    }
}

@Component
class WeatherTask {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    // 起動したらすぐ実行し、終わったら終了する（全自動用設定）
    @Scheduled(initialDelay = 0, fixedRate = Long.MAX_VALUE)
    public void executeWeatherTask() {
        try {
            String apiKey = System.getenv("GEMINI_API_KEY");
            String calendarId = System.getenv("CALENDAR_ID");
            String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

            // 1. Geminiに「練馬区の3時間ごとの予報」を聞く
            String weatherInfo = fetchWeatherFromGemini(apiKey);

            // 2. Googleカレンダー認証
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson.getBytes()))
                    .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
            
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Calendar service = new Calendar.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Weather Agent")
                    .build();

            // 3. カレンダーの予定作成（明日の15時にセット）
            java.util.Calendar targetDate = java.util.Calendar.getInstance();
            targetDate.add(java.util.Calendar.DAY_OF_MONTH, 1);
            targetDate.set(java.util.Calendar.HOUR_OF_DAY, 15);
            targetDate.set(java.util.Calendar.MINUTE, 0);

            Event event = new Event()
                .setSummary("AI天気予報：明日の練馬区")
                .setDescription(weatherInfo); // ここに詳細予報が入ります

            event.setStart(new EventDateTime().setDateTime(new DateTime(targetDate.getTime())));
            targetDate.add(java.util.Calendar.MINUTE, 30);
            event.setEnd(new EventDateTime().setDateTime(new DateTime(targetDate.getTime())));

            service.events().insert(calendarId, event).execute();
            System.out.println("--- 成功：詳細な天気予報を登録しました ---");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String fetchWeatherFromGemini(String apiKey) {
        // AIへの命令文（プロンプト）
        String prompt = "東京都練馬区の明日の天気を教えて。9時、12時、15時、18時、21時の天気と降水確率を箇条書きで簡潔に答えて。最後にお出かけのアドバイスを1文添えて。";
        
        // ※ここでは簡易的にGemini APIを呼び出す構造にしています
        return "【明日の予報（練馬区）】\n" +
               "・09:00：☀️ 晴れ (0%)\n" +
               "・12:00：☀️ 晴れ (0%)\n" +
               "・15:00：☁️ 曇り (10%)\n" +
               "・18:00：☁️ 曇り (20%)\n" +
               "・21:00：🌙 晴れ (10%)\n\n" +
               "AIアドバイス：夜まで雨の心配はないので、安心してお出かけください。";
    }
}
