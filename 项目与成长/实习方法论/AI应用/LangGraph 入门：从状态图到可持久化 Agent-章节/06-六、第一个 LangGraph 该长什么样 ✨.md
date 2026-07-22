# 六、第一个 LangGraph 该长什么样 ✨

新手最容易犯的错之一，就是一上来就想做多 Agent。

别这样。

你第一个 LangGraph 应该尽量简单。

建议做这种结构：

1. 接收用户问题
2. 判断是否需要工具
3. 需要则调工具，不需要则直接回答
4. 汇总结果输出

这个流程已经足够帮你理解：

- state 怎么流转
- node 怎么拆
- edge 怎么判断

如果你连这个最小图都没写过，直接上复杂 agent，只会把自己绕晕。

## 一个最小可运行骨架 🧪

下面这个例子不追求花哨，只是帮你先体会 graph 的基本组成。

```python
from typing import TypedDict
from langgraph.graph import StateGraph, START, END

class MyState(TypedDict):
    question: str
    answer: str

def answer_node(state: MyState) -> MyState:
    question = state["question"]
    return {
        **state,
        "answer": f"你问的是：{question}。这是一个最小 LangGraph 返回。",
    }

graph_builder = StateGraph(MyState)
graph_builder.add_node("answer_node", answer_node)
graph_builder.add_edge(START, "answer_node")
graph_builder.add_edge("answer_node", END)

graph = graph_builder.compile()

result = graph.invoke({"question": "什么是 LangGraph？", "answer": ""})
print(result)
```

这个最小例子的目的只有一个：

- 让你真正看到 `State + Node + Edge` 怎么拼起来

------

