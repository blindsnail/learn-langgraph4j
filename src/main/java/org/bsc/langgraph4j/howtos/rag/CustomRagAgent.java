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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;


import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

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

        ingestDocs(urls, embeddingModel, embeddingStore);
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
        long started = System.nanoTime();
        log.info("准备下载 {} 篇文档", urls.size());
        var docs = urls.stream()
                .map(url -> {
                    long downloadStarted = System.nanoTime();
                    log.info("开始下载文档: {}", url);
                    try {
                        var document = UrlDocumentLoader.load(url, new TextDocumentParser());
                        log.info("文档下载完成: {}, 字符数={}, 耗时={} ms",
                                url, document.text().length(), elapsedMillis(downloadStarted));
                        return document;
                    } catch (RuntimeException e) {
                        log.error("文档下载失败: " + url + ", 耗时=" + elapsedMillis(downloadStarted) + " ms", e);
                        throw e;
                    }
                })
                .toList();

        var textSplitter = DocumentSplitters.recursive(1000, 150);

        var ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(textSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        long ingestionStarted = System.nanoTime();
        log.info("文档下载完毕，开始切分、向量化并写入存储");
        try {
            var result = ingestor.ingest(docs);
            log.info("文档入库完成，入库耗时={} ms，总耗时={} ms，tokenUsage={}",
                    elapsedMillis(ingestionStarted), elapsedMillis(started), result.tokenUsage());
        } catch (RuntimeException e) {
            log.error("文档入库失败，耗时=" + elapsedMillis(ingestionStarted) + " ms", e);
            throw e;
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static NodeAction<MessagesState<ChatMessage>> loggedNode(
            String name, NodeAction<MessagesState<ChatMessage>> action) {
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

    private static EdgeAction<MessagesState<ChatMessage>> loggedEdge(
            String name, EdgeAction<MessagesState<ChatMessage>> action) {
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

        NodeAction<MessagesState<ChatMessage>> queryAndRetrieve = loggedNode("query_and_retrieve", state -> {
            var params = ChatRequestParameters.builder()
                    .toolSpecifications(toolService.toolSpecifications())
                    .build();
            var request = ChatRequest.builder()
                    .parameters(params)
                    .messages(state.messages())
                    .build();
            long modelStarted = System.nanoTime();
            log.info("[Node:query_and_retrieve] 开始调用模型，判断是否需要检索");
            var aiMessage = chatModel.chat(request).aiMessage();
            log.info("[Node:query_and_retrieve] 模型调用完成，耗时={} ms", elapsedMillis(modelStarted));
            if (!aiMessage.hasToolExecutionRequests()) {
                log.info("[Node:query_and_retrieve] 模型直接回答，无需执行检索工具");
                return Map.of("messages", aiMessage);
            }

            // Preserve the same conversation context that the separate retrieve node received.
            var messages = new ArrayList<>(state.messages());
            messages.add(aiMessage);
            var invocationData = new HashMap<>(state.data());
            invocationData.put("messages", messages);

            long toolsStarted = System.nanoTime();
            log.info("[Node:query_and_retrieve] 开始执行工具，数量={}，工具={}",
                    aiMessage.toolExecutionRequests().size(),
                    aiMessage.toolExecutionRequests().stream().map(tool -> tool.name()).toList());
            var updates = new HashMap<>(toolService.execute(
                                aiMessage.toolExecutionRequests(),
                                InvocationContext.builder()
                                        .invocationParameters(InvocationParameters.from(invocationData))
                                        .build(),
                                "messages")
                        .thenApply(Command::update)
                        .join());
            log.info("[Node:query_and_retrieve] 工具执行完成，耗时={} ms", elapsedMillis(toolsStarted));

            // Return only new messages; MessagesState appends them to the existing history.
            var newMessages = new ArrayList<ChatMessage>();
            newMessages.add(aiMessage);
            for (var result : (List<?>) updates.get("messages")) {
                newMessages.add((ChatMessage) result);
            }
            updates.put("messages", newMessages);
            return updates;
        });

        var documentGrader = AiServices.create(DocumentGrader.class, chatModel);

        EdgeAction<MessagesState<ChatMessage>> gradeDocuments = loggedEdge("grade_documents", state -> {
            if (state.lastMessage().filter(AiMessage.class::isInstance).isPresent()) {
                log.info("[Edge:grade_documents] 已有模型直接回答，跳过文档评分，结束工作流");
                return "respond";
            }
            var question = findFirstUserMessage(state.messages());
            var context = state.lastMessage()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .map(ToolExecutionResultMessage::text)
                    .orElse("");
            String prompt = "Retrieved document:\n" + context + "\n\nUser question: " + question;
            log.info("[Edge:grade_documents] 开始调用模型评估相关性，问题字符数={}，上下文字符数={}",
                    question.length(), context.length());
            var score = documentGrader.grade(prompt);
            log.info("[Edge:grade_documents] 文档相关性评分={}", score.binaryScore);
            return "yes".equalsIgnoreCase(score.binaryScore) ? "generate_answer" : "rewrite_question";
        });

        NodeAction<MessagesState<ChatMessage>> rewriteQuestion = loggedNode("rewrite_question", state -> {
            var question = findFirstUserMessage(state.messages());
            log.info("[Node:rewrite_question] 开始调用模型改写问题，原问题字符数={}", question.length());
            var response = chatModel.chat(UserMessage.from("Formulate an improved search question: " + question));
            log.info("[Node:rewrite_question] 问题改写完成，将重新进入 query_and_retrieve");
            return Map.of("messages", UserMessage.from(response.aiMessage().text()));
        });

        NodeAction<MessagesState<ChatMessage>> generateAnswer = loggedNode("generate_answer", state -> {
            var question = findFirstUserMessage(state.messages());
            var context = findAndJoinToolExecutionResultMessages(state.messages());
            var prompt = "Question: %s\n<context>\n%s\n</context>".formatted(question, context);
            log.info("[Node:generate_answer] 开始调用模型生成答案，问题字符数={}，上下文字符数={}",
                    question.length(), context.length());
            var response = chatModel.chat(UserMessage.from(prompt));
            return Map.of("messages", response.aiMessage());
        });

        var stateSerializer = new LC4jStateSerializer<MessagesState<ChatMessage>>(MessagesState::new);

        return new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                .addNode("query_and_retrieve", node_async(queryAndRetrieve))
                .addNode("rewrite_question", node_async(rewriteQuestion))
                .addNode("generate_answer", node_async(generateAnswer))
                .addEdge(START, "query_and_retrieve")
                .addConditionalEdges("query_and_retrieve", edge_async(gradeDocuments), Map.of(
                        "respond", END,
                        "generate_answer", "generate_answer",
                        "rewrite_question", "rewrite_question"
                ))
                .addEdge("rewrite_question", "query_and_retrieve")
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

