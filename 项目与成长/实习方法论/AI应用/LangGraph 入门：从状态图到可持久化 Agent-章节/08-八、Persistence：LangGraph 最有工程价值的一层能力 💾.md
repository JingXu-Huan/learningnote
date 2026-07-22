# 八、Persistence：LangGraph 最有工程价值的一层能力 💾

如果说 LangGraph 哪块最值得工程师重点理解，我会优先说 persistence。

因为这块直接决定：

- 你的 Agent 能不能恢复
- 能不能跨步骤记住上下文
- 能不能支持人工中断后继续执行

官方对 persistence 的定义也很清晰：

- 它让应用在单次 graph run 之外仍能保留信息

这对下面这些场景非常重要：

- 多轮会话连续处理
- 长任务中断恢复
- 故障恢复
- 需要记忆的 Agent

## 先用一个“状态会传递”的例子找感觉

```python
from typing import TypedDict
from langgraph.graph import StateGraph, START, END

class StudyState(TypedDict):
    topic: str
    notes: str
    final_answer: str

def collect_notes(state: StudyState) -> StudyState:
    topic = state["topic"]
    return {
        **state,
        "notes": f"{topic} 的重点：先看核心概念，再整理高频题。",
    }

def generate_answer(state: StudyState) -> StudyState:
    return {
        **state,
        "final_answer": f"学习建议：{state['notes']}",
    }

builder = StateGraph(StudyState)
builder.add_node("collect_notes", collect_notes)
builder.add_node("generate_answer", generate_answer)
builder.add_edge(START, "collect_notes")
builder.add_edge("collect_notes", "generate_answer")
builder.add_edge("generate_answer", END)

graph = builder.compile()
result = graph.invoke(
    {"topic": "LangGraph", "notes": "", "final_answer": ""}
)
print(result)
```

这个例子虽然还没真的持久化，但它能帮助你先看到：

- 一个节点产生的状态，后一个节点可以继续用

------

