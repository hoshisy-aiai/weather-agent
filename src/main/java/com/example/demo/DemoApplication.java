package com.example.demo;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("--- AIエージェント起動完了！ ---");
    }
}

@Component
class WeatherTask {

    // 以前の @Value("${gemini.api.key}") は消し、環境変数から直接取得する形にしています
    @Scheduled(cron = "0 0 15 * * *", zone = "Asia/Tokyo")
    public void executeWeatherTask() {
        try {
            String apiKey = System.getenv("GEMINI_API_KEY");
            String calendarId = System.getenv("CALENDAR_ID");

            if (apiKey == null || calendarId == null) {
                System.err.println("エラー: 環境変数（APIキーまたはカレンダーID）が設定されていません。");
                return;
            }

            // Google Calendar APIの準備
            Calendar service = GoogleCalendarService.getService();

            // 1. カレンダーに登録する「日付」の準備
            java.util.Calendar targetDate = java.util.Calendar.getInstance();
            targetDate.add(java.util.Calendar.DAY_OF_MONTH, 1); // 明日の日付

            // 2. ★ 応用編：世界時間の「午前6時」にセット（これが日本時間の15時になります）
            targetDate.set(java.util.Calendar.HOUR_OF_DAY, 6);
            targetDate.set(java.util.Calendar.MINUTE, 0);
            targetDate.set(java.util.Calendar.SECOND, 0);
            targetDate.set(java.util.Calendar.MILLISECOND, 0);

            // 3. 終了時刻（30分後）の作成
            java.util.Calendar endDate = (java.util.Calendar) targetDate.clone();
            endDate.add(java.util.Calendar.MINUTE, 30);

            // 4. イベントの作成
            Event event = new Event()
                .setSummary("AIエージェント：明日の天気確認")
                .setDescription("GitHub Actions（世界時間 06:00）経由で登録。日本時間では 15:00 です。");

            // 開始時刻のセット
            EventDateTime start = new EventDateTime()
                .setDateTime(new DateTime(targetDate.getTime()));
            event.setStart(start);

            // 終了時刻のセット
            EventDateTime end = new EventDateTime()
                .setDateTime(new DateTime(endDate.getTime()));
            event.setEnd(end);

            // 5. カレンダーへの書き込み実行
            service.events().insert(calendarId, event).execute();
            System.out.println("--- 成功：日本時間の15:00（世界時間06:00）に予定を登録しました ---");

        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}