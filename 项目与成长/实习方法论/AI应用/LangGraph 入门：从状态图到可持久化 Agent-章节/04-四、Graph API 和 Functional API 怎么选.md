# 四、Graph API 和 Functional API 怎么选

官方给了两套主要思路。

## 1. Graph API

适合：

- 你想显式地定义 graph
- 你想把节点和边看得很清楚
- 你更愿意用声明式方式组织流程

优点：

- 结构直观
- 适合理解编排本质
- 复杂流程更清晰

## 2. Functional API

适合：

- 你想保留普通 Python 控制流
- 你不想把已有代码重构成很显式的图
- 你想以更少改动接入 persistence / streaming / interrupts

官方对 Functional API 的描述很明确：

- 它允许你继续使用 `if`、`for`、函数调用等普通语言结构
- 通过 `@entrypoint` 和 `@task` 接入关键能力

## 3. 新手怎么选

如果你是第一次学 LangGraph，我建议：

- **先学 Graph API**

原因很简单：

- 它更能帮你形成 LangGraph 的心智模型

等你把本质吃透了，再看 Functional API 会轻松很多。

------

