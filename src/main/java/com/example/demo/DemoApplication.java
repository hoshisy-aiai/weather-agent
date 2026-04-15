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

                // Geminiから天気とURLを取得
                String weatherInfo = fetchWeatherFromGemini(apiKey);

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
                
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                Calendar service = new Calendar.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                        .setApplicationName("Weather Agent")
                        .build();

                java.util.Calendar targetDate = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
                targetDate.set(java.util.Calendar.HOUR_OF_DAY, 15); // 今日の15時にセット
                targetDate.set(java.util.Calendar.MINUTE, 0);
                targetDate.set(java.util.Calendar.SECOND, 0);

                Event event = new Event()
                    .setSummary("AI天気予報：明日の練馬区")
                    .setDescription(weatherInfo); // ここに予報と参考URLが入ります

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
            System.exit(0);
        };
    }

    private String fetchWeatherFromGemini(String apiKey) {
        String modelName = System.getenv("GEMINI_MODEL_NAME");
        if (modelName == null || modelName.isEmpty()) {
            modelName = "gemini-2.5-flash"; 
        }
        
        try {            
            // プロンプト：URLの出力指示を追加
            String prompt = "東京都練馬区の明日の天気をGoogle検索で詳しく調べてください。\n" +
                            "回答は以下のフォーマットのみを出力し、挨拶や重複は厳禁です。末尾に必ず参考サイトURLを添えてください。\n\n" +
                            "【明日の予報（練馬区）】\n" +
                            "・06:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・09:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・12:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・15:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・18:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・21:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n\n" +
                            "AIアドバイス：[服装や傘の指示]\n\n" +
                            "参考サイト：[ここにURLを直接記載]";

            String requestBody = "{\n" +
                    "  \"contents\": [{\"parts\": [{\"text\": \"" + prompt.replace("\n", "\\n") + "\"}]}],\n" +
                    "  \"tools\": [{\"googleSearch\": {}}]\n" +
                    "}";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            
            // 汎用パース：テキストを抽出
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                result.append(matcher.group(1).replace("\\n", "\n"));
            }

            if (result.length() > 0) {
                String output = result.toString().trim();
                
                // --- 重複排除の最終兵器 ---
                // 「【明日の予報」というタイトルが2回以上出現するかチェック
                String searchTag = "【明日の予報";
                int firstIndex = output.indexOf(searchTag);
                int secondIndex = output.indexOf(searchTag, firstIndex + searchTag.length());

                if (secondIndex != -1) {
                    // 2回目に出現したタイトルの直前でバッサリ切り落とす
                    output = output.substring(0, secondIndex).trim();
                }

                // さらに、URLが末尾にあることを確認して完了
                return output;
            } else {
                return "【APIエラー】応答にテキストが含まれていません:\n" + body;
            }

        } catch (Exception e) {
            return "【システムエラー】 " + e.getMessage();
        }
    }
}
