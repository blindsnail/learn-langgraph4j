package org.bsc.langgraph4j.howtos.rag.node;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.howtos.rag.RagState;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RetrieveNode implements NodeAction<RagState> {
    private final LC4jToolService toolService;

    public RetrieveNode(LC4jToolService toolService) {
        this.toolService = toolService;
    }

    @Override
    public Map<String, Object> apply(RagState state) {
        var aiMessage = state.lastMessage()
                .filter(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .filter(AiMessage::hasToolExecutionRequests)
                .orElseThrow(() -> new IllegalStateException("Expected an AI message with tool requests"));

        var updates = new HashMap<>(toolService.execute(
                        aiMessage.toolExecutionRequests(),
                        InvocationContext.builder()
                                .invocationParameters(InvocationParameters.from(state.data()))
                                .build(),
                        "messages")
                .thenApply(Command::update)
                .join());

        var documents = ((List<?>) updates.get("messages")).stream()
                .map(ToolExecutionResultMessage.class::cast)
                .map(ToolExecutionResultMessage::text)
                .collect(Collectors.joining("\n\n"));
        updates.put(RagState.DOCUMENTS, documents);
        return updates;
    }
}
