# 七、结构化输出：LangChain 最值得优先掌握的能力之一 📦

如果说新手学 LangChain 最容易低估哪块，那一定是结构化输出。

因为很多人一开始只会：

- 让模型返回一大段自然语言

但真实项目里，后端更常需要的是：

- 分类结果
- 意图识别结果
- 抽取字段
- JSON 风格对象

## 一个最小例子

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

## 为什么这件事重要

因为它让你的输出从：

- “看起来差不多对”

变成：

- “字段清晰、类型稳定、后端能接”

## 新手要建立的判断

现在的主线不是“疯狂雕 prompt，让模型吐 JSON”，而是：

- 先定 schema
- 再用 structured output
- prompt 只是补充约束

## 练手升级：做一个“学习任务拆解器” 🧠

这个练习比单纯分类更像真实场景。

```python
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI

class StudyPlan(BaseModel):
    topic: str = Field(description="学习主题")
    current_level: str = Field(description="当前水平")
    goals: list[str] = Field(description="学习目标列表")
    first_action: str = Field(description="第一步该做什么")
    need_code_practice: bool = Field(description="是否需要代码练习")

llm = ChatOpenAI(model="gpt-4.1-mini", temperature=0)
structured_llm = llm.with_structured_output(StudyPlan)

prompt = """
用户现在有一些 Python 基础。
他希望在一周内学完 LangChain，LangSmith，LangGraph。
请帮他拆成一个清晰的学习任务。
"""

result = structured_llm.invoke(prompt)
print(result)
print(result.model_dump())
```

继续练：

- 把 `goals` 改成 `list[str]` 之外，再增加 `day_by_day_plan`
- 增加 `risk_points`
- 试试把输入换成 “我要准备 Redis 面试” 或 “我要做一个 RAG demo”

------

