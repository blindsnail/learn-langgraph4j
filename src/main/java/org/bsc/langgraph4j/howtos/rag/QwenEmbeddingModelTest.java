package org.bsc.langgraph4j.howtos.rag;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;

import java.util.Arrays;

public class QwenEmbeddingModelTest {
    public static void main(String[] args) {
        var embeddingModel = QwenEmbeddingModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("text-embedding-v4")
                .build();

        Response<Embedding> response = embeddingModel.embed("Hello, world!");
        System.out.println(Arrays.toString(response.content().vector()));
        System.out.println(response.content().dimension());
    }
}
