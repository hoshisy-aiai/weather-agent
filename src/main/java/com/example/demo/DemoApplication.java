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

import java.io.ByteArrayInputStream;
import java.util.Collections;

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

    // 日本時間の毎日15時に実行するように設定
    @Scheduled(cron = "0 0 15 * * *", zone = "Asia/Tokyo")
    public void executeWeatherTask() {
        try {
            String calendarId = System.getenv("CALENDAR_ID");
            String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

            if (calendarId == null || credentialsJson == null) {
                System.err.println("エラー: 環境変数が不足しています。");
                return;
            }

            // Googleカレンダー認証（外部クラスを使わず、このメソッド内で完結）
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson.getBytes()))
                    .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
            
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Calendar service = new Calendar.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Weather Agent")
                    .build();

            // 明日の世界時間06:00（日本時間15:00）をセット
            java.util.Calendar targetDate = java.util.Calendar.getInstance();
            targetDate.add(java.util.Calendar.DAY_OF_MONTH, 1);
            targetDate.set(java.util.Calendar.HOUR_OF_DAY, 6);
            targetDate.set(java.util.Calendar.MINUTE, 0);
            targetDate.set(java.util.Calendar.SECOND, 0);

            java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
            endDate.add(java.util.Calendar.MINUTE, 30);

            Event event = new Event()
                .setSummary("AIエージェント：明日の天気確認")
                .setDescription("15時（世界時間6時）に自動登録されました。");

            event.setStart(new EventDateTime().setDateTime(new DateTime(targetDate.getTime())));
            event.setEnd(new EventDateTime().setDateTime(new DateTime(endDate.getTime())));

            service.events().insert(calendarId, event).execute();
            System.out.println("--- 成功：カレンダーの15時に予定を入れました ---");

        } catch (Exception e) {
            System.err.println("エラー発生: " + e.getMessage());
        }
    }
}
