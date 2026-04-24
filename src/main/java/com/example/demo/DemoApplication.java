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
            System.out.println("--- AIエージェント起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                // モデルのフォールバックは維持（エラー対策のため）
                String weatherInfo = fetchWeatherWithFallback(apiKey);

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
                targetDate.set(java.util.Calendar.SECOND, 0);

                Event event = new Event()
                    .setSummary("AI天気予報：明日の練馬区")
                    .setDescription(weatherInfo);

                EventDateTime start = new EventDateTime()
                    .setDateTime(new DateTime(targetDate.getTime()))
                    .setTimeZone("Asia/Tokyo");
                event.setStart(start);

                java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
                endDate.add(java.util.Calendar.MINUTE, 30);
                EventDateTime end = new EventDateTime()
                    .setDateTime(new DateTime(endDate.getTime()))
                    .setTimeZone("Asia/Tokyo");
                event.setEnd(end);

                service.events().insert(calendarId, event).execute();
                System.out.println("--- 成功：カレンダーに登録しました ---");

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        };
    }

    private String fetchWeatherWithFallback(String apiKey) {
        // 現時点で最も安定しているモデルを指定
        String[] models = {"gemini-1.5-flash", "gemini-1.5-pro"};
        for (String modelName : models) {
            String result = callGeminiApi(modelName, apiKey);
            if (!result.contains("【APIエラー】")) return result;
        }
        return "【全モデル制限中】明日またお試しください。";
    }

    private String callGeminiApi(String modelName, String apiKey) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm");
            String currentTime = sdf.format(cal.getTime()); // 現在時刻（14:55頃）を取得

            // プロンプトを「14:55時点の予報」を意識するように改良
            String prompt = "現在は " + currentTime + " です。この直近の検索データに基づき、明日（本日より翌日）の東京都練馬区の天気を答えてください。" +
                            "特に、現時点（14:55）で発表されている最新の予報を反映させてください。" +
                            "回答は以下の形式のみ、1回だけ出力してください。" +
                            "【明日の予報(練馬区)】" +
                            "・06:00: [天気] (気温/降水確率)" +
                            "・09:00: [天気] (気温/降水確率)" +
                            "・12:00: [天気] (気温/降水確率)" +
                            "・15:00: [天気] (気温/降水確率)" +
                            "・18:00: [天気] (気温/降水確率)" +
                            "・21:00: [天気] (気温/降水確率)" +
                            "AIアドバイス: [14:55時点の最新情報を踏まえた指示]" +
                            "参考サイト: [URL]";

            String safePrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n");
            String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + safePrompt + "\"}]}],\"tools\":[{\"googleSearch\":{}}]}";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) return "【APIエラー】" + response.statusCode();

            Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"(.+?)\"");
            Matcher matcher = pattern.matcher(response.body());
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                sb.append(matcher.group(1).replace("\\n", "\n").replace("\\\"", "\""));
            }

            String output = sb.toString().trim();
            // 重複カット
            int first = output.indexOf("【明日の予報");
            if (first != -1) {
                int second = output.indexOf("【明日の予報", first + 7);
                if (second != -1) output = output.substring(0, second).trim();
            }
            return output;

        } catch (Exception e) {
            return "【システムエラー】 " + e.getMessage();
        }
    }
}
