package org.bsc.langgraph4j.howtos.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class DocumentIngestor {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestor.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public DocumentIngestor(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public void ingest(List<String> urls) {
        long started = System.nanoTime();
        log.info("准备下载 {} 篇文档", urls.size());
        var docs = urls.stream()
                .map(DocumentIngestor::downloadDocument)
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
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ingestionStarted), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), result.tokenUsage());
        } catch (RuntimeException e) {
            log.error("文档入库失败，耗时=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ingestionStarted) + " ms", e);
            throw e;
        }
    }

    private static @NonNull Document downloadDocument(String url) {
        long downloadStarted = System.nanoTime();
        log.info("开始下载文档: {}", url);
        try {
            var document = UrlDocumentLoader.load(url, new TextDocumentParser());
            log.info("文档下载完成: {}, 字符数={}, 耗时={} ms",
                    url, document.text().length(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - downloadStarted));
            return document;
        } catch (RuntimeException e) {
            log.error("文档下载失败: " + url + ", 耗时=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - downloadStarted) + " ms", e);
            throw e;
        }
    }
}
