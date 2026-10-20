package org.bsc.langgraph4j.quickstart.langchain4j;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;

public class AnthropicModelTest {
    public static void main(String[] args) {
        ChatModel model = AnthropicChatModel.builder()
                .modelName("MiniMax-M3")
                .baseUrl("https://api.minimax.io/anthropic/v1")
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build();

        String result = model.chat("你好，介绍一下自己");
        System.out.println(result);
    }
}
