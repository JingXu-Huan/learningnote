# 九、Checkpointer 和 Store 到底有什么区别 🗂️

这一块一定要分清，不然后面很容易全混成“记忆”。

## 1. Checkpointer

它保存的是：

- thread 级 graph state

适合：

- 短期记忆
- 当前会话延续
- human-in-the-loop
- time travel
- fault tolerance

更直白一点：

**checkpointer 负责把“当前这条流程跑到哪了”记下来。**

## 2. Store

它保存的是：

- graph state 之外的应用级数据

适合：

- 长期记忆
- 用户偏好
- 共享知识
- 跨线程数据

更直白一点：

**store 负责把“以后还想再用的信息”存下来。**

## 3. 一句话区分

- `checkpointer`：更像“流程存档”
- `store`：更像“长期资料库”

## 练手思路

做两个实验：

1. 把“当前这次对话已经走到哪一步”放进流程状态里
2. 把“用户长期偏好 Java / Redis / 分布式”放进单独数据结构里

这样你会更容易体会：

- 什么是短期流程状态
- 什么是长期业务信息

------

