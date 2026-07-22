# 二、你应该怎么学 LangChain

## 1. 按新版文档学，不要被老教程带偏

现在很多旧教程还在讲：

- `LLMChain`
- 老式 `AgentExecutor`
- 一堆旧 prompt chain 玩法

但 LangChain v1 的方向已经很明确：

- `langchain` 命名空间被精简
- 主打 **essential building blocks for agents**
- 很多旧东西迁到了 `langchain-classic`

所以你学习时优先看：

- `docs.langchain.com/oss/python/langchain/...`

而不是只盯着很老的文章。

## 2. 新手最合理的顺序

不要一开始就啃所有高级概念。

正确顺序应该是：

1. 先调通模型
2. 再学 tool calling
3. 再学结构化输出
4. 再理解 memory
5. 最后再碰更复杂的 agent 形态

------

