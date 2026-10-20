package org.bsc.langgraph4j.howtos.rag.edge;

import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.howtos.rag.RagState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradeDocumentsEdge implements EdgeAction<RagState> {
    private static final Logger log = LoggerFactory.getLogger(GradeDocumentsEdge.class);

    private final DocumentGrader documentGrader;

    public GradeDocumentsEdge(ChatModel chatModel) {
        this.documentGrader = new DocumentGrader(chatModel);
    }

    @Override
    public String apply(RagState state) {
        var question = state.originalQuestion();
        var context = state.documents();
        String prompt = "Retrieved document:\n" + context + "\n\nUser question: " + question;

        log.info("[Edge:grade_documents] 开始调用模型评估相关性，问题字符数={}，上下文字符数={}",
                question.length(), context.length());
        var score = documentGrader.grade(prompt);
        log.info("[Edge:grade_documents] 文档相关性评分={}", score);

        return "yes".equalsIgnoreCase(score) ? "generate_answer" : "rewrite_question";
    }
}
