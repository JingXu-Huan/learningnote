# LangSmith 调试、观测与评测入门 😎😎😎

> 适合人群：已经能跑一个最小 LLM / LangChain 应用，但调试还靠猜的人
> 
> 文档基线：基于官方文档整理，时间点为 `2026-07-09`

------

## 📑 目录

- [一、先说结论：为什么 LangSmith 很值得学](#一先说结论为什么-langsmith-很值得学)
- [二、LangSmith 到底是干嘛的](#二langsmith-到底是干嘛的)
- [三、最关键的四个词：Project、Trace、Run、Thread](#三最关键的四个词projecttracerunthread)
- [四、第一步：先把 Tracing 打开](#四第一步先把-tracing-打开)
- [五、看 Trace 时到底应该看什么](#五看-trace-时到底应该看什么)
- [六、如果不用 LangChain，还能怎么接 LangSmith](#六如果不用-langchain还能怎么接-langsmith)
- [七、评测为什么重要](#七评测为什么重要)
- [八、LangSmith 评测三件套](#八langsmith-评测三件套)
- [九、做一个最小 Evaluation 示例](#九做一个最小-evaluation-示例)
- [十、Dataset 为什么要版本化](#十dataset-为什么要版本化)
- [十一、Prompt Engineering 在 LangSmith 里怎么理解](#十一prompt-engineering-在-langsmith-里怎么理解)
- [十二、LangSmith 的关键技术细节](#十二langsmith-的关键技术细节)
- [十三、LangSmith 的最佳实践](#十三langsmith-的最佳实践)
- [十四、练手项目 1：给 LangChain Agent 接上 Tracing](#十四练手项目-1给-langchain-agent-接上-tracing)
- [十五、练手项目 2：跑一个最小评测实验](#十五练手项目-2跑一个最小评测实验)
- [十六、最容易踩的坑](#十六最容易踩的坑)
- [十七、学完这篇你应该掌握什么](#十七学完这篇你应该掌握什么)

------

## 一、先说结论：为什么 LangSmith 很值得学

很多新手会觉得：

- 先把应用写出来再说
- 观测、评测以后再补

这个思路在普通 CRUD 项目里有时还能凑合，但在 LLM 应用里很容易翻车。

因为 LLM 应用最大的特点之一就是：

- **中间过程不透明**
- **输出不稳定**

没有 LangSmith 这类工具，你调试 agent 经常就是：

- 看最终回答
- 猜中间发生了什么
- 再改 prompt
- 再猜一次

这基本等于盲飞。

所以先说结论：

**LangSmith 的第一价值，是让你终于能看清一条请求中间到底发生了什么。**

------

## 二、LangSmith 到底是干嘛的

按官方定位，LangSmith 主要覆盖这些方向：

- tracing / observability
- debug
- evaluation
- prompt engineering
- studio / test

把它翻成更接地气的话：

- 记录执行链路
- 调 bug
- 跑评测
- 管 Prompt
- 看实验结果

你可以把它理解成：

```mermaid
flowchart LR
    A["你的 LLM 应用"] --> B["LangSmith"]
    B --> C["Trace"]
    B --> D["Debug"]
    B --> E["Evaluation"]
    B --> F["Prompt Iteration"]
```

------

## 三、最关键的四个词：Project、Trace、Run、Thread

这一块一定要背熟，不然后面看 LangSmith UI 会很懵。

### 1. Project

一个应用或服务的 traces 容器。

比如：

- 你的“学习助手”
- 你的“RAG 问答系统”
- 你的“工单分类 Agent”

都可以分别对应不同 project。

### 2. Trace

一次完整操作的执行链路。

比如用户发来一句：

- “帮我总结这段 Java 并发笔记”

这一次请求里涉及的模型调用、工具调用、解析步骤等，可以组成一条 trace。

### 3. Run

Run 是 trace 里的一个具体步骤。

它可以是：

- 一次 LLM 调用
- 一次 retrieval
- 一次 prompt formatting
- 一次工具调用

可以把 run 理解成链路里的一个 span。

### 4. Thread

Thread 是多轮对话下，多条 trace 组成的一条会话线。

也就是：

- 一轮对话可能是一条 trace
- 多轮连续对话可以组成一个 thread

------

## 四、第一步：先把 Tracing 打开

如果你已经在用 LangChain，官方文档说明支持自动 tracing，这对新手非常友好。

### 最基础的环境变量

```powershell
$env:LANGSMITH_API_KEY="你的key"
$env:LANGSMITH_TRACING="true"
```

如果还要跑 OpenAI：

```powershell
$env:OPENAI_API_KEY="你的key"
```

### 最简单的做法

1. 配好环境变量
2. 跑你的 LangChain 脚本
3. 打开 LangSmith UI
4. 找到那条 trace

这一步不用追求复杂。

只要你能在 UI 里看见 trace，就已经迈过最重要的一步了。

### 最小练手代码

```python
import os
from langchain_openai import ChatOpenAI

print("LANGSMITH_TRACING =", os.getenv("LANGSMITH_TRACING"))

llm = ChatOpenAI(model="gpt-4.1-mini", temperature=0)
result = llm.invoke("用两句话解释什么是 LangSmith")

print(result.content)
```

先完成这一步，再去 LangSmith UI 里找这条 trace。

------

## 五、看 Trace 时到底应该看什么

很多人第一次打开 trace，会有一种“信息好多，但我不知道看哪”的感觉。

你先只看这五件事：

### 1. 用户输入是什么

别小看这一步。

很多“模型回答奇怪”的问题，本质是你传给它的输入根本就不对。

### 2. 中间有没有调用工具

如果你预期它调工具却没调，那问题可能在：

- tool 描述不清
- prompt 没引导到位
- 模型本身判断失误

### 3. 工具入参对不对

Agent 调了工具，不代表就调对了。

比如你让它查“北京天气”，结果它把参数传成了“今天”。

### 4. 模型输出是不是偏题

这里要判断：

- 是模型理解歪了
- 还是工具返回内容本身就不够好

### 5. 耗时和 token 大概怎样

真实项目里这很重要，因为它直接影响：

- 成本
- 响应速度
- 用户体验

------

## 六、如果不用 LangChain，还能怎么接 LangSmith

官方文档给了三种常见手段：

- `@traceable`
- `trace` 上下文
- `RunTree` API

新手怎么选？

### 1. 优先级建议

- 你用 LangChain / LangGraph：先吃自动 tracing
- 你是自定义代码：先看 `@traceable`
- 你要更细粒度控制：再看 `trace` 和 `RunTree`

### 2. 新手不要一上来就搞底层埋点

因为你现在最需要的是：

- 先看见链路

不是一开始就研究所有 tracing API。

### `@traceable` 最小例子

```python
from langsmith import traceable
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4.1-mini", temperature=0)

@traceable(name="classify_question")
def classify_question(text: str) -> str:
    result = llm.invoke(f"判断这句话属于什么意图：{text}")
    return result.content

print(classify_question("帮我总结这段 Redis 笔记"))
```

这个例子的价值是：

- 即使你不是完整 LangChain agent，也可以把关键函数打点出来

------

## 七、评测为什么重要

LLM 应用很容易掉进一个坑：

- “我看了几条样例，感觉挺好”

这不叫评测，这叫主观印象。

为什么不够？

因为 LLM 有明显的非确定性：

- 同一个输入，多次输出可能有波动
- 换个模型、改个 prompt、加个工具，整体效果都可能漂

所以评测的价值在于：

- 让你用更稳定的方式判断改动是不是变好了

------

## 八、LangSmith 评测三件套

官方在 evaluation quickstart 里讲得非常清楚。

跑一个 evaluation，最核心的是三样东西：

### 1. Dataset

测试数据集。

也就是：

- 你拿什么输入来测
- 有没有参考答案

### 2. Target function

你要评测的目标函数。

它可以是：

- 一次 LLM 调用
- 一个模块
- 整条工作流

### 3. Evaluators

评分函数。

它负责判断输出好不好。

比如：

- 是否分类正确
- 是否命中参考答案
- 是否满足格式要求

这三件套你必须记熟。

------

## 九、做一个最小 Evaluation 示例

新手最适合从“意图分类”这种简单任务开始。

### 1. 准备数据集

例如 10 到 20 条：

- “帮我总结这段 Java 并发笔记”
- “把这段接口文档改正式一点”
- “解释一下缓存击穿”

参考标签分别是：

- `总结`
- `改写`
- `解释`

### 2. 写 target function

就是你 LangChain 里做结构化输出的那段代码。

### 3. 写 evaluator

最小 evaluator 可以极其简单：

- 模型输出的 `intent` 是否等于参考标签

### 4. 你从这次实验想得到什么

不是“看起来还行”，而是：

- 哪类输入最容易错
- 改 prompt 之后是否真的提升
- 哪些例子应该沉淀成长期测试集

### 一个更像实战的最小代码骨架

下面这个例子用结构化输出做“意图分类”，再用 evaluator 检查是否命中参考标签。

```python
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI

class IntentResult(BaseModel):
    intent: str = Field(description="意图，例如 总结、解释、改写")
    reason: str = Field(description="判断依据")

llm = ChatOpenAI(model="gpt-4.1-mini", temperature=0)
structured_llm = llm.with_structured_output(IntentResult)

dataset = [
    {"input": "帮我总结这段 Java 并发笔记", "expected_intent": "总结"},
    {"input": "解释一下 Redis 为什么会出现缓存穿透", "expected_intent": "解释"},
    {"input": "把这段接口文档改正式一点", "expected_intent": "改写"},
]

def target_function(text: str) -> dict:
    result = structured_llm.invoke(text)
    return result.model_dump()

def evaluator(output: dict, expected_intent: str) -> bool:
    return output["intent"] == expected_intent

correct = 0

for row in dataset:
    output = target_function(row["input"])
    ok = evaluator(output, row["expected_intent"])
    correct += int(ok)
    print("=" * 60)
    print("输入：", row["input"])
    print("输出：", output)
    print("期望：", row["expected_intent"])
    print("是否命中：", ok)

print("准确条数：", correct)
print("总条数：", len(dataset))
print("准确率：", correct / len(dataset))
```

这段代码还不是 LangSmith 平台版 evaluation，但它能先帮你理解：

- dataset 是什么
- target function 是什么
- evaluator 是什么

------

## 十、Dataset 为什么要版本化

官方专门提 dataset versioning，不是没事找事。

这件事很关键，因为你的数据集不会一成不变。

真实使用里你会不断做这些事：

- 补充坏例子
- 修正标签
- 删除无效样本
- 拆 train / test

如果没有版本概念，你后面很难回答：

- “这次实验到底是基于哪一版数据做的？”

所以 dataset 版本化，本质上是在给你的评测过程建立可追溯性。

------

## 十一、Prompt Engineering 在 LangSmith 里怎么理解

很多人一说 prompt engineering，就想到“研究怎么写一句更厉害的话”。

这个理解太窄了。

按 LangSmith 的思路，更实用的理解是：

- 创建 prompt
- 测试 prompt
- 版本化 prompt
- 和数据集一起评估 prompt

也就是说，Prompt 在这里不是“灵感写作”，而是工程资产。

你要关心的是：

- 这个 prompt 当前版本是什么
- 它在数据集上的效果怎样
- 改完之后是变好还是变差

这个思路非常值得后端工程师学习。

### 练手建议

你可以准备两个 prompt 版本，分别跑同一组样本：

```text
版本 A：请判断用户问题的意图，只返回意图名称。
版本 B：请判断用户问题的意图，意图只能是 总结 / 解释 / 改写 / 复习，并给出一句判断依据。
```

观察点：

- 哪个版本更稳定
- 哪个版本更容易命中你的 evaluator
- 哪个版本更适合后续接结构化输出

------

## 十二、LangSmith 的关键技术细节

这一节重点回答一个问题：

**LangSmith 不只是“看日志网页”，它到底在工程上帮你抓什么。**

### 1. Trace 和 Run 是分层的

你可以把它近似理解成：

- trace：一次完整请求
- run：trace 里的子步骤

这意味着你在排查问题时可以区分：

- 是整条链路慢
- 还是某一步特别慢

### 2. LangSmith 对 LLM 应用的观测粒度，比普通应用日志更贴近 agent

普通日志常见是：

- 请求来了
- 调接口了
- 返回了

而 LLM 应用更需要看到：

- 输入 prompt 是什么
- 模型调没调 tool
- tool 参数是什么
- 中间输出长什么样
- 最终结构化结果是什么

LangSmith 的价值就在这里。

### 3. Evaluation 的核心不是“算分”，而是“让改动可比较”

你后面会不断改：

- prompt
- model
- tool 描述
- schema

如果没有 evaluation，你很难回答：

- 这次修改到底是更好了，还是只是碰巧看起来更顺眼

### 4. Dataset、Prompt、Experiment 最好一起看

这是一个很关键的工程意识：

- dataset 决定你拿什么测
- prompt 决定你怎么问
- experiment 决定这次比较的结果

三者是联动的，不要割裂看。

### 5. Tracing 和 Evaluation 是两条线，但最好同时存在

- tracing 解决“这次为什么错”
- evaluation 解决“这一版整体有没有变好”

这两个视角不能互相替代。

------

## 十三、LangSmith 的最佳实践

### 1. 从第一天就开 tracing

不要等“项目大了再说”。

因为越早开 tracing，你越早形成正确的调试方式。

### 2. 一个应用一个 project，不要乱混

比如：

- 学习助手一个 project
- RAG demo 一个 project
- 分类器一个 project

这样 trace 才有可读性。

### 3. 先做小而干净的数据集，再扩充

不要一开始就想搞 500 条样本。

更好的顺序是：

1. 先做 10 到 20 条高质量样本
2. 先跑通评测闭环
3. 再不断补坏例子

### 4. evaluator 要尽量具体

不推荐这种：

- “整体感觉好不好”

更推荐这种：

- 意图字段是否命中
- 是否包含必须字段
- 是否引用了错误来源

### 5. 保存坏例子，比收藏好例子更重要

真实优化中，最有价值的往往不是：

- 那些本来就答得不错的样本

而是：

- 最容易翻车的边界样本

### 6. 做改动时，一次只动一个主要变量

例如：

- 这次只改 prompt
- 下次只换 model
- 再下次只改 schema

否则你很难知道提升来自哪里。

### 7. 先看失败案例，再看平均分

平均分重要，但不够。

因为很多真实问题都藏在：

- 某几类高风险失败样本

### 8. 把 Prompt 当成资产管理

也就是：

- 要版本化
- 要能对比
- 要能回滚
- 要能和评测结果挂钩

------

## 十四、练手项目 1：给 LangChain Agent 接上 Tracing

### 目标

写一个最小 agent，然后在 LangSmith 里看完整链路。

### 参考代码

```python
from langchain.agents import create_agent

def search_notes(topic: str) -> str:
    """搜索学习笔记"""
    notes = {
        "redis": "Redis 重点：数据类型、持久化、缓存穿透、主从复制。",
        "mysql": "MySQL 重点：索引、事务、锁、MVCC。",
    }
    return notes.get(topic.lower(), f"没有找到 {topic} 相关笔记。")

agent = create_agent(
    model="openai:gpt-4.1-mini",
    tools=[search_notes],
)

result = agent.invoke(
    {"messages": [{"role": "user", "content": "帮我快速复习 Redis"}]}
)

print(result)
```

### 你在 LangSmith 里要重点看

- 有没有调用 `search_notes`
- 工具入参是不是 `redis`
- 最终回答有没有利用工具结果

------

## 十五、练手项目 2：跑一个最小评测实验

### 目标

做一个最小“意图分类”评测。

### 推荐步骤

1. 先用前面的本地 `dataset + target_function + evaluator` 跑通
2. 再把这批样本迁到 LangSmith dataset
3. 再在平台上跑 experiment

### 你至少要准备这些样本

```python
samples = [
    {"input": "帮我总结 Redis 持久化", "expected": "总结"},
    {"input": "解释一下 MySQL 幻读", "expected": "解释"},
    {"input": "把这段文档改正式一点", "expected": "改写"},
    {"input": "明天面试 RocketMQ，给我复习路线", "expected": "复习"},
    {"input": "帮我压缩这段自我介绍", "expected": "改写"},
]
```

### 继续升级

- 增加容易混淆的样本
- 增加错误样本分析
- 比较两个 prompt 版本

------

## 十六、最容易踩的坑

### 1. 只看最终输出，不看链路

这样你根本不知道错在哪。

### 2. 觉得 tracing 是可有可无

这会让你调试 agent 的效率非常低。

### 3. 只做人工 eyeballing，不做 evaluation

你会很难稳定比较不同版本的效果。

### 4. 数据集乱改但不关心版本

最后实验结果很难追溯。

### 5. 把 Prompt 当灵感，不当资产

真实项目里，prompt 应该能：

- 保存
- 对比
- 回滚
- 评估

### 6. 一次改太多变量

这样最后很难解释效果变化来自哪里。

### 7. 只看均值，不看失败样本

这会掩盖很多高风险错误。

------

## 十七、学完这篇你应该掌握什么

如果你把这篇内容吃透了，至少应该能做到：

- 能独立打开 LangSmith tracing
- 能看懂 project / trace / run / thread
- 能通过 trace 找 bug
- 能说出 evaluation 三件套
- 能做一个最小 dataset + evaluator + experiment
- 能理解 prompt versioning 和 dataset versioning 的价值

做到这里，你就已经不再是“只能靠猜调 LLM 应用”的状态了。

------

## 🔗 推荐继续看

- [[项目与成长/实习方法论/AI应用/LangChain 从入门到能写 Agent]] —— 先把应用跑起来
- [[项目与成长/实习方法论/AI应用/LangGraph 入门：从状态图到可持久化 Agent]] —— 再学更复杂的编排
- [[项目与成长/实习方法论/AI应用/LangChain、LangSmith、LangGraph 一周入门攻略]] —— 一周学习总路线
