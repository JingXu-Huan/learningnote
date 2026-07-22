# 五、第二个脚本：写一个最小 Agent 🤖

模型能调通后，下一步就不是“继续聊天”，而是“让模型能调你的函数”。

这就是 Agent 入门的关键一步。

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

## 这段代码你要重点看什么

### 1. `create_agent`

这是 LangChain v1 里非常重要的入口。

它的思路不是“给你一堆链类让你拼”，而是围绕 agent 来组织能力。

### 2. `tools=[get_weather]`

这是把你的 Python 函数暴露给模型。

### 3. `messages`

Agent 调用不再只是“传个字符串”，而是围绕消息来组织上下文。

## 升级版练手：做 2 个工具 🧩

只写一个天气函数还不够，你至少要体会一下模型在多个工具之间做选择。

```python
from langchain.agents import create_agent

def get_weather(city: str) -> str:
    """查询某个城市的天气"""
    return f"{city}：晴天，28 度，适合出门。"

def get_interview_tip(topic: str) -> str:
    """根据主题返回一条面试复习建议"""
    tips = {
        "redis": "先复习数据类型、持久化、缓存穿透、缓存雪崩。",
        "mysql": "先复习索引、事务、锁、MVCC、执行计划。",
        "spring": "先复习 IOC、AOP、事务传播、自定义注解。",
    }
    return tips.get(topic.lower(), f"{topic}：建议先整理核心概念，再准备 5 个高频面试题。")

agent = create_agent(
    model="openai:gpt-4.1-mini",
    tools=[get_weather, get_interview_tip],
)

questions = [
    "帮我查一下郑州天气",
    "我明天面试 Redis，给我一个复习建议",
    "我准备 Spring 面试，给我一个简短建议",
]

for question in questions:
    result = agent.invoke(
        {"messages": [{"role": "user", "content": question}]}
    )
    print("=" * 60)
    print(question)
    print(result)
```

练手要求：

- 把 `get_interview_tip` 改成你自己的方向，比如 Java、并发、MQ
- 再加一个 `get_note_summary(topic: str)` 工具
- 故意把某个工具说明写得很含糊，看模型会不会选错

------

