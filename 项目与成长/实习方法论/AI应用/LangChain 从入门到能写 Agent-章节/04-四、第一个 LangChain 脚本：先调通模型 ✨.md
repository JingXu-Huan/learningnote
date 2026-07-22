# 四、第一个 LangChain 脚本：先调通模型 ✨

今天最重要的目标很简单：

**先完成一次最小模型调用。**

代码如下：

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4.1-mini")

result = llm.invoke("用三句话解释什么是 LangChain")
print(result.content)
```

这段代码背后，你只需要先理解两件事：

## 1. `ChatOpenAI` 是模型适配器

它负责把你写的 Python 代码，转成 provider 能理解的请求。

## 2. `invoke(...)` 是最基础的调用方式

你传进去一个输入，拿回来一个结果。

如果这一步报错，优先排查：

- API key 有没有配
- 模型名有没有写错
- 包有没有装对
- 虚拟环境有没有激活

## 练手任务 🎯

先不要急着进下一节，先把下面 3 个变体都敲一遍：

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4.1-mini", temperature=0)

questions = [
    "用一句话解释什么是 LangChain",
    "用三点说明 LangChain 和直接调 OpenAI API 的区别",
    "假设我是后端实习生，为什么要学 Agent 框架",
]

for q in questions:
    result = llm.invoke(q)
    print("=" * 40)
    print("问题：", q)
    print("回答：", result.content)
```

观察点：

- 同一个模型，换不同问题，输出风格有什么变化
- `temperature=0` 后，多次执行是否更稳定
- `result` 除了 `content` 之外，还有没有别的信息

------

