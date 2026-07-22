# 一、先说结论：为什么 LangGraph 不适合一上来就学 🧭

这个结论不是我主观拍脑袋说的，官方定位本身就已经很明确：

- LangGraph 是 **low-level orchestration framework**
- 它关注的是 **long-running、stateful agents**
- 如果你刚开始接触 agents，应该先熟悉 models 和 tools
- 如果你只是想快速起步，优先用 LangChain 的高层 agent

所以：

**LangGraph 不适合零基础直接入门，但非常适合在你已经会写最小 Agent 之后继续进阶。**

这也是为什么很多人第一次看 LangGraph 会觉得抽象。

不是你不行，而是它本来就更底层。

------

