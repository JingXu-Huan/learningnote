# 用 Go channel 模拟 Java 并发工具

> 面向熟悉 Java 并发编程、正在学习 Go 的开发者。通过阻塞队列、信号量、互斥锁、倒计时门闩、Future、worker pool 和循环屏障，理解 channel 如何协调并发。
>
> 本文演示核心行为，不完整复刻 Java API 的公平性、可重入、取消和异常处理语义。代码块为独立片段，省略 `package` 和必要的 `import`；`doWork`、`prepare`、`execute` 等函数代表业务逻辑。Future 泛型示例需要 Go 1.18 或以上版本。

------

## 目录

- [1. 先理解 channel 的四种能力](#1-先理解-channel-的四种能力)
- [2. 阻塞队列：BlockingQueue 与 SynchronousQueue](#2-阻塞队列blockingqueue-与-synchronousqueue)
- [3. 信号量：Semaphore](#3-信号量semaphore)
- [4. 互斥锁：模拟普通 Lock](#4-互斥锁模拟普通-lock)
- [5. 倒计时门闩：CountDownLatch](#5-倒计时门闩countdownlatch)
- [6. 异步结果：Future](#6-异步结果future)
- [7. 任务执行器：ExecutorService](#7-任务执行器executorservice)
- [8. 循环屏障：CyclicBarrier](#8-循环屏障cyclicbarrier)
- [9. 其他工具的对应边界](#9-其他工具的对应边界)
- [10. 常见坑与面试表达](#10-常见坑与面试表达)
- [参考资料](#参考资料)

## 1. 先理解 channel 的四种能力

channel 不仅可以传递数据，也能协调执行顺序。核心能力如下：

| 能力 | 行为 | 可以解决的问题 |
| --- | --- | --- |
| 无缓冲通信 | 发送与接收必须配对，双方才能完成通信 | 直接交接任务 |
| 缓冲与阻塞 | 缓冲满时发送阻塞；空且未关闭时接收阻塞 | 排队、限制并发 |
| `select` | 等待多个通信事件；带 `default` 时可以非阻塞 | 超时、取消、尝试提交 |
| `close` | 已发送数据仍可读取；耗尽后接收立即返回零值与 `ok == false` | 完成通知、一次性广播 |

**发送一条消息通常只供一个接收者取走；关闭一个空的通知 channel，可以让全部等待者继续。**

这是 Java 工具与 channel 模式之间的对应关系：

| Java 并发工具 | channel 实现思路 |
| --- | --- |
| `ArrayBlockingQueue` | 有缓冲 channel |
| `SynchronousQueue` | 无缓冲 channel |
| `Semaphore` | channel 容量代表许可数 |
| 普通互斥锁 | 容量为 1 的 channel |
| `CountDownLatch` | 收集 N 个完成信号，再关闭通知 channel |
| `CyclicBarrier` | 到齐后关闭本轮 channel，再创建下一轮 |
| `Future` | 保存异步结果，用 channel 通知完成 |
| `ExecutorService` | 任务 channel + 固定数量的 goroutine |
| `Condition` | 简单事件用消息通知或关闭广播；反复等待条件需要额外协调 |

这些模式基于 [Go 语言规范](https://go.dev/ref/spec#Channel_types) 中的 channel 语义，以及 [Java 并发包](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/package-summary.html) 中各工具的用途。

## 2. 阻塞队列：BlockingQueue 与 SynchronousQueue

### 2.1 有缓冲 channel：有界阻塞队列

~~~go
queue := make(chan string, 2)

// 类似 BlockingQueue.put：队列满时阻塞。
queue <- "任务 A"
queue <- "任务 B"

// 类似 BlockingQueue.take：队列空且未关闭时阻塞。
task := <-queue
fmt.Println(task)
~~~

非阻塞提交类似 `offer`：

~~~go
select {
case queue <- "任务 C":
    fmt.Println("提交成功")
default:
    fmt.Println("队列已满")
}
~~~

这种方式把“尝试发送”和“判断能否发送”作为一个操作。不要先判断 `len(queue) < cap(queue)` 再发送，因为判断结束后，其他 goroutine 可能已经填满队列。

### 2.2 无缓冲 channel：直接交接

~~~go
handoff := make(chan string)

go func() {
    handoff <- "任务" // 等待接收方接手。
}()

task := <-handoff
fmt.Println(task)
~~~

它类似 `SynchronousQueue`，不存储待取走的元素。发送完成意味着值已经被接收，**不意味着任务已经处理完成**；要确认处理完成，需要额外的应答或完成信号。[Go 发送语义](https://go.dev/ref/spec#Send_statements)

## 3. 信号量：Semaphore

需求：最多允许 3 个任务同时执行。

~~~go
sem := make(chan struct{}, 3)

for i := 0; i < 10; i++ {
    sem <- struct{}{} // acquire：占用许可，满了就等待。

    go func(id int) {
        defer func() {
            <-sem // release：释放许可。
        }()

        doWork(id)
    }(i)
}
~~~

本文统一使用以下约定：

~~~text
发送一个空结构体 → 占用一个许可
接收一个空结构体 → 释放一个许可
~~~

`struct{}{}` 只表示信号，不承载业务数据。

把获取许可放在 `go` 之前，可以限制启动速度，避免先创建大量等待许可的 goroutine。这个模式也是 [Effective Go](https://go.dev/doc/effective_go#channels) 中的 channel 信号量用法。

需要注意：

- 循环结束只表示任务已经启动，等待所有任务结束还需要完成通知。
- 每次成功获取必须对应一次释放；用 `defer` 保证正常返回路径会释放。
- 信号量 channel 不应通过 `close` 释放许可；关闭会破坏协议。
- 如果需要取消等待，可以用 `select` 同时等待发送许可和 `ctx.Done()`。

## 4. 互斥锁：模拟普通 Lock

把信号量容量设置为 1，就能限制同一时间只有一个调用进入临界区。

~~~go
lock := make(chan struct{}, 1)
counter := 0

increment := func() {
    lock <- struct{}{} // Lock。
    defer func() {
        <-lock // Unlock。
    }()

    counter++
}
~~~

临界区就是访问受保护共享数据的代码区域。这里所有并发读写 `counter` 的代码都必须遵守同一个锁协议。

这个实现不具备 Java `ReentrantLock` 的可重入语义：同一个 goroutine 在尚未释放时再次获取，会阻塞。它也没有提供公平性保证或持有者校验。

在实际项目中，保护共享变量通常直接使用 [`sync.Mutex`](https://pkg.go.dev/sync#Mutex) 更清楚；学习这个实现的价值是理解“互斥”和“容量为 1 的许可”之间的联系。

## 5. 倒计时门闩：CountDownLatch

需求：3 个任务完成后，所有等待者都可以继续。

~~~go
const n = 3

completed := make(chan struct{}, n)
allDone := make(chan struct{})

// 协调者：收齐 n 个信号后打开门。
go func() {
    for i := 0; i < n; i++ {
        <-completed
    }
    close(allDone)
}()

for i := 0; i < n; i++ {
    go func(id int) {
        defer func() {
            completed <- struct{}{} // 类似 countDown()。
        }()

        doWork(id)
    }(i)
}

// 类似 await()。
<-allDone
fmt.Println("全部完成")
~~~

两个 channel 分别承担不同职责：

- `completed` 用来收集计数，一条消息由协调者接收一次。
- `allDone` 用来广播完成，关闭后全部等待者都可以继续，后来的等待者也无需阻塞。

**只从 `completed` 接收 N 次，是一个等待者收集 N 次完成；增加 `allDone` 才能让多个等待者共享完成状态。**

这个片段假设恰好有 N 次完成通知，模拟固定任务数的一次性门闩，并未实现完整的 `CountDown()` API。工程中通常用 [`sync.WaitGroup`](https://pkg.go.dev/sync#WaitGroup) 等待一组任务结束。

## 6. 异步结果：Future

### 6.1 一次消费的结果 channel

~~~go
result := make(chan int, 1)

go func() {
    result <- expensiveCalculation()
}()

value := <-result // 类似一次 Future.get()。
fmt.Println(value)
~~~

缓冲容量为 1，让计算完成后可以交付结果，不必等待调用方立即接收。

但这个结果只能被取走一次；再次接收会阻塞。关闭结果 channel 也不会缓存结果：数据耗尽后，接收得到的是零值。

### 6.2 支持重复读取的 Future

Java 的 `Future.get()` 可以重复调用。更接近的实现是：保存计算结果，再关闭 channel 通知完成。

~~~go
type Future[T any] struct {
    done  chan struct{}
    value T
    err   error
}

func Async[T any](fn func() (T, error)) *Future[T] {
    f := &Future[T]{
        done: make(chan struct{}),
    }

    go func() {
        f.value, f.err = fn()
        close(f.done)
    }()

    return f
}

func (f *Future[T]) Get(ctx context.Context) (T, error) {
    select {
    case <-f.done:
        return f.value, f.err

    case <-ctx.Done():
        var zero T
        return zero, ctx.Err()
    }
}
~~~

此实现需要导入 `context`。多个 goroutine 可以调用 `Get`，读取同一个完成结果；代码只在计算 goroutine 中写入结果一次。

为什么结果字段不需要额外加锁？结果写入发生在 `close(f.done)` 之前，调用者收到关闭通知后才读取。这个顺序由 [Go 内存模型](https://go.dev/ref/mem#chan) 保证。

边界如下：

- `Get` 的超时只结束等待，不会自动停止计算；计算取消需要让 `fn` 接收并检查 context。
- 如果完成和取消同时可用，`select` 可能选择任意一个分支，此实现不保证取消优先。
- 此实现没有处理计算函数的 panic。
- 如果 `T` 包含 map、slice 或指针，后续对其底层数据的并发修改仍需要同步。

## 7. 任务执行器：ExecutorService

goroutine 比线程轻量，但仍需要限制并发和排队数量。固定 worker + 任务 channel 可以模拟执行器的核心行为。

~~~go
jobs := make(chan func(), 100)
workersDone := make(chan struct{}, 4)

// 固定 4 个 worker。
for i := 0; i < 4; i++ {
    go func() {
        defer func() {
            workersDone <- struct{}{}
        }()

        for job := range jobs {
            job()
        }
    }()
}

// 类似 execute()。
for i := 0; i < 10; i++ {
    id := i
    jobs <- func() {
        doWork(id)
    }
}

// 停止接收新任务，worker 处理完已排队任务后退出。
close(jobs)

// 类似等待执行器终止。
for i := 0; i < 4; i++ {
    <-workersDone
}
~~~

~~~mermaid
flowchart LR
    P["任务提交者"] --> Q["有界任务 channel"]
    Q --> W1["worker 1"]
    Q --> W2["worker 2"]
    Q --> W3["worker 3"]
    Q --> W4["worker 4"]
    W1 --> D["执行任务"]
    W2 --> D
    W3 --> D
    W4 --> D
~~~

这里有两个独立的限制：

| 参数 | 限制对象 |
| --- | --- |
| worker 数量为 4 | 同时执行的任务数量 |
| channel 容量为 100 | 已排队、尚未被取走的任务数量 |

队列满时，提交者阻塞，形成背压，也就是让生产速度受消费速度约束。关闭队列后，`range` 仍会消费缓冲中的任务，再结束循环。

示例假设任务正常返回，由单个提交方在全部提交结束后关闭队列。多提交者需要协调关闭时机；任务错误、panic、拒绝策略和强制关闭等行为需要另行设计。相关生命周期问题可参考 [Go 官方并发文章](https://go.dev/blog/pipelines)。

## 8. 循环屏障：CyclicBarrier

`CountDownLatch` 是“任务完成后，等待者继续”；`CyclicBarrier` 是“参与者都到齐后，大家一起继续”，而且可以反复使用。

只使用 channel 管理计数和每轮通知：

~~~go
type barrierState struct {
    remaining int
    done      chan struct{}
}

type Barrier struct {
    parties int
    state   chan barrierState
}

func NewBarrier(n int) *Barrier {
    if n <= 0 {
        panic("parties must be positive")
    }

    b := &Barrier{
        parties: n,
        state:   make(chan barrierState, 1),
    }

    b.state <- barrierState{
        remaining: n,
        done:      make(chan struct{}),
    }
    return b
}

func (b *Barrier) Await() {
    s := <-b.state // 独占计数状态。
    currentRound := s.done

    s.remaining--
    if s.remaining == 0 {
        close(currentRound) // 本轮所有参与者继续。

        s = barrierState{
            remaining: b.parties,
            done:      make(chan struct{}), // 下一轮。
        }
    }

    b.state <- s // 归还状态。
    <-currentRound
}
~~~

参与者这样使用，最后用完成信号保证调用方不会提前结束：

~~~go
barrier := NewBarrier(3)
finished := make(chan struct{}, 3)

for i := 0; i < 3; i++ {
    go func(id int) {
        defer func() { finished <- struct{}{} }()

        prepare(id)
        barrier.Await() // 三人都完成准备，才进入执行阶段。

        execute(id)
        barrier.Await() // 三人都完成执行，才继续。
    }(i)
}

for i := 0; i < 3; i++ {
    <-finished
}
~~~

关键点：

1. `state` 容量为 1，里面保存唯一一份状态；取走状态的调用者拥有修改权。
2. 每个调用者保存本轮的 `done`，然后减少剩余人数。
3. 最后一个到达者关闭本轮 `done`，创建下一轮状态。
4. 先归还状态，再等待本轮通知，避免拿着状态阻塞其他参与者。

每轮必须创建新的通知 channel，因为关闭后的 channel 无法重新打开。

此实现要求固定数量的参与者每轮都调用一次 `Await`。参与者提前退出会导致其他人一直等待；它没有实现 Java 的超时、屏障损坏、重置和 barrier action 等行为。[Java CyclicBarrier 文档](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)

## 9. 其他工具的对应边界

| Java 工具 | Go 中的实现选择 |
| --- | --- |
| `Condition` | 简单事件可以发送通知或关闭广播；反复等待共享条件通常用 `sync.Cond` |
| `ReadWriteLock` | 通常使用 `sync.RWMutex` |
| `AtomicInteger`、CAS | 使用 `sync/atomic` |
| `ConcurrentHashMap` | 根据访问模式选择锁保护的 map、`sync.Map`，或让一个 goroutine 独占 map |
| `CompletableFuture` | 用 channel 串联计算阶段；完整实现还需要错误、取消和组合规则 |
| `Phaser` | 需要协调每轮参与者注册、退出和到达计数，固定人数的 Barrier 不够 |

Go 官方指出，简单通知场景中，发送 channel 消息类似 `Signal`，关闭 channel 类似 `Broadcast`。但关闭是永久状态；要反复广播，需要管理每轮通知 channel，并保证条件检查与等待注册之间不会丢失通知。因此不能只替换一行调用就完整模拟条件变量。[Go Cond 文档](https://pkg.go.dev/sync#Cond)

## 10. 常见坑与面试表达

### 10.1 channel 安全不等于传递的数据自动安全

channel 本身支持并发发送和接收。但传递 map、slice、指针后，如果双方仍同时修改底层数据，可能发生数据竞争。需要约定数据所有权，或使用额外同步。

### 10.2 明确谁关闭 channel

通常由发送方或知道全部发送者已经退出的协调者关闭。接收方不能在仍可能有人发送时随意关闭。

- 向已关闭的 channel 发送会 panic。
- 重复关闭 channel 会 panic。
- 关闭 nil channel 会 panic。
- 从 nil channel 接收或向其发送会一直阻塞。
- channel 不必为了释放资源而关闭；关闭用于表达协议状态。

这些行为见 [Go 语言规范](https://go.dev/ref/spec#Close)。

### 10.3 等待者取消不等于生产者取消

如果消费者提前退出，而生产者仍在发送，生产者可能永远阻塞。可以用有界缓冲解决已知数量的结果交付，或者让发送操作同时监听取消信号：

~~~go
select {
case output <- value:
    // 已交付。
case <-ctx.Done():
    return
}
~~~

发送方、接收方与任务计算都需要明确自己的退出路径。[Go 官方取消模式](https://go.dev/blog/pipelines)

### 10.4 channel 同时建立同步顺序

一次发送与对应接收、关闭与收到关闭通知之间，能建立内存模型中的同步关系。因此 channel 除了传消息，还能安全地发布完成前写入的数据。

但这不意味着“只要代码中使用了 channel，所有共享变量就都安全”。必须分析具体访问是否被相应通信顺序保护。[Go 内存模型](https://go.dev/ref/mem#chan)

### 10.5 面试时可以这样回答

> Go channel 可以利用缓冲容量实现阻塞队列和信号量，用容量为 1 的许可实现普通互斥；收集完成消息后关闭通知 channel，可以实现一次性门闩；保存异步结果并通过关闭通知发布，可以实现支持重复读取的 Future；任务 channel 配合固定 worker 可以实现有界执行器。循环屏障还需要每轮的计数和独立通知 channel。
>
> 这些模式模拟核心协调行为，不自动具备 Java API 的可重入、公平性、取消或异常语义。实际开发中，消息交接和任务流水线适合 channel，共享数据互斥和原子操作通常直接用 sync、sync/atomic。

相关笔记：[13：goroutine、channel 与并发治理](13-goroutine-channel与并发治理.md)。

## 参考资料

- [Go 语言规范：Channel types](https://go.dev/ref/spec#Channel_types)
- [Go 语言规范：Send statements](https://go.dev/ref/spec#Send_statements)
- [Go 语言规范：Close](https://go.dev/ref/spec#Close)
- [Go 内存模型：Channel communication](https://go.dev/ref/mem#chan)
- [Effective Go：Channels](https://go.dev/doc/effective_go#channels)
- [Go sync 包](https://pkg.go.dev/sync)
- [Go 并发模式：流水线和取消](https://go.dev/blog/pipelines)
- [Java SE 25：java.util.concurrent](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/package-summary.html)
- [Java SE 25：CyclicBarrier](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)

官方资料核对日期：2026-09-30。Java API 引用基于 Java SE 25，泛型 Future 示例适用于 Go 1.18+。
