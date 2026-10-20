package io.learn.langgraph4j.langchain4j;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class OpenAiModelTest {
    public static void main(String[] args) {
        ChatModel model = OpenAiChatModel.builder()
                .modelName("MiniMax-M3")
                .baseUrl("https://api.minimax.io/v1")
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
