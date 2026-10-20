package org.bsc.langgraph4j.howtos.rag;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.howtos.rag.edge.GradeDocumentsEdge;
import org.bsc.langgraph4j.howtos.rag.node.GenerateAnswerNode;
import org.bsc.langgraph4j.howtos.rag.node.GenerateQueryOrRespondNode;
import org.bsc.langgraph4j.howtos.rag.node.RetrieveNode;
import org.bsc.langgraph4j.howtos.rag.node.RewriteQuestionNode;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;


import java.util.Map;

import java.util.List;

public class CustomRagAgent {
    private static final Logger log = LoggerFactory.getLogger(CustomRagAgent.class);

    public static void main(String[] args) throws GraphStateException {
        log.info("RAG 程序启动，准备加载文档并构建向量索引");
        var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();

        var embeddingModel = QwenEmbeddingModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("text-embedding-v4")
                .build();

        var urls = List.of(
                "https://lilianweng.github.io/posts/2024-11-28-reward-hacking/",
                "https://lilianweng.github.io/posts/2024-07-07-hallucination/",
                "https://lilianweng.github.io/posts/2024-04-12-diffusion-video/"
        );

        new DocumentIngestor(embeddingModel, embeddingStore).ingest(urls);
        log.info("向量索引已就绪，准备构建并运行 RAG 工作流");

        var chatModel = OpenAiChatModel.builder()
                .modelName("MiniMax-M3")
                .baseUrl("https://api.minimax.io/v1")
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .temperature(0.0)
                .build();

        var workflow = buildWorkflow(embeddingStore, embeddingModel, chatModel);

        var graph = workflow.compile();

        UserMessage message = UserMessage.from("What does Lilian Weng say about types of reward hacking?");

        GraphInput input = GraphInput.args(Map.of(
                "messages", message,
                RagState.ORIGINAL_QUESTION, message.singleText(),
                RagState.SEARCH_QUERY, message.singleText()));
        var result = graph.invoke(input, RunnableConfig.empty()).orElseThrow();
        System.out.println(result.answer());
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static NodeAction<RagState> loggedNode(
            String name, NodeAction<RagState> action) {
        return state -> {
            long started = System.nanoTime();
            log.info("[Node:{}] 开始，消息数={}", name, state.messages().size());
            try {
                var updates = action.apply(state);
                log.info("[Node:{}] 完成，耗时={} ms", name, elapsedMillis(started));
                return updates;
            } catch (Exception e) {
                log.error("[Node:{}] 失败，耗时={} ms", name, elapsedMillis(started), e);
                throw e;
            }
        };
    }

    private static EdgeAction<RagState> loggedEdge(
            String name, EdgeAction<RagState> action) {
        return state -> {
            long started = System.nanoTime();
            log.info("[Edge:{}] 开始判断路由", name);
            try {
                var route = action.apply(state);
                log.info("[Edge:{}] 完成，路由={}，耗时={} ms", name, route, elapsedMillis(started));
                return route;
            } catch (Exception e) {
                log.error("[Edge:{}] 失败，耗时={} ms", name, elapsedMillis(started), e);
                throw e;
            }
        };
    }

    private static StateGraph<RagState> buildWorkflow(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ChatModel chatModel) throws GraphStateException {

        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .build();

        var toolService = LC4jToolService.builder()
                .toolsFromObject(new BlogTools(retriever))
                .build();

        NodeAction<RagState> generateQueryOrRespond =
                loggedNode("generate_query_or_respond", new GenerateQueryOrRespondNode(toolService, chatModel));

        NodeAction<RagState> retrieve =
                loggedNode("retrieve", new RetrieveNode(toolService));

        EdgeAction<RagState> routeOnToolCalls = loggedEdge("route_on_tool_calls", state ->
                state.lastMessage()
                        .filter(AiMessage.class::isInstance)
                        .map(AiMessage.class::cast)
                        .filter(AiMessage::hasToolExecutionRequests)
                        .isPresent() ? "retrieve" : "respond");

        EdgeAction<RagState> gradeDocuments =
                loggedEdge("grade_documents", new GradeDocumentsEdge(chatModel));

        NodeAction<RagState> rewriteQuestion =
                loggedNode("rewrite_question", new RewriteQuestionNode(chatModel));

        NodeAction<RagState> generateAnswer =
                loggedNode("generate_answer", new GenerateAnswerNode(chatModel));

        var stateSerializer = new LC4jStateSerializer<>(RagState::new);

        return new StateGraph<>(RagState.SCHEMA, stateSerializer)
                .addNode("generate_query_or_respond", node_async(generateQueryOrRespond))
                .addNode("retrieve", node_async(retrieve))
                .addNode("rewrite_question", node_async(rewriteQuestion))
                .addNode("generate_answer", node_async(generateAnswer))
                .addEdge(START, "generate_query_or_respond")
                .addConditionalEdges("generate_query_or_respond", edge_async(routeOnToolCalls), Map.of(
                        "retrieve", "retrieve",
                        "respond", END
                ))
                .addConditionalEdges("retrieve", edge_async(gradeDocuments), Map.of(
                        "generate_answer", "generate_answer",
                        "rewrite_question", "rewrite_question"
                ))
                .addEdge("rewrite_question", "generate_query_or_respond")
                .addEdge("generate_answer", END);
    }
}
