package org.bsc.langgraph4j.howtos.rag.node;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.howtos.rag.RagState;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class GenerateQueryOrRespondNode implements NodeAction<RagState> {
    private static final Logger log = LoggerFactory.getLogger(GenerateQueryOrRespondNode.class);

    private final LC4jToolService toolService;
    private final ChatModel chatModel;

    public GenerateQueryOrRespondNode(LC4jToolService toolService, ChatModel chatModel) {
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
        log.info("[Node:generate_query_or_respond] 开始调用模型，判断是否需要检索");
        var aiMessage = chatModel.chat(request).aiMessage();
        log.info("[Node:generate_query_or_respond] 模型调用完成，耗时={} ms", elapsedMillis(modelStarted));
        if (!aiMessage.hasToolExecutionRequests()) {
            log.info("[Node:generate_query_or_respond] 模型直接回答，无需执行检索工具");
            return Map.of(
                    "messages", aiMessage,
                    RagState.DOCUMENTS, "",
                    RagState.ANSWER, aiMessage.text());
        }

        return Map.of("messages", aiMessage);
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
