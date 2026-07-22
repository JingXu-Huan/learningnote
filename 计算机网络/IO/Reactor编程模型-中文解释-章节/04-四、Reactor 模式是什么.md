# 四、Reactor 模式是什么

Reactor 可以理解为一个“IO 事件分发器”：

1. Reactor 等待一组 Channel 的 IO 事件；
2. 操作系统告诉 Reactor 哪些 Channel 已经就绪；
3. Reactor 根据事件类型找到对应的 Handler；
4. Handler 执行一小段非阻塞处理；
5. 如果任务还没有完成，Handler 保存状态，等待下一次事件。

它的关键点不是“用了一个叫 Reactor 的类”，而是：

> 把 IO 等待集中管理，把具体动作绑定到事件上，并通过状态机分多次推进一个请求。

---

