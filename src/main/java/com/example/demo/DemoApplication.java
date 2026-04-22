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

                // ★改良：自動切り替え機能付きのメソッドを呼び出す
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

    // ★新設：モデルを順番に試す「フォールバック」ロジック
private String fetchWeatherWithFallback(String apiKey) {
        // 確実に存在する安定したモデルのリスト
        String[] models = {"gemini-2.0-flash", "gemini-1.5-flash"};
        StringBuilder errorLog = new StringBuilder();

        for (String modelName : models) {
            System.out.println("モデル " + modelName + " で取得を試みます...");
            String result = callGeminiApi(modelName, apiKey);
            
            // 成功（エラーメッセージで始まらない場合）は結果を返す
            if (!result.startsWith("【APIエラー】") && !result.startsWith("【システムエラー】")) {
                return result;
            }
            
            // 失敗した場合はログに記録して次へ
            errorLog.append("・").append(modelName).append(": ").append(result).append("\n");
        }
        
        return "【全モデル制限中】本日の無料枠を使い切った可能性があります。明日までお待ちください。\n\n詳細ログ:\n" + errorLog.toString();
    }
    
    private String callGeminiApi(String modelName, String apiKey) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日");
            String todayStr = sdf.format(cal.getTime());
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
            String tomorrowStr = sdf.format(cal.getTime());

            // ユーザーさんの指定した詳細なプロンプト
            String prompt = "現在は " + todayStr + " です。明日 " + tomorrowStr + " の東京都練馬区の天気をGoogle検索で詳しく調べてください。\n" +
                            "回答は以下のフォーマットのみを出力し、挨拶や重複は厳禁です。末尾に必ず参考サイトURLを添えてください。\n\n" +
                            "【明日の予報（練馬区）】\n" +
                            "・06:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・09:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・12:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・15:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・18:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n" +
                            "・21:00：[絵文字] [天気] ([気温]℃ / 降水[確率]%)\n\n" +
                            "AIアドバイス：[服装や傘の指示]\n\n" +
                            "参考サイト：[URL]";

            String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt.replace("\n", "\\n") + "\"}]}],\"tools\":[{\"googleSearch\":{}}]}";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            // HTTPステータスが200（成功）でない場合はエラーとして返す
            if (response.statusCode() != 200) {
                return "【APIエラー】ステータスコード: " + response.statusCode() + "\n" + response.body();
            }

            String body = response.body();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                sb.append(matcher.group(1).replace("\\n", "\n"));
            }

            if (sb.length() > 0) {
                String output = sb.toString().trim();
                // 重複カットのロジック
                String searchTag = "【明日の予報";
                int firstIndex = output.indexOf(searchTag);
                int secondIndex = output.indexOf(searchTag, firstIndex + searchTag.length());
                if (secondIndex != -1) {
                    output = output.substring(0, secondIndex).trim();
                }
                return output;
            } else {
                return "【APIエラー】応答にテキストが含まれていません:\n" + body;
            }

        } catch (Exception e) {
            return "【システムエラー】 " + e.getMessage();
        }
    }
}
