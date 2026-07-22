# 😎😎😎聊聊 CompletableFuture：异步任务编排与并发执行

> Java 8 引入的 `java.util.concurrent.CompletableFuture`，是对 `Future` 的增强版，支持**链式编排、回调、组合、异常处理**，是异步编程的"瑞士军刀"。

---

> 本文已按二级标题拆分为独立章节，按需进入对应笔记阅读。

## 章节目录

- [[CompletableFuture-章节/01-一、为什么需要 CompletableFuture|一、为什么需要 CompletableFuture]]
- [[CompletableFuture-章节/02-二、核心思想|二、核心思想]]
- [[CompletableFuture-章节/03-三、四种创建方式|三、四种创建方式]]
- [[CompletableFuture-章节/04-四、链式编排：结果转换与消费|四、链式编排：结果转换与消费]]
- [[CompletableFuture-章节/05-五、组合：多任务协同|五、组合：多任务协同]]
- [[CompletableFuture-章节/06-六、异常处理|六、异常处理]]
- [[CompletableFuture-章节/07-七、allOf 与 anyOf|七、allOf 与 anyOf]]
- [[CompletableFuture-章节/08-八、实战案例：电商下单|八、实战案例：电商下单]]
- [[CompletableFuture-章节/09-九、注意事项与坑|九、注意事项与坑]]
- [[CompletableFuture-章节/10-十、最佳实践|十、最佳实践]]
- [[CompletableFuture-章节/11-十一、进阶技巧|十一、进阶技巧]]
- [[CompletableFuture-章节/12-十二、整体 API 关系图|十二、整体 API 关系图]]
- [[CompletableFuture-章节/13-十三、一句话总结|十三、一句话总结]]

## 相关笔记

- [[线程池七大核心参数]] —— 异步任务运行的载体
- [[../README]] —— 多线程总览
- [[技术栈/Java与框架/Java/lambda表达式]] —— CompletableFuture 的回调大量使用 Lambda
- [[技术栈/Java与框架/Java/四种特殊的接口]] —— Supplier / Function / Consumer 接口在 thenApply / thenAccept 中的应用

