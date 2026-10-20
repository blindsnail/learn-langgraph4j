package io.learn.langgraph4j.springai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;

public class AnthropicModelTest {
    public static void main(String[] args) {
        ChatModel model = AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .model("MiniMax-M3")
                        .baseUrl("https://api.minimax.io/anthropic")
                        .apiKey(System.getenv("MINIMAX_API_KEY"))
                        .temperature(0.1)
                        .build())
                .build();

        String result = model.call("你好，介绍一下自己");
        System.out.println(result);
    }
}
