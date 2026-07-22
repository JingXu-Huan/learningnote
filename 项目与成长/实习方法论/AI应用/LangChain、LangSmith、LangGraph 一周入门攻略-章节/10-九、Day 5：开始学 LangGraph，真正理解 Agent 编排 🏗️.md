# 九、Day 5：开始学 LangGraph，真正理解 Agent 编排 🏗️

## 今日目标

今天才开始碰 LangGraph，顺序不要倒。

因为官方自己就说得很直：

- LangGraph 是 **low-level**
- 它关注的是 **agent orchestration**
- 如果你刚开始接触 agent，应该先熟悉 models 和 tools，甚至先从 LangChain agents 入手

这正是我们把它放到第 5 天的原因。

## 今天只学三个词

- **State**
- **Node**
- **Edge**

官方 Graph API 概述里给得很清楚：

1. `State`：当前应用快照
2. `Node`：干活的函数
3. `Edge`：决定下一步走向的函数

一句话记忆：

**node 干活，edge 决定往哪走，state 负责把过程中的信息串起来。**

## 今天推荐的第一个图

不要一上来就多 Agent。

先做最小图：

1. 读取用户问题
2. 判断是否需要工具
3. 调工具或直接回答
4. 输出结果

## 你今天要理解的本质

LangGraph 不是只是把代码画成流程图。

它的价值在于：

- 可以循环
- 可以中断再恢复
- 可以把状态持久化
- 可以做 human-in-the-loop
- 可以更细粒度控制 agent 的执行

## Graph API 还是 Functional API

官方给出的判断也很实用：

- 想显式地定义图结构，用 **Graph API**
- 想保留普通 Python 控制流、少改已有代码，用 **Functional API**

如果你是新手，我建议：

- **先学 Graph API**
- 学会后再看 Functional API

因为 Graph API 更容易把 agent 编排的本质看清楚。

## 今天的验收标准

- 你能解释 State / Node / Edge 各是什么
- 你能说出 LangGraph 和 LangChain agent 的层次区别
- 你能跑一个最小 graph demo

------

