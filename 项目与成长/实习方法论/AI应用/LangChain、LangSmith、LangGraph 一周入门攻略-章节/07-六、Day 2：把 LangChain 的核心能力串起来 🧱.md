# 六、Day 2：把 LangChain 的核心能力串起来 🧱

## 今日目标

今天开始从“能跑”升级到“能做点正经事”。

重点只抓三件事：

1. 结构化输出
2. memory 基本概念
3. RAG 先有感知，不深挖

## 1. 先学结构化输出

这是最值得优先掌握的 LangChain 能力之一。

原因很简单：

- 真实项目里，后端更需要结构化结果，不需要一大段废话
- 结构化输出是你从“玩具 demo”走向“工程化”的第一步

推荐先练：

```python
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI

class UserIntent(BaseModel):
    intent: str = Field(description="用户意图")
    urgency: str = Field(description="紧急程度")
    summary: str = Field(description="一句话总结")

llm = ChatOpenAI(model="gpt-4.1-mini")
structured_llm = llm.with_structured_output(UserIntent)

result = structured_llm.invoke("我明天要面试，想快速复习 Redis 持久化")
print(result)
```

你今天不用纠结所有细节，但要知道：

- 现在官方主线更强调 **schema + structured output**
- 别一上来就迷恋老式 parser

## 2. 理解 short-term memory 和 long-term memory

LangChain 文档里已经把这两件事讲得很明确了：

- **short-term memory**：线程级、对话内、会随着当前会话推进而更新
- **long-term memory**：跨线程、跨会话，长期保存用户偏好、事实、知识

你今天先建立概念，不需要一次做完所有实现。

## 3. RAG 先知道它是什么

这周你不需要把检索系统学成向量数据库专家。

你只需要知道：

- LangChain 可以把“检索到的上下文”喂给模型
- RAG 不是框架本身，RAG 是一种应用模式

如果还有时间，再看官方 `Build a RAG agent with LangChain`。

## 今天的验收标准

- 你能写出一个结构化输出 demo
- 你能说清短期记忆和长期记忆的区别
- 你知道 LangChain 不等于 RAG，RAG 只是 LangChain 常见用法之一

------

