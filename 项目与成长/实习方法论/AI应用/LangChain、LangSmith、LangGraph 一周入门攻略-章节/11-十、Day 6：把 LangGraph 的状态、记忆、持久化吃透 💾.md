# 十、Day 6：把 LangGraph 的状态、记忆、持久化吃透 💾

## 今日目标

今天学 LangGraph 最重要的工程能力：

- persistence
- checkpointer
- store

这是 LangGraph 和“普通 while 循环 agent”差距最大的地方之一。

## 1. 先分清两类持久化

官方文档把它拆得很清楚：

- **Checkpointer**：保存 thread 级 graph state，适合短期记忆、会话恢复、human-in-the-loop、time travel、fault tolerance
- **Store**：保存 graph state 之外的应用数据，适合长期记忆、用户偏好、共享知识

这里你会发现一个很重要的呼应：

- LangChain 的 long-term memory，底层就建立在 **LangGraph stores** 上

也就是说：

**LangChain 的一些高级能力，底层很多是站在 LangGraph runtime 上的。**

## 2. 今天建议做的 demo

做一个“学习助手”：

- 第一轮：用户说“我正在准备 Java 面试”
- 第二轮：用户说“继续刚才的话题，给我 5 个 Redis 高频题”

你要做到：

- 同一个 thread 下能续接上下文
- 程序中断后还能恢复

## 3. 顺手看一下本地 server

如果你精力够，可以走一遍官方 `Run a local server`：

- 安装 `langgraph-cli[inmem]`
- `langgraph new ...`
- `langgraph dev`
- 用 Studio 连本地服务

这一步能帮你把“写 graph”升级成“把 graph 当成服务运行”。

## 今天的验收标准

- 你能说出 checkpointer 和 store 的区别
- 你能解释为什么 LangGraph 适合长流程 agent
- 你能跑一个最小持久化 demo

------

