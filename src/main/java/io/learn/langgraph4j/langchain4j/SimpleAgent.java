package io.learn.langgraph4j.langchain4j;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public class SimpleAgent {
    public static void main(String[] args) throws GraphStateException {
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

        CompiledGraph<AgentExecutor.State> agent = AgentExecutor.builder()
                .chatModel(model)
                .toolsFromObject(new TestTool())
                .build()
                .compile();

        List<UserMessage> messages =
                List.of(UserMessage.from("perform test twice and return number of current active threads"));
        for (var item : agent.stream(GraphInput.args(Map.of("messages", messages)), RunnableConfig.empty())) {
            System.out.println(item);
        }
    }

}
