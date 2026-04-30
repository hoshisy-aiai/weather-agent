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
import java.time.Duration;
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
            System.out.println("--- 粘り強い最終版 起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                String weatherInfo = fetchWeatherWithBruteForce(apiKey);

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
                
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                Calendar service = new Calendar.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                        .setApplicationName("Weather Agent")
                        .build();

                java.util.Calendar targetDate = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
                targetDate.set(java.util.Calendar.HOUR_OF_DAY, 15);
                targetDate.set(java.util.Calendar.MINUTE, 0);

                String title = "AI天気予報：明日の練馬区";
                if (weatherInfo.contains("☔")) title += " ☔";
                else if (weatherInfo.contains("☀️")) title += " ☀️";

                Event event = new Event()
                    .setSummary(title)
                    .setDescription(weatherInfo);

                event.setStart(new EventDateTime().setDateTime(new DateTime(targetDate.getTime())).setTimeZone("Asia/Tokyo"));
                java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
                endDate.add(java.util.Calendar.MINUTE, 30);
                event.setEnd(new EventDateTime().setDateTime(new DateTime(endDate.getTime())).setTimeZone("Asia/Tokyo"));

                service.events().insert(calendarId, event).execute();
                System.out.println("--- カレンダー登録完了 ---");

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        };
    }

    private String fetchWeatherWithBruteForce(String apiKey) {
        // 安定性の高い 1.5-flash をメインに、予備で 2.0-flash を使用
        String[] models = {"gemini-1.5-flash", "gemini-2.0-flash"};
        
        for (String modelName : models) {
            for (int i = 1; i <= 2; i++) {
                System.out.println(modelName + " で試行中... (" + i + "回目)");
                String result = callGeminiApi(modelName, apiKey);

                if (!result.startsWith("【エラー】")) {
                    return result;
                }
                // エラー時は少し待ってからリトライ
                try { Thread.sleep(10000); } catch (InterruptedException e) {}
            }
        }
        return "一時的にAIが混雑しています。少し時間を置いてから再度お試しください。";
    }

    private String callGeminiApi(String modelName, String apiKey) {
        try {
            java.util.Calendar tomorrow = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
            SimpleDateFormat sdf = new SimpleDateFormat("M月d日");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
            String dateStr = sdf.format(tomorrow.getTime());

            String prompt = "Google検索を使って、" + dateStr + "の東京都練馬区の天気を調べてください。\\n" +
                            "その後、以下の形式で出力してください。\\n" +
                            "1. 各時間(06,09,12,15,18,21時)の天気・気温・降水確率（絵文字を使って！）\\n" +
                            "2. 💡AIアドバイス（服装や持ち物について）\\n" +
                            "3. 区切り線「━━━━」を使って見やすく整えること。";

            String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}],\"tools\":[{\"googleSearch\":{}}]}";

            // タイムアウトを120秒（長め）に設定
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(120))
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.out.println("API Error: " + response.statusCode());
                return "【エラー】APIステータスコード: " + response.statusCode();
            }

            Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"(.+?)\"");
            Matcher matcher = pattern.matcher(response.body());
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                sb.append(matcher.group(1).replace("\\n", "\n").replace("\\\"", "\""));
            }

            String output = sb.toString().trim();
            return output.isEmpty() ? "【エラー】回答が空" : output;

        } catch (Exception e) {
            return "【エラー】" + e.getMessage();
        }
    }
}
