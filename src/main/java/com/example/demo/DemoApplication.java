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
            System.out.println("--- 改良版AIエージェント起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                String weatherInfo = fetchWeatherWithBruteForce(apiKey);

                if (weatherInfo.equals("【制限中】")) {
                    System.err.println("AIが応答しませんでした。カレンダー登録を中止します。");
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
        // 利用可能リストに基づいた最新モデル
        String[] models = {"gemini-2.5-flash", "gemini-2.0-flash"};
        
        for (String modelName : models) {
            for (int i = 1; i <= 3; i++) {
                System.out.println(modelName + " で試行中... (" + i + "回目)");
                String result = callGeminiApi(modelName, apiKey);

                if (!result.contains("【APIエラー】") && !result.contains("【システムエラー】")) {
                    return result;
                }
                int waitTime = (i == 1) ? 20000 : 60000; 
                try { Thread.sleep(waitTime); } catch (InterruptedException e) {}
            }
        }
        return "【制限中】";
    }

    private String callGeminiApi(String modelName, String apiKey) {
        try {
            // 現在時刻の取得
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            String currentTime = new SimpleDateFormat("yyyy年MM月dd日 HH:mm").format(cal.getTime());

            // ★改善①：明日の日付と曜日をJava側で正確に計算
            java.util.Calendar tomorrow = (java.util.Calendar) cal.clone();
            tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
            String[] weekDays = {"日", "月", "火", "水", "木", "金", "土"};
            String tomorrowDateStr = new SimpleDateFormat("M月d日").format(tomorrow.getTime());
            String tomorrowDayOfWeek = weekDays[tomorrow.get(java.util.Calendar.DAY_OF_WEEK) - 1];

            // AIへの指示（プロンプト）
            String prompt = "現在は " + currentTime + " です。最新の検索データを使用し、明日 " + tomorrowDateStr + "(" + tomorrowDayOfWeek + ") の東京都練馬区の天気を回答してください。\n" +
                            "【重要ルール】\n" +
                            "1. 曜日は必ず (" + tomorrowDayOfWeek + ") とすること。\n" +
                            "2. 回答は以下の形式で「1回だけ」出力し、絶対に繰り返さないこと。\n" +
                            "3. 余計な挨拶や重複したアドバイスは禁止。\n\n" +
                            "形式：\n" +
                            "【明日の予報(練馬区)】【14:55時点】\n" +
                            "・06:00: [天気] (気温/降水確率)\n" +
                            "・09:00: [天気] (気温/降水確率)\n" +
                            "・12:00: [天気] (気温/降水確率)\n" +
                            "・15:00: [天気] (気温/降水確率)\n" +
                            "・18:00: [天気] (気温/降水確率)\n" +
                            "・21:00: [天気] (気温/降水確率)\n\n" +
                            "AIアドバイス: [最新状況を踏まえた服装の指示を1つだけ]\n" +
                            "参考サイト: tenki.jp";

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

            String output = sb.toString().trim();

            // ★改善②・③：もしAIが重複して出力してしまった場合の「お掃除」処理
            // 最初に見つけた【明日の予報】の位置を探す
            int firstIdx = output.indexOf("【明日の予報");
            if (firstIdx != -1) {
                // 2つ目の【明日の予報】がもしあれば、そこから先を消す
                int secondIdx = output.indexOf("【明日の予報", firstIdx + 5);
                if (secondIdx != -1) {
                    output = output.substring(0, secondIdx).trim();
                }
            }
            
            // 参考サイトが2回出てくる問題も、最後の「tenki.jp」の後に余計なものがあれば削る
            int lastSiteIdx = output.lastIndexOf("tenki.jp");
            if (lastSiteIdx != -1) {
                output = output.substring(0, lastSiteIdx + 8).trim();
            }

            return output;

        } catch (Exception e) {
            return "【システムエラー】";
        }
    }
}
