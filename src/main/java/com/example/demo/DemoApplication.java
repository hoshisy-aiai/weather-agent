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
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner runWeatherTask() {
        return args -> {
            System.out.println("--- 最終修正版：全文章抽出・今日15時登録 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                String weatherInfo = fetchWeatherWithGemini(apiKey);

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
                
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                Calendar service = new Calendar.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                        .setApplicationName("Weather Agent")
                        .build();

                // 今日の15時
                java.util.Calendar targetDate = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
                targetDate.set(java.util.Calendar.HOUR_OF_DAY, 15);
                targetDate.set(java.util.Calendar.MINUTE, 0);
                targetDate.set(java.util.Calendar.SECOND, 0);

                Event event = new Event()
                    .setSummary("🌤️ AI天気予報：明日の練馬区")
                    .setDescription(weatherInfo);

                event.setStart(new EventDateTime().setDateTime(new DateTime(targetDate.getTime())).setTimeZone("Asia/Tokyo"));
                java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
                endDate.add(java.util.Calendar.MINUTE, 30);
                event.setEnd(new EventDateTime().setDateTime(new DateTime(endDate.getTime())).setTimeZone("Asia/Tokyo"));

                service.events().insert(calendarId, event).execute();
                System.out.println("--- 完了：予報データをすべて書き込みました ---");

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        };
    }

    private String fetchWeatherWithGemini(String apiKey) throws Exception {
        java.util.Calendar tomorrow = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
        tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
        String tomorrowStr = new SimpleDateFormat("M月d日").format(tomorrow.getTime());

        String prompt = "Google検索で明日" + tomorrowStr + "の練馬区の天気を調べ、以下の形式で回答してください。\\n" +
                        "🗓️ 【明日の予報(練馬区)】\\n" +
                        "━━━━━━━━━━━━━━━━━━━━\\n" +
                        "・06時: [天気・気温・降水確率]\\n" +
                        "・09時: [天気・気温・降水確率]\\n" +
                        "・12時: [天気・気温・降水確率]\\n" +
                        "・15時: [天気・気温・降水確率]\\n" +
                        "・18時: [天気・気温・降水確率]\\n" +
                        "・21時: [天気・気温・降水確率]\\n" +
                        "━━━━━━━━━━━━━━━━━━━━\\n" +
                        "💡 AIアドバイス：[服装と持ち物を短く1文で]";

        String requestBody = "{" +
                "\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}]" +
                ",\"tools\":[{\"googleSearch\":{}}]" +
                ",\"generationConfig\":{\"temperature\":0.1, \"maxOutputTokens\":1000}" +
                "}";

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        
        // 【修正】JSONからテキストを抽出する正規表現をより厳密に
        Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(response.body());
        
        String longestText = "";
        while (matcher.find()) {
            String found = matcher.group(1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\r", "");
            // 最も長いテキストブロック（＝検索結果に基づいた回答本体）を採用
            if (found.length() > longestText.length()) {
                longestText = found;
            }
        }

        return longestText.isEmpty() ? "取得エラー" : longestText.trim();
    }
}
