# learn-langgraph4j

用于学习 LangGraph4j 的 Maven 工程。

- Group ID：`io.learn.langgraph4j`
- Artifact ID：`learn-langgraph4j`
- Java：17 或以上版本
- 核心依赖：`org.bsc.langgraph4j:langgraph4j-core:1.9.3`

## 构建

```bash
mvn clean package
```

## 运行入口

```bash
mvn dependency:copy-dependencies -DincludeScope=runtime
java -cp 'target/classes:target/dependency/*' io.learn.langgraph4j.Main
```

## 日志

使用 SLF4J 2.0.20 和 Logback Classic 1.6.5，配置位于 `src/main/resources/logback.xml`。
默认以 UTF-8 输出 INFO 及以上级别日志到控制台，包含时间、线程、级别、Logger 名称和消息。

可通过环境变量或 JVM 系统属性设置 `LOG_LEVEL`（根日志级别）和 `APP_LOG_LEVEL`（项目包日志级别）：

```bash
java -DAPP_LOG_LEVEL=DEBUG -cp 'target/classes:target/dependency/*' io.learn.langgraph4j.Main
```

## 文档

- [LangGraph4j 官方仓库](https://github.com/langgraph4j/langgraph4j)
