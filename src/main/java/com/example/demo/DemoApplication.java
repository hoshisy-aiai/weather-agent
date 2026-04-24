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
            System.out.println("--- 粘り強いAIエージェント起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                // 1. AIに天気を聞く（承認済みリストのモデルでリトライを繰り返す）
                String weatherInfo = fetchWeatherWithBruteForce(apiKey);

                // 2. もしAIが全滅していたら、カレンダーには書かずに終了する（ガード機能）
                if (weatherInfo.equals("【制限中】")) {
                    System.err.println("AIが応答しませんでした。カレンダーへの登録を中止します。");
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

                // 15:00の予定として登録
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
                event.setEnd(new EventDateTime().setDateTime(new DateTime(endDate.getTime())).setTimeZone("Asia/Tokyo"));

                service.events().insert(calendarId, event).execute();
                System.out.println("--- 成功：カレンダーに登録しました ---");

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        };
    }

    private String fetchWeatherWithBruteForce(String apiKey) {
        // ★ご提示いただいた「利用可能リスト」に基づくモデル名
        String[] models = {
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        };
        
        for (String modelName : models) {
            for (int i = 1; i <= 3; i++) {
                System.out.println(modelName + " にアタック中... (" + i + "回目)");
                String result = callGeminiApi(modelName, apiKey);

                // 成功判定（APIエラーが含まれていなければOK）
                if (!result.contains("【APIエラー】") && !result.contains("【システムエラー】")) {
                    return result;
                }

                // 2回目以降は待ち時間を長く（60秒）して、制限解除を待つ
                int waitTime = (i == 1) ? 20000 : 60000; 
                System.out.println("混雑中... " + (waitTime/1000) + "秒待機して再試行します。");
                try { Thread.sleep(waitTime); } catch (InterruptedException e) {}
            }
        }
        return "【制限中】";
    }

    private String callGeminiApi(String modelName, String apiKey) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            String currentTime = new SimpleDateFormat("yyyy年MM月dd日 14:55").format(cal.getTime());

            String prompt = "現在は " + currentTime + " です。最新の検索を行い、明日（翌日）の東京都練馬区の天気を答えてください。" +
                            "必ず【14:55時点】の最新情報を反映すること。" +
                            "回答形式：\n【明日の予報(練馬区)】\n・06:00: [天気] (気温/降水確率)\n" +
                            "・09:00: [天気] (気温/降水確率)\n・12:00: [天気] (気温/降水確率)\n" +
                            "・15:00: [天気] (気温/降水確率)\n・18:00: [天気] (気温/降p水確率)\n" +
                            "・21:00: [天気] (気温/降水確率)\n" +
                            "AIアドバイス: [14:55時点の最新情報を踏まえたアドバイス]\n参考サイト: [URL]";

            String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt.replace("\n", "\\n").replace("\"", "\\\"") + "\"}]}],\"tools\":[{\"googleSearch\":{}}]}";

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
            return sb.toString().trim();

        } catch (Exception e) {
            return "【システムエラー】";
        }
    }
}
