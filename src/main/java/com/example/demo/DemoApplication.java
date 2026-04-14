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
        System.out.println("--- Gemini APIへ天気検索をリクエスト中... ---");
        try {
            // 1. あの「きれいな表記」を守らせるためのプロンプト（指示書）
            String prompt = "東京都練馬区の明日の天気をGoogle検索を使って最新情報で調べてください。\n" +
                            "回答は必ず以下のフォーマットを厳守し、余計な挨拶や説明は一切含めないでください。カレンダーにそのまま登録します。\n\n" +
                            "【明日の予報（練馬区）】\n" +
                            "・06:00：[絵文字] [天気] ([気温]℃)\n" +
                            "・09:00：[絵文字] [天気] ([気温]℃)\n" +
                            "・12:00：[絵文字] [天気] ([気温]℃)\n" +
                            "・15:00：[絵文字] [天気] ([気温]℃)\n" +
                            "・18:00：[絵文字] [天気] ([気温]℃)\n" +
                            "・21:00：[絵文字] [天気] ([気温]℃)\n\n" +
                            "AIアドバイス：[傘の必要性や服装への一言]";

            // 2. Geminiに送るためのJSONデータを作成（Google検索ツールを有効化）
            String requestBody = "{\n" +
                    "  \"contents\": [{\"parts\": [{\"text\": \"" + prompt.replace("\n", "\\n") + "\"}]}],\n" +
                    "  \"tools\": [{\"googleSearch\": {}}]\n" +
                    "}";

            // 3. HTTPクライアントでGemini 1.5 Flashに送信
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // 4. 結果を受け取る
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            // 5. カレンダーAPI用にすでに入っているGsonを使って、返ってきたJSONからテキストだけを抽出
            com.google.gson.JsonObject jsonResponse = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            
            return jsonResponse
                    .getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();

        } catch (Exception e) {
            e.printStackTrace();
            return "【エラー】天気の取得に失敗しました。\n詳細: " + e.getMessage();
        }
    }
}
