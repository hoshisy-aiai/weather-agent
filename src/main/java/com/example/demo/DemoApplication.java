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
            System.out.println("--- 安定版 AIエージェント起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                // AIに天気を聞く（リトライ回数を調整）
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

                // タイトルに絵文字を入れるように工夫
                String title = "AI天気予報：明日の練馬区";
                if (weatherInfo.contains("☔")) title += " ☔";
                else if (weatherInfo.contains("☀️")) title += " ☀️";
                else if (weatherInfo.contains("☁️")) title += " ☁️";

                Event event = new Event()
                    .setSummary(title)
                    .setDescription(weatherInfo);

                EventDateTime start = new EventDateTime()
                    .setDateTime(new DateTime(targetDate.getTime()))
                    .setTimeZone("Asia/Tokyo");
                event.setStart(start);

                java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
                endDate.add(java.util.Calendar.MINUTE, 30);
                event.setEnd(new EventDateTime().setDateTime(new DateTime(endDate.getTime())).setTimeZone("Asia/Tokyo"));

                service.events().insert(calendarId, event).execute();
                System.out.println("--- 成功：カレンダーに登録完了 ---");

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        };
    }

    private String fetchWeatherWithBruteForce(String apiKey) {
        String[] models = {"gemini-2.0-flash", "gemini-1.5-flash"}; // 安定している1.5も候補に入れる
        
        for (String modelName : models) {
            for (int i = 1; i <= 2; i++) { // リトライを2回に減らして時間を短縮
                System.out.println(modelName + " で試行中... (" + i + "回目)");
                String result = callGeminiApi(modelName, apiKey);

                if (!result.startsWith("【エラー】")) {
                    return result;
                }
                
                try { Thread.sleep(5000); } catch (InterruptedException e) {}
            }
        }
        return "天気情報の取得に時間がかかりすぎました。Actionsのログを確認してください。";
    }

    private String callGeminiApi(String modelName, String apiKey) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            java.util.Calendar tomorrow = (java.util.Calendar) cal.clone();
            tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
            
            SimpleDateFormat sdfDate = new SimpleDateFormat("M月d日");
            sdfDate.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
            String tomorrowDateStr = sdfDate.format(tomorrow.getTime());

            String prompt = "明日 " + tomorrowDateStr + " の東京都練馬区の天気をGoogle検索で調べて、以下の形式で回答してください。\n" +
                            "・絵文字をたくさん使って視覚的に分かりやすくすること。\n" +
                            "・「06:00、09:00、12:00、15:00、18:00、21:00」の予報を必ず含めること。\n" +
                            "・最後に💡でAIアドバイスを添えること。\n" +
                            "・区切り線「━━━━」を使ってきれいに整えること。";

            String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt.replace("\n", "\\n").replace("\"", "\\\"") + "\"}]}],\"tools\":[{\"googleSearch\":{}}]}";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"(.+?)\"");
            Matcher matcher = pattern.matcher(response.body());
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                sb.append(matcher.group(1).replace("\\n", "\n").replace("\\\"", "\""));
            }

            String output = sb.toString().trim();
            if (output.isEmpty()) return "【エラー】回答が空でした";
            
            return output;

        } catch (Exception e) {
            return "【エラー】システムエラー";
        }
    }
}
