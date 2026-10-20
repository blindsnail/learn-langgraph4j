package org.bsc.langgraph4j.howtos.rag;

import dev.langchain4j.model.output.structured.Description;

public class GradeDocuments {
    @Description("Relevance score: 'yes' if relevant, or 'no' if not relevant")
    public String binaryScore;
}
