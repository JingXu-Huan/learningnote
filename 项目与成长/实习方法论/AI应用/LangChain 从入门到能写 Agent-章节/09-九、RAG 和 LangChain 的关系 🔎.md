# 九、RAG 和 LangChain 的关系 🔎

很多人会误以为：

- LangChain = RAG

这是错的。

更准确地说：

- **RAG 是一种应用模式**
- **LangChain 是帮助你实现这种模式的框架之一**

RAG 的核心思想是：

1. 先检索外部信息
2. 再把检索结果喂给模型
3. 模型基于这些上下文回答

LangChain 可以帮你组织这条链路，但它本身不等于 RAG。

## 最小练手：假装做一个本地知识检索工具

你现在不用急着接向量数据库，先用一个假的检索函数把流程跑通。

```python
from langchain.agents import create_agent

NOTES = {
    "redis": "Redis 常考：数据类型、持久化、主从复制、哨兵、分片集群。",
    "mysql": "MySQL 常考：索引、事务、隔离级别、锁、MVCC、执行计划。",
    "mq": "消息队列常考：削峰填谷、异步解耦、重复消费、顺序消息。",
}

def search_notes(topic: str) -> str:
    """根据主题搜索学习笔记"""
    return NOTES.get(topic.lower(), f"没有找到 {topic} 相关笔记。")

agent = create_agent(
    model="openai:gpt-4.1-mini",
    tools=[search_notes],
)

result = agent.invoke(
    {"messages": [{"role": "user", "content": "帮我总结一下 Redis 面试最该先看什么"}]}
)

print(result)
```

你要理解的不是这个例子有多高级，而是：

- “先检索，再回答” 这条链路已经出现了
- 以后把假检索换成真检索，就是更完整的 RAG

------

