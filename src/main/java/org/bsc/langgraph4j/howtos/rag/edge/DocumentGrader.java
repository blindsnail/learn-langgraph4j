package org.bsc.langgraph4j.howtos.rag.edge;

import dev.langchain4j.service.SystemMessage;
import org.bsc.langgraph4j.howtos.rag.GradeDocuments;

interface DocumentGrader {
    @SystemMessage("Grade whether the retrieved document is relevant to the user question. Return yes or no.")
    GradeDocuments grade(@dev.langchain4j.service.UserMessage String prompt);
}
