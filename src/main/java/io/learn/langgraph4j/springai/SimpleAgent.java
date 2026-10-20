package io.learn.langgraph4j.springai;

import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;

public class SimpleAgent {
    public static void main(String[] args) throws GraphStateException {
        ChatModel model = AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .apiKey(System.getenv("MINIMAX_API_KEY"))
                        .baseUrl("https://api.minimax.io/anthropic")
                        .model("MiniMax-M3")
                        .temperature(0.1)
                        .build())
                .build();

        var agent = AgentExecutor.builder()
                .chatModel(model)
                .toolsFromObject(new TestTool())
                .build()
                .compile();

        var messages = List.of(UserMessage.builder()
                .text("perform test twice and return number of current active threads")
                .build());
        for (var item : agent.stream(GraphInput.args(Map.of("messages", messages)), RunnableConfig.empty())) {
            System.out.println(item);
        }
    }

}
