# 五、Day 1：先把 LangChain 跑起来 🚀

## 今日目标

今天不要想太多，就干一件事：

**先成功调用一次模型，再成功跑一个最小 agent。**

## 你今天只看这些概念

- model
- messages
- tools
- `create_agent`
- `invoke`

## 推荐学习顺序

1. 看官方 `Install`
2. 看官方 `Quickstart`
3. 自己敲一个最小脚本

## 你应该写出的第一个脚本

建议先写一个最小聊天调用：

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4.1-mini")

result = llm.invoke("用三句话解释什么是 LangChain")
print(result.content)
```

如果这个都没跑通，不要往下学。

## 第二个脚本：最小 Agent

```python
from langchain.agents import create_agent

def get_weather(city: str) -> str:
    """查询天气"""
    return f"{city}，天气晴，25度。"

agent = create_agent(
    model="openai:gpt-4.1-mini",
    tools=[get_weather],
)

result = agent.invoke(
    {"messages": [{"role": "user", "content": "帮我查一下北京天气"}]}
)

print(result)
```

## 今天必须搞懂的点

### 1. LangChain 现在主线是“围绕 agent 的核心构件”

不是以前那种“到处都是 chain 类”。

### 2. `create_agent` 很重要

它是你后面学习 LangChain 的主入口之一。

### 3. Tool 本质上就是“让模型调用你的 Python 函数”

不要把它神化。

## 今天的验收标准

- 你能解释 `ChatOpenAI().invoke(...)` 和 `create_agent(...).invoke(...)` 的区别
- 你能让模型成功调用一个工具
- 你知道报错优先看哪里：API key、模型名、包没装、环境没激活

------

