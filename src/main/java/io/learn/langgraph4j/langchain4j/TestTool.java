package io.learn.langgraph4j.langchain4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import static java.lang.String.format;

public class TestTool {

    @Tool("tool for test AI agent executor")
    String execTest(@P("test message") String message) {
        return format("test tool ('%s') executed with result 'OK'", message);
    }

    @Tool("return current number of system thread allocated by application")
    int threadCount() {
        return Thread.getAllStackTraces().size();
    }

}
