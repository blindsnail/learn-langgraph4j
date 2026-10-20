package org.bsc.langgraph4j.howtos.rag.edge;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

final class DocumentGrader {
    private final ChatModel chatModel;

    DocumentGrader(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    String grade(String prompt) {
        var response = chatModel.chat(
                SystemMessage.from("Grade whether the retrieved document is relevant to the user question. "
                        + "Return exactly one word: yes or no. Do not include any explanation."),
                UserMessage.from(prompt));
        return response.aiMessage().text().strip();
    }
}
