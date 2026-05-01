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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.TimeZone;
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
            System.out.println("--- 2026/05/01 安定版 Gemini 2.5 Flash で再挑戦 ---");
            try {
                String apiKey = System.getenv("GEMINI_API_KEY");
                String calendarId = System.getenv("CALENDAR_ID");
                String credentialsJson = System.getenv("GOOGLE_CREDENTIALS");

                String weatherInfo = fetchWeatherWithGemini(apiKey);

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
                
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                Calendar service = new Calendar.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                        .setApplicationName("Weather Agent")
                        .build();

                // 明日の日付
                java.util.Calendar targetDate = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
                targetDate.add(java.util.Calendar.DAY_OF_MONTH, 1);
                targetDate.set(java.util.Calendar.HOUR_OF_DAY, 7); // 朝7時にセットして1日を確認しやすく
                targetDate.set(java.util.Calendar.MINUTE, 0);

                String title = "練馬区の天気予報";
                if (weatherInfo.contains("☔")) title += " ☔";
                else if (weatherInfo.contains("☀️") || weatherInfo.contains("晴")) title += " ☀️";

                Event event = new Event()
                    .setSummary(title)
                    .setDescription(weatherInfo);

                event.setStart(new EventDateTime().setDateTime(new DateTime(targetDate.getTime())).setTimeZone("Asia/Tokyo"));
                java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
                endDate.add(java.util.Calendar.HOUR, 1); // 1時間の枠を確保
                event.setEnd(new EventDateTime().setDateTime(new DateTime(endDate.getTime())).setTimeZone("Asia/Tokyo"));

                service.events().insert(calendarId, event).execute();
                System.out.println("--- 完璧！カレンダーに書き込みました ---");

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        };
    }

    private String fetchWeatherWithGemini(String apiKey) throws Exception {
        String modelName = "gemini-2.5-flash"; 
        
        java.util.Calendar tomorrow = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
        tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("M月d日");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        String dateStr = sdf.format(tomorrow.getTime());

        // プロンプト自体が回答に混ざらないよう「出力内容のみを返せ」と念押し
        String prompt = "Google検索を使用して、" + dateStr + "の東京都練馬区の天気を調べてください。\\n" +
                        "以下のルールを厳守し、結果のみを出力してください。私の指示文は含めないでください。\\n" +
                        "1. 06,09,12,15,18,21時の天気・気温・降水確率を絵文字付きで1行ずつ書く。\\n" +
                        "2. 💡 AIアドバイス として、服装と持ち物を合計2文以内で簡潔に書く。\\n" +
                        "3. 余計な挨拶や解説は一切不要。";

        // 回答がループしないよう generationConfig で制御
        String requestBody = "{" +
                "\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}]" +
                ",\"tools\":[{\"googleSearch\":{}}]" +
                ",\"generationConfig\":{\"temperature\":0.2, \"maxOutputTokens\":500}" +
                "}";

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"(.+?)\"");
        Matcher matcher = pattern.matcher(response.body());
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            sb.append(matcher.group(1).replace("\\n", "\n").replace("\\\"", "\""));
        }

        return sb.toString().trim();
    }
}
