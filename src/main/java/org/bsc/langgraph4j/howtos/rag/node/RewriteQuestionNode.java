package org.bsc.langgraph4j.howtos.rag.node;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.howtos.rag.RagState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class RewriteQuestionNode implements NodeAction<RagState> {
    private static final Logger log = LoggerFactory.getLogger(RewriteQuestionNode.class);

    private final ChatModel chatModel;

    public RewriteQuestionNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(RagState state) {
        var question = state.originalQuestion();
        log.info("[Node:rewrite_question] 开始调用模型改写问题，原问题字符数={}", question.length());
        var prompt = "Original question: %s\nCurrent search question: %s\nFormulate an improved search question."
                .formatted(question, state.searchQuery());
        var response = chatModel.chat(UserMessage.from(prompt));
        log.info("[Node:rewrite_question] 问题改写完成，将重新进入 generate_query_or_respond");
        var searchQuery = response.aiMessage().text();
        return Map.of(
                "messages", UserMessage.from(searchQuery),
                RagState.SEARCH_QUERY, searchQuery);
    }
}
