# 🧠 JMM（Java 内存模型）

> 配套动画：[打开 JMM 场景](动画/JMM-JVM-GC-交互动画.html#jmm)
>
> 先说结论：**JMM 不是 JVM 的堆、栈布局图**。它定义的是多线程读写共享变量时，什么结果是合法的，以及如何建立可见性、有序性和原子性保证。

------

## 目录

- [一、JMM 到底解决什么问题](#一jmm-到底解决什么问题)
- [二、主内存与工作内存：只是抽象](#二主内存与工作内存只是抽象)
- [三、可见性、有序性、原子性](#三可见性有序性原子性)
- [四、happens-before：并发推理的核心](#四happens-before并发推理的核心)
- [五、volatile、synchronized 与 AtomicInteger](#五volatilesynchronized-与-atomicinteger)
- [六、一个安全发布示例](#六一个安全发布示例)
- [七、常见误区与面试速答](#七常见误区与面试速答)

------

## 一、JMM 到底解决什么问题

多个线程访问同一个共享变量时，不能只按“代码从上到下执行”来想。编译器和 CPU 为了优化，可能缓存数据、延迟写回或重排序；如果程序没有建立正确同步关系，线程 B 不一定能按我们期望的时机看到线程 A 的写入。

JMM（Java Memory Model）规定了并发程序的**可观察行为**，主要关心三个问题：

| 问题 | 含义 | 例子 |
| --- | --- | --- |
| 可见性 | 一个线程写入后，另一个线程何时能看到 | A 把开关设为 true，B 能否及时退出循环 |
| 有序性 | 哪些操作允许重排序，哪些必须保持顺序 | 先初始化数据，再发布“已就绪”标志 |
| 原子性 | 一个操作能否被其他线程“插队”观察到中间状态 | 两个线程同时执行 count++ 是否会丢失更新 |

JMM 的目标不是禁止所有优化，而是让正确同步的程序在不同 JVM 和硬件上都有可推理的结果。

------

## 二、主内存与工作内存：只是抽象

动画里的“主内存”和“工作内存”是 JMM 的教学抽象：

- **主内存**：共享变量交互遵循的模型位置。
- **工作内存**：每个线程对变量的私有副本、读取和写入过程的模型描述。

它们**不能**直接等同为：

- 主内存 = JVM 堆；
- 工作内存 = Java 虚拟机栈；
- 工作内存 = 某一级 CPU Cache。

真实机器上可能涉及寄存器、多级缓存、写缓冲区和编译器优化；JMM 只规定 Java 并发语义，不承诺具体物理布局。

> 需要区分“并发语义”和“运行时数据区”时，可看 [[JVM运行时数据区]]。

------

## 三、可见性、有序性、原子性

### 1️⃣ 可见性

线程 A 修改共享变量后，线程 B 是否能观察到新值。

常用的可见性建立方式：

- 对同一把监视器锁：A 解锁后，B 再加锁；
- 对同一个 volatile 变量：A 写后，B 再读；
- 线程启动和结束协作：start / join；
- 并发工具类内部提供的同步语义。

### 2️⃣ 有序性

只要不改变**单线程语义**，编译器和处理器可以重排序。因此下面的“先写数据，再写标志”在没有同步时，不能自动成为对其他线程可靠的发布协议。

~~~java
data = 42;
ready = true;
~~~

如果 ready 是 volatile，线程 A 的 volatile 写与线程 B 后续对同一 ready 的 volatile 读之间建立 happens-before；B 读到 ready == true 后，就能正确观察到 A 在发布前对 data 的写入。

### 3️⃣ 原子性

“一行 Java 代码”不一定是一个原子操作。例如 count++ 至少包含读取、加一、写回三个动作。

~~~java
count++; // read -> add -> write，不是原子操作
~~~

volatile 可以保证对 count 的单次读取和单次写入具备可见性/顺序语义，但不能让 count++ 自动变成原子操作。

------

## 四、happens-before：并发推理的核心

如果操作 A happens-before 操作 B，那么 A 的结果对 B 可见，并且 A 的执行顺序先于 B（这是 JMM 的内存可见性与顺序关系，不要机械理解成物理时间线）。

常用规则如下：

| 规则 | 关系 |
| --- | --- |
| 程序顺序规则 | 同一线程内，前面的操作 happens-before 后面的操作 |
| 监视器锁规则 | 对同一监视器的 unlock happens-before 后续 lock |
| volatile 变量规则 | 对同一 volatile 变量的写 happens-before 后续读 |
| 线程启动规则 | Thread.start() happens-before 新线程中的动作 |
| 线程终止规则 | 线程中的动作 happens-before 其他线程从 join() 成功返回 |
| 传递性 | A happens-before B，且 B happens-before C，则 A happens-before C |

动画中的发布链可这样推导：

1. 线程 A 先执行 value = 42；
2. A 再写 volatile ready = true；
3. 线程 B 后续读到 ready == true；
4. 根据程序顺序、volatile 规则和传递性，B 随后读取 value 时能看到 42。

------

## 五、volatile、synchronized 与 AtomicInteger

| 工具 | 可见性 | 有序性 | 互斥 / 复合原子性 | 典型场景 |
| --- | --- | --- | --- | --- |
| volatile | ✅ | ✅ | ❌ | 状态标志、一次性安全发布 |
| synchronized | ✅ | ✅ | ✅ | 临界区保护、多个字段保持一致 |
| Lock | ✅ | ✅ | ✅ | 需要可中断、超时、多个 Condition 的锁控制 |
| AtomicInteger 等 | ✅ | ✅ | 对支持的原子操作为 ✅ | 计数器、CAS 更新 |

### volatile 适合什么

适合“一个线程写，其他线程读”的状态信号，且不依赖 read-modify-write 复合操作。

### volatile 不适合什么

不适合下面这种“读取旧值、计算新值、写回”的竞争场景：

~~~java
private volatile int count = 0;

public void increment() {
    count++; // 仍然可能丢失更新
}
~~~

应改用 AtomicInteger 或锁：

~~~java
private final java.util.concurrent.atomic.AtomicInteger count =
        new java.util.concurrent.atomic.AtomicInteger();

public int increment() {
    return count.incrementAndGet();
}
~~~

------

## 六、一个安全发布示例

下面是动画对应的简化示例：

~~~java
public class Publisher {
    private int value;
    private volatile boolean ready;

    public void publish() {
        value = 42;     // 1. 普通写
        ready = true;   // 2. volatile 写：发布
    }

    public int consume() {
        if (ready) {    // 3. volatile 读：获得发布前的可见性
            return value;
        }
        return -1;
    }
}
~~~

这里的关键不是“强制刷新某一级缓存”，而是 JMM 保证：

- publish() 中 value = 42 在 ready = true 前；
- 对 ready 的 volatile 写 happens-before 后续读到 ready 的 volatile 读；
- 因此 consume() 读到 ready 为 true 时，随后读 value 可以看到 42。

若对象在构造过程中就被其他线程拿到引用，或发布流程比这个例子复杂，应使用更完整的同步协议。另一个常见工具是 **final 字段**：对象正确构造且没有在构造期间逸出时，final 字段有特殊的安全发布语义；但 final 引用不表示其指向对象的内部状态不可变。

------

## 七、常见误区与面试速答

### JMM 的主内存就是 JVM 堆吗？

不是。JMM 是并发语义模型；堆是 JVM 运行时数据区的逻辑概念，解决的是不同层面的问题。

### volatile 能保证线程安全吗？

不完整。它能保证单次读写的可见性和一定的顺序约束，但不能提供互斥，也不能让 count++、先检查再执行等复合操作原子化。

### synchronized 为什么既能加锁又能保证可见性？

对同一监视器，解锁 happens-before 后续加锁。线程进入同步块后能看到此前受该锁保护的写入，同时同步块提供互斥。

### 64 位 long / double 一定原子吗？

正确的并发代码不要依赖实现细节。JLS 仍允许非 volatile long / double 的读写被拆分；现代 HotSpot 通常会原子实现，但需要规范级保证时应使用 volatile 或同步工具。

### 一句话总结

> JMM 用 happens-before 描述跨线程可见性与顺序；volatile 管“看得见、顺序对”，锁和原子类再解决“不能同时改”的问题。

------

## 🔗 相关笔记

- [[JVM运行时数据区]] —— 运行时数据在 JVM 规范中的逻辑区域
- [[GC垃圾回收]] —— 对象何时从 GC Roots 角度变得不可达
- [[技术栈/Java与框架/多线程/线程池七大核心参数]] —— 线程池中的并发执行场景
- [Java Language Specification, Chapter 17](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html) —— JMM 规范参考
