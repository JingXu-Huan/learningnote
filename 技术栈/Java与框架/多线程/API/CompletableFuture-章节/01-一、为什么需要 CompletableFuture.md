# 一、为什么需要 CompletableFuture

## 1.1 传统 `Future` 的痛点

`Future` 是 Java 5 引入的异步结果占位符，但**只能阻塞 `get()` 取结果**，无法做到：

- 任务完成后**自动通知**我
- 多个异步任务**编排组合**（先后、并行、汇聚）
- 任务执行过程中**捕获异常**并处理
- 像写同步代码一样**链式调用**

## 1.2 CompletableFuture 解决了什么

| 能力 | 说明 |
|------|------|
| 主动完成 | `complete()` / `completeExceptionally()` 手动结束 |
| 回调通知 | `thenApply` / `thenAccept` / `thenRun` |
| 链式编排 | `thenCompose` 串行依赖 |
| 任务组合 | `thenCombine` 双源汇聚 |
| 多任务协同 | `allOf` 全完成 / `anyOf` 任一完成 |
| 异常处理 | `exceptionally` / `handle` / `whenComplete` |

## 1.3 适用场景

- 微服务间**并行调用**多个下游接口（聚合数据）
- 耗时操作**异步化**（发短信、写日志、查缓存）
- 复杂的**任务编排 DAG**（有向无环图）
- **流水线式**数据处理

---

