# 七、Day 3：接入 LangSmith，看见程序内部发生了什么 🔭

## 今日目标

今天开始学会一句非常重要的话：

**LLM 应用不能只看最终答案，要看中间链路。**

LangSmith 的第一价值不是“高级监控平台”，而是：

**让你终于能看见 agent 每一步干了什么。**

## 先理解 LangSmith 里最关键的几个词

官方对数据结构的定义可以直接记成下面这样：

- **Project**：一个应用或服务的 trace 容器
- **Trace**：一次完整操作的执行链路
- **Run**：链路里某一个具体步骤
- **Thread**：多轮对话下，多条 trace 组成的一条会话线

这是你后面读 UI、读日志、读评测结果的基础词汇表。

## 今天怎么做

### 1. 开 tracing

```powershell
$env:LANGSMITH_API_KEY="你的key"
$env:LANGSMITH_TRACING="true"
```

### 2. 跑昨天的 LangChain 脚本

如果你用的是 LangChain，官方说明它支持自动 tracing，不需要你手搓很多埋点。

### 3. 打开 LangSmith UI 看 trace

重点不是“页面按钮怎么点”，而是你要学会看：

- 输入是什么
- 中间有没有调用 tool
- tool 入参对不对
- 最终输出是不是偏题
- token 消耗和耗时大概怎样

## 今天必须观察的三个问题

1. 模型到底有没有按你的预期调用工具
2. Prompt 是不是把模型带偏了
3. 明明结果错了，到底错在模型、工具还是你自己的代码

## 如果你不是 LangChain 应用怎么办

LangSmith 也支持手动埋点。官方给了三种常见方式：

- `@traceable`
- `trace` 上下文
- `RunTree` API

但你这周先别深入，先把自动 tracing 用熟。

## 今天的验收标准

- 你能在 LangSmith 里找到自己程序的一条 trace
- 你能点开其中一个 run 看输入输出
- 你能说清一个 bug 大概卡在哪一层

------

