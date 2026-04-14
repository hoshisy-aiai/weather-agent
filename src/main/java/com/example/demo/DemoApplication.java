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
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.TimeZone;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner runWeatherTask() {
        return args -> {
            System.out.println("--- AIエージェント起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                String weatherInfo = fetchWeatherFromGemini(apiKey);

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
                
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                Calendar service = new Calendar.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                        .setApplicationName("Weather Agent")
                        .build();

                // ★ここを修正しました！
                // TimeZoneを明示的に "Asia/Tokyo" にすることで、GitHub上でも日本時間として扱います
                java.util.Calendar targetDate = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
                targetDate.add(java.util.Calendar.DAY_OF_MONTH, 1); // 明日の日付
                targetDate.set(java.util.Calendar.HOUR_OF_DAY, 15); // 日本時間の「15時」をそのまま指定！
                targetDate.set(java.util.Calendar.MINUTE, 0);
                targetDate.set(java.util.Calendar.SECOND, 0);

                Event event = new Event()
                    .setSummary("AI天気予報：明日の練馬区")
                    .setDescription(weatherInfo);

                // Googleカレンダー側にも「これは日本時間ですよ」と教えてあげます
                EventDateTime start = new EventDateTime()
                    .setDateTime(new DateTime(targetDate.getTime()))
                    .setTimeZone("Asia/Tokyo");
                event.setStart(start);

                targetDate.add(java.util.Calendar.MINUTE, 30);
                EventDateTime end = new EventDateTime()
                    .setDateTime(new DateTime(targetDate.getTime()))
                    .setTimeZone("Asia/Tokyo");
                event.setEnd(end);

                service.events().insert(calendarId, event).execute();
                System.out.println("--- 成功：日本時間15時に登録しました ---");

            } catch (Exception e) {
                e.printStackTrace();
            }

            // 仕事が終わったらプログラムを強制終了（これでGitHubのグルグルも止まります）
            System.exit(0);
        };
    }

    private String fetchWeatherFromGemini(String apiKey) {
    // 1. Geminiに見せる「お手本」を詳しく書く
    String instructions = "東京都練馬区の明日の天気を調べてください。\n" +
                          "返答は必ず以下の形式にしてください。余計な挨拶はいりません。\n\n" +
                          "【明日の予報（練馬区）】\n" +
                          "・06:00：[絵文字] [天気] ([温度]℃)\n" +
                          "（中略：21:00まで同様に）\n\n" +
                          "AIアドバイス：[具体的なアドバイス]";

    // 2. この instructions（指示）をGeminiに送信する
    // 送信されたGeminiは、検索機能（Grounding）を使って最新情報をこの形に当てはめて返してくれます。
    
    // ここで返ってきた「文章」が、そのままカレンダーの「説明欄」に書き込まれます！
    }
}
