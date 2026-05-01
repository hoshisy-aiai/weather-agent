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
            System.out.println("--- 修正版：今日15時登録・本文フル抽出AI起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                String weatherInfo = fetchWeatherWithBruteForce(apiKey);

                if (weatherInfo.equals("【制限中】")) {
                    System.err.println("リトライ上限に達しました。");
                    System.exit(0);
                }

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
                
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                Calendar service = new Calendar.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                        .setApplicationName("Weather Agent")
                        .build();

                // 【修正①】日付を「今日」に。時間は「15時」に固定。
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
                System.out.println("--- 完了：今日の15時に明日の予報を登録しました ---");

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        };
    }

    private String fetchWeatherWithBruteForce(String apiKey) {
        String modelName = "gemini-2.5-flash"; // 安定版
        for (int i = 1; i <= 3; i++) {
            System.out.println(modelName + " 試行中... (" + i + "回目)");
            String result = callGeminiApi(modelName, apiKey);
            if (!result.contains("【エラー】")) return result;
            try { Thread.sleep(20000); } catch (InterruptedException e) {}
        }
        return "【制限中】";
    }

    private String callGeminiApi(String modelName, String apiKey) {
        try {
            java.util.Calendar tomorrow = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
            String tomorrowStr = new SimpleDateFormat("M月d日").format(tomorrow.getTime());

            // 本文が消えないよう、出力順序とフォーマットをより厳格に指示
            String prompt = "Google検索で明日" + tomorrowStr + "の練馬区の天気を調べ、以下の形式で回答してください。\\n" +
                            "1. タイトル： 🗓️ 【明日の予報(練馬区)】\\n" +
                            "2. 区切り線： ━━━━━━━━━━━━━━━━━━━━\\n" +
                            "3. 3時間ごとの天気(06時〜21時)を絵文字・気温・降水確率付きで1行ずつ書く。\\n" +
                            "4. 区切り線： ━━━━━━━━━━━━━━━━━━━━\\n" +
                            "5. AIアドバイス： 服装と持ち物を1文で。\\n" +
                            "挨拶や『検索しました』等のメタ発言は一切禁止。出力例にない項目は書かない。";

            String requestBody = "{" +
                    "\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}]" +
                    ",\"tools\":[{\"googleSearch\":{}}]" +
                    ",\"generationConfig\":{\"temperature\":0.1, \"maxOutputTokens\":800}" +
                    "}";

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            // 【修正②】本文が途切れないよう正規表現を強化
            // 最初の "text": " の後から、次にエスケープされていない " が来るまでを全部取る
            Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"([\\s\\S]+?)(?<!\\\\)\"");
            Matcher matcher = pattern.matcher(response.body());
            
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String text = matcher.group(1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\r", "");
                // 検索過程のログなどは無視し、実際の回答っぽい部分だけ採用
                if (text.contains("練馬区") || text.contains("予報")) {
                    sb.append(text);
                }
            }

            String output = sb.toString().trim();
            return output.isEmpty() ? "【エラー】空の回答" : output;

        } catch (Exception e) {
            return "【エラー】" + e.getMessage();
        }
    }
}
