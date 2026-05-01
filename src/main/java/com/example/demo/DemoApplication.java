package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner runWeatherTask(WebClient.Builder webClientBuilder) {
        return args -> {
            // 2026/05/01 調査済みの安定モデルを指定
            String modelId = "gemini-2.5-flash"; 
            String apiKey = System.getenv("GEMINI_API_KEY");
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + apiKey;

            WebClient webClient = webClientBuilder.build();

            // AIへの指示（システムプロンプト的な役割をメッセージに込める）
            String prompt = "あなたは優秀な気象アドバイザーです。東京の天気を調べ、" +
                            "単なる予報だけでなく、その天気に合わせた服装や持ち物のアドバイスを、" +
                            "親しみやすい日本語で、見やすい箇条書きで回答してください。";

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                    "temperature", 0.7,
                    "topP", 0.95,
                    "maxOutputTokens", 1000
                )
            );

            System.out.println("\n=== 🤖 Gemini " + modelId + " エージェント起動中... ===\n");

            webClient.post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    try {
                        // レスポンスからテキスト部分を抽出
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                        List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                        return Mono.just(parts.get(0).get("text"));
                    } catch (Exception e) {
                        return Mono.just("エラー：AIからの回答を解析できませんでした。");
                    }
                })
                .subscribe(result -> {
                    System.out.println("--- ☀️ 東京の天気アドバイス ---");
                    System.out.println(result);
                    System.out.println("-------------------------------\n");
                    System.exit(0);
                }, error -> {
                    System.err.println("❌ エラーが発生しました: " + error.getMessage());
                    System.exit(1);
                });
        };
    }
}
