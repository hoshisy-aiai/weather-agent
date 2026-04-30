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
            System.out.println("--- Yahoo!指定・検品ガード版AI起動 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                String weatherInfo = fetchWeatherWithBruteForce(apiKey);

                // 全リトライ失敗、または制限中の場合はカレンダー登録しない
                if (weatherInfo.equals("【制限中】")) {
                    System.err.println("正しい形式の回答が得られなかったため、カレンダー登録を中止しました。");
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

                // カレンダーの予定枠を15時に設定
                java.util.Calendar targetDate = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
                targetDate.set(java.util.Calendar.HOUR_OF_DAY, 15);
                targetDate.set(java.util.Calendar.MINUTE, 0);

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
        // 利用可能なモデル一覧
        String[] models = {"gemini-2.0-flash", "gemini-2.5-flash"};
        
        for (String modelName : models) {
            for (int i = 1; i <= 3; i++) {
                System.out.println(modelName + " で試行中... (" + i + "回目)");
                String result = callGeminiApi(modelName, apiKey);

                // エラー文字が含まれていなければ「検品合格」として採用
                if (!result.contains("【APIエラー】") && !result.contains("【システムエラー】")) {
                    return result;
                }
                
                System.out.println("回答が不完全なため、リトライします...");
                int waitTime = (i == 1) ? 20000 : 60000; 
                try { Thread.sleep(waitTime); } catch (InterruptedException e) {}
            }
        }
        return "【制限中】";
    }

    private String callGeminiApi(String modelName, String apiKey) {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
            java.util.Calendar tomorrow = (java.util.Calendar) cal.clone();
            tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
            
            TimeZone tzTokyo = TimeZone.getTimeZone("Asia/Tokyo");

            SimpleDateFormat sdfDate = new SimpleDateFormat("M月d日");
            sdfDate.setTimeZone(tzTokyo);
            String tomorrowDateStr = sdfDate.format(tomorrow.getTime());

            SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy年MM月dd日 HH:mm");
            sdfFull.setTimeZone(tzTokyo);
            String currentTime = sdfFull.format(cal.getTime());

            SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");
            sdfTime.setTimeZone(tzTokyo);
            String promptTime = sdfTime.format(cal.getTime());

            String[] weekDays = {"日", "月", "火", "水", "木", "金", "土"};
            String tomorrowDayOfWeek = weekDays[tomorrow.get(java.util.Calendar.DAY_OF_WEEK) - 1];
            
            // ★プロンプトで Yahoo!天気を指定
            String prompt = "【最重要：明日の日付と曜日は " + tomorrowDateStr + "(" + tomorrowDayOfWeek + ") です。絶対に間違えないでください】\n" +
                            "現在は " + currentTime + " です。Google検索を使い、明日 " + tomorrowDateStr + "(" + tomorrowDayOfWeek + ") の東京都練馬区の天気を調べて回答してください。\n" +
                            "データが取得できない場合でも言い訳は不要です。必ず以下の形式で「1回だけ」出力し、絶対に繰り返さないこと。\n\n" +
                            "【明日の予報(練馬区)】【" + promptTime + "時点】\n" +
                            "・06:00: [天気] (気温/降水確率)\n" +
                            "・09:00: [天気] (気温/降水確率)\n" +
                            "・12:00: [天気] (気温/降水確率)\n" +
                            "・15:00: [天気] (気温/降水確率)\n" +
                            "・18:00: [天気] (気温/降水確率)\n" +
                            "・21:00: [天気] (気温/降水確率)\n\n" +
                            "AIアドバイス: [服装のアドバイスを1文で]\n" +
                            "参考サイト: weather.yahoo.co.jp";

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

            // ★検品：指定したタイトルが含まれているかチェック
            if (!output.contains("【明日の予報(練馬区)】")) {
                return "【システムエラー】フォーマットが正しくありません";
            }

            // フッターでカット
            int cutPoint = output.indexOf("weather.yahoo.co.jp");
            if (cutPoint == -1) cutPoint = output.indexOf("tenki.jp"); // 念のため旧サイト名でもチェック
            
            if (cutPoint != -1) {
                // サイト名の後ろに続く可能性のある文字も含めてカット
                output = output.substring(0, Math.min(output.length(), cutPoint + 20)).trim();
            }
            
            return output;

        } catch (Exception e) {
            return "【システムエラー】通信に失敗しました";
        }
    }
}
