# 八、Memory 先建立概念，不要一上来就死磕实现 🧵

LangChain 里 memory 相关内容容易让新手一头雾水。

先别急着实现，先理解概念。

## 1. Short-term memory

官方文档讲得很清楚：

- 它是线程级、会话内的记忆
- 会随着当前对话推进而更新
- 常见就是 conversation history

## 2. Long-term memory

- 跨线程
- 跨会话
- 可以长期保存用户偏好、事实、知识

## 新手先记一句话

- **短期记忆**：当前对话别忘
- **长期记忆**：下次再来还记得

你先能把这两个概念分清，就已经比很多只会调 API 的人强了。

## 先做一个最小“多轮消息”实验

虽然这里不展开完整 memory 实现，但你至少要体会“消息历史会影响回答”。

```python
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage

llm = ChatOpenAI(model="gpt-4.1-mini", temperature=0)

messages = [
    SystemMessage(content="你是一个 Java 学习助手，回答尽量短。"),
    HumanMessage(content="我正在准备 Redis 面试。"),
]

first = llm.invoke(messages)
print("第一轮：", first.content)

messages.append(AIMessage(content=first.content))
messages.append(HumanMessage(content="继续刚才的话题，再给我 5 个高频问题。"))

second = llm.invoke(messages)
print("第二轮：", second.content)
```

这个练习的目标不是实现 memory，而是先建立感受：

- 多轮上下文确实会影响回答
- 只传最后一句，和带着历史消息传，结果会不同

------

