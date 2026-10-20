package org.bsc.langgraph4j.howtos.rag;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.Command;
import org.jspecify.annotations.NonNull;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;


import java.util.Map;
import java.util.stream.Collectors;

import java.util.List;

public class CustomRagAgent {
    public static void main(String[] args) throws GraphStateException {
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

        ingestDocs(urls, embeddingModel, embeddingStore);

        var chatModel = OpenAiChatModel.builder()
                .modelName("MiniMax-M3")
                .baseUrl("https://api.minimax.io/v1")
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        var workflow = buildWorkflow(embeddingStore, embeddingModel, chatModel);

        var graph = workflow.compile();

        UserMessage message = UserMessage.from("What does Lilian Weng say about types of reward hacking?");

        for (var step : graph.stream(GraphInput.args(Map.of("messages", message)), RunnableConfig.empty())) {
            step.state().lastMessage()
                    .filter(AiMessage.class::isInstance)
                    .map(AiMessage.class::cast)
                    .map(AiMessage::text)
                    .ifPresent(System.out::println);
        }
    }

    private static void ingestDocs(List<String> urls,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore) {
        var docs = urls.stream()
                .map(url -> UrlDocumentLoader.load(url, new TextDocumentParser()))
                .toList();

        var textSplitter = DocumentSplitters.recursive(100, 50);

        var ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(textSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(docs);
    }

    private static StateGraph<MessagesState<ChatMessage>> buildWorkflow(
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

        NodeAction<MessagesState<ChatMessage>> generateQueryOrRespond = state -> {
            var params = ChatRequestParameters.builder()
                    .toolSpecifications(toolService.toolSpecifications())
                    .build();
            var request = ChatRequest.builder()
                    .parameters(params)
                    .messages(state.messages())
                    .build();
            var response = chatModel.chat(request);
            return Map.of("messages", response.aiMessage());
        };

        var documentGrader = AiServices.create(DocumentGrader.class, chatModel);

        EdgeAction<MessagesState<ChatMessage>> gradeDocuments = state -> {
            var question = findFirstUserMessage(state.messages());
            var context = state.lastMessage()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .map(ToolExecutionResultMessage::text)
                    .orElse("");
            String prompt = "Retrieved document:\n" + context + "\n\nUser question: " + question;
            var score = documentGrader.grade(prompt);
            return "yes".equalsIgnoreCase(score.binaryScore) ? "generate_answer" : "rewrite_question";
        };

        NodeAction<MessagesState<ChatMessage>> rewriteQuestion = state -> {
            var question = findFirstUserMessage(state.messages());
            var response = chatModel.chat(UserMessage.from("Formulate an improved search question: " + question));
            return Map.of("messages", UserMessage.from(response.aiMessage().text()));
        };

        NodeAction<MessagesState<ChatMessage>> generateAnswer = state -> {
            var question = findFirstUserMessage(state.messages());
            var context = findAndJoinToolExecutionResultMessages(state.messages());
            var prompt = "Question: %s\n<context>\n%s\n</context>".formatted(question, context);
            var response = chatModel.chat(UserMessage.from(prompt));
            return Map.of("messages", response.aiMessage());
        };

        EdgeAction<MessagesState<ChatMessage>> routeOnToolCalls = state -> {
            var last = state.lastMessage().orElse(null);
            if (last instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                return "retrieve";
            }
            return "respond";
        };

        NodeAction<MessagesState<ChatMessage>> retrieve = state -> {
            var last = state.lastMessage().orElseThrow();
            if (last instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                return toolService.execute(
                                aiMessage.toolExecutionRequests(),
                                InvocationContext.builder()
                                        .invocationParameters(InvocationParameters.from(state.data()))
                                        .build(),
                                "messages")
                        .thenApply(Command::update)
                        .join();
            }
            return Map.of();
        };

        var stateSerializer = new LC4jStateSerializer<MessagesState<ChatMessage>>(MessagesState::new);

        return new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
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

    private static @NonNull String findAndJoinToolExecutionResultMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .map(ToolExecutionResultMessage::text)
                .collect(Collectors.joining("\n\n"));
    }

    private static @NonNull String findFirstUserMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .findFirst()
                .map(UserMessage::singleText)
                .orElse("");
    }
}

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

class GradeDocuments {
    @Description("Relevance score: 'yes' if relevant, or 'no' if not relevant")
    public String binaryScore;
}

interface DocumentGrader {
    @SystemMessage("Grade whether the retrieved document is relevant to the user question. Return yes or no.")
    GradeDocuments grade(@dev.langchain4j.service.UserMessage String prompt);
}

