package org.bsc.langgraph4j.howtos.rag.node;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.howtos.rag.RagState;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryAndRetrieveNode implements NodeAction<RagState> {
    private static final Logger log = LoggerFactory.getLogger(QueryAndRetrieveNode.class);

    private final LC4jToolService toolService;
    private final ChatModel chatModel;

    public QueryAndRetrieveNode(LC4jToolService toolService, ChatModel chatModel) {
        this.toolService = toolService;
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(RagState state) {
        var params = ChatRequestParameters.builder()
                .toolSpecifications(toolService.toolSpecifications())
                .build();
        var request = ChatRequest.builder()
                .parameters(params)
                .messages(state.messages())
                .build();
        long modelStarted = System.nanoTime();
        log.info("[Node:query_and_retrieve] 开始调用模型，判断是否需要检索");
        var aiMessage = chatModel.chat(request).aiMessage();
        log.info("[Node:query_and_retrieve] 模型调用完成，耗时={} ms", elapsedMillis(modelStarted));
        if (!aiMessage.hasToolExecutionRequests()) {
            log.info("[Node:query_and_retrieve] 模型直接回答，无需执行检索工具");
            return Map.of(
                    "messages", aiMessage,
                    RagState.DOCUMENTS, "",
                    RagState.ANSWER, aiMessage.text());
        }

        // Preserve the same conversation context that the separate retrieve node received.
        var messages = new ArrayList<>(state.messages());
        messages.add(aiMessage);
        var invocationData = new HashMap<>(state.data());
        invocationData.put("messages", messages);

        long toolsStarted = System.nanoTime();
        log.info("[Node:query_and_retrieve] 开始执行工具，数量={}，工具={}",
                aiMessage.toolExecutionRequests().size(),
                aiMessage.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList());
        var updates = new HashMap<>(toolService.execute(
                        aiMessage.toolExecutionRequests(),
                        InvocationContext.builder()
                                .invocationParameters(InvocationParameters.from(invocationData))
                                .build(),
                        "messages")
                .thenApply(Command::update)
                .join());
        log.info("[Node:query_and_retrieve] 工具执行完成，耗时={} ms", elapsedMillis(toolsStarted));

        // Return only new messages; MessagesState appends them to the existing history.
        var newMessages = new ArrayList<ChatMessage>();
        newMessages.add(aiMessage);
        var documents = new ArrayList<String>();
        for (var result : (List<?>) updates.get("messages")) {
            var toolResult = (ToolExecutionResultMessage) result;
            newMessages.add(toolResult);
            documents.add(toolResult.text());
        }
        updates.put("messages", newMessages);
        updates.put(RagState.DOCUMENTS, String.join("\n\n", documents));
        return updates;
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
