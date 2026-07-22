# LangGraph 入门：从状态图到可持久化 Agent 😎😎😎

> 适合人群：已经知道 LangChain 的基本玩法，想继续理解更底层 Agent 编排的人
> 
> 文档基线：基于官方文档整理，时间点为 `2026-07-09`

------

> 本文已按二级标题拆分为独立章节，按需进入对应笔记阅读。

## 章节目录

- [[LangGraph 入门：从状态图到可持久化 Agent-章节/01-一、先说结论：为什么 LangGraph 不适合一上来就学 🧭|一、先说结论：为什么 LangGraph 不适合一上来就学 🧭]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/02-二、LangGraph 到底解决什么问题 🏗️|二、LangGraph 到底解决什么问题 🏗️]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/03-三、最核心的三个概念：State、Node、Edge 🧠|三、最核心的三个概念：State、Node、Edge 🧠]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/04-四、Graph API 和 Functional API 怎么选|四、Graph API 和 Functional API 怎么选]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/05-五、为什么 LangGraph 比普通 Agent 循环更强|五、为什么 LangGraph 比普通 Agent 循环更强]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/06-六、第一个 LangGraph 该长什么样 ✨|六、第一个 LangGraph 该长什么样 ✨]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/07-七、Thinking in LangGraph：真正要学的是拆流程的方式|七、Thinking in LangGraph：真正要学的是拆流程的方式]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/08-八、Persistence：LangGraph 最有工程价值的一层能力 💾|八、Persistence：LangGraph 最有工程价值的一层能力 💾]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/09-九、Checkpointer 和 Store 到底有什么区别 🗂️|九、Checkpointer 和 Store 到底有什么区别 🗂️]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/10-十、本地运行 LangGraph Server 的意义|十、本地运行 LangGraph Server 的意义]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/11-十一、哪些场景更适合上 LangGraph 🚦|十一、哪些场景更适合上 LangGraph 🚦]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/12-十二、LangGraph 的关键技术细节 ⚙️|十二、LangGraph 的关键技术细节 ⚙️]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/13-十三、LangGraph 的最佳实践 ✅|十三、LangGraph 的最佳实践 ✅]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/14-十四、练手项目 1：写一个最小状态图 🧩|十四、练手项目 1：写一个最小状态图 🧩]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/15-十五、练手项目 2：加上条件分支和工具调用 🔀|十五、练手项目 2：加上条件分支和工具调用 🔀]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/16-十六、练手项目 3：体验持久化思路 🧵|十六、练手项目 3：体验持久化思路 🧵]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/17-十七、最容易踩的坑 ⚠️|十七、最容易踩的坑 ⚠️]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/18-十八、学完这篇你应该掌握什么 🎉|十八、学完这篇你应该掌握什么 🎉]]
- [[LangGraph 入门：从状态图到可持久化 Agent-章节/19-🔗 推荐继续看|🔗 推荐继续看]]
