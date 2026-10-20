package io.learn.langgraph4j.springai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

public class OpenAiModelTest {
    public static void main(String[] args) {
        ChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .model("MiniMax-M3")
                        .baseUrl("https://api.minimax.io/v1")
                        .apiKey(System.getenv("MINIMAX_API_KEY"))
                        .temperature(0.1)
                        .maxRetries(2)
                        .build())
                .build();

        String result = model.call("你好，介绍一下自己");
        System.out.println(result);
    }
}
