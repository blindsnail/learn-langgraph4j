package org.bsc.langgraph4j.howtos.rag;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.Map;

public class RagState extends MessagesState<ChatMessage> {
    public static final String ORIGINAL_QUESTION = "originalQuestion";
    public static final String SEARCH_QUERY = "searchQuery";
    public static final String DOCUMENTS = "documents";
    public static final String ANSWER = "answer";

    // Conversation messages append; the other fields replace their previous values.
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_STATE, MessagesState.SCHEMA.get(MESSAGES_STATE),
            ORIGINAL_QUESTION, Channels.base(() -> ""),
            SEARCH_QUERY, Channels.base(() -> ""),
            DOCUMENTS, Channels.base(() -> ""),
            ANSWER, Channels.base(() -> "")
    );

    public RagState(Map<String, Object> initData) {
        super(initData);
    }

    public String originalQuestion() {
        return this.<String>value(ORIGINAL_QUESTION).orElseThrow();
    }

    public String searchQuery() {
        return this.<String>value(SEARCH_QUERY).orElseThrow();
    }

    public String documents() {
        return this.<String>value(DOCUMENTS).orElse("");
    }

    public String answer() {
        return this.<String>value(ANSWER).orElse("");
    }
}
