package org.bsc.langgraph4j.howtos.rag.node;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.howtos.rag.RagState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class GenerateAnswerNode implements NodeAction<RagState> {
    private static final Logger log = LoggerFactory.getLogger(GenerateAnswerNode.class);

    private final ChatModel chatModel;

    public GenerateAnswerNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(RagState state) {
        var question = state.originalQuestion();
        var context = state.documents();
        var prompt = "Question: %s\n<context>\n%s\n</context>".formatted(question, context);
        log.info("[Node:generate_answer] 开始调用模型生成答案，问题字符数={}，上下文字符数={}",
                question.length(), context.length());
        var response = chatModel.chat(UserMessage.from(prompt));
        return Map.of(
                "messages", response.aiMessage(),
                RagState.ANSWER, response.aiMessage().text());
    }
}
