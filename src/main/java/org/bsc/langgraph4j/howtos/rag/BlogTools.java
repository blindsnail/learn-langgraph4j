package org.bsc.langgraph4j.howtos.rag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.stream.Collectors;

class BlogTools {
    private final ContentRetriever retriever;

    BlogTools(ContentRetriever retriever) {
        this.retriever = retriever;
    }

    @Tool("Search and return information about Lilian Weng blog posts.")
    String retrieveBlogPosts(@P("search query") String query) {
        return retriever.retrieve(new Query(query)).stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));
    }
}
