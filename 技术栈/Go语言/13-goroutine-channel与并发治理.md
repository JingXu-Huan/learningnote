# 13：goroutine、channel 与并发治理

> 预计：90 分钟<br>
> 本章目标：不是“会写 go”，而是知道如何让并发任务可取消、可等待、有限制。

------

## 1. 四个工具各做什么

| 工具 | 主要职责 | Java 中可类比的概念 |
| --- | --- | --- |
| goroutine | 启动并发执行单元 | 提交线程池任务 |
| WaitGroup | 等待一组任务结束 | CountDownLatch |
| Mutex | 保护共享数据 | synchronized / Lock |
| channel | 传递任务或协调时序 | BlockingQueue 的一部分用途 |
| Context | 超时、取消、请求链路传播 | 取消信号与显式上下文 |

goroutine 很轻量，但它不会因为 HTTP 请求结束就自动停止。

## 2. 有界 worker pool

下面示例最多启动指定数量的 worker 来发送通知：

~~~go
func SendAll(
    ctx context.Context,
    jobs []int64,
    workers int,
    send func(context.Context, int64) error,
) error {
    if workers < 1 {
        return errors.New("workers must be positive")
    }

    jobCh := make(chan int64)
    var wg sync.WaitGroup
    var mu sync.Mutex
    var errs []error

    allErrors := func() error {
        mu.Lock()
        defer mu.Unlock()
        return errors.Join(errs...)
    }

    worker := func() {
        defer wg.Done()
        for {
            select {
            case <-ctx.Done():
                return
            case id, ok := <-jobCh:
                if !ok {
                    return
                }

                jobCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
                err := send(jobCtx, id)
                cancel()
                if err != nil {
                    mu.Lock()
                    errs = append(errs, err)
                    mu.Unlock()
                }
            }
        }
    }

    wg.Add(workers)
    for i := 0; i < workers; i++ {
        go worker()
    }

    for _, id := range jobs {
        select {
        case <-ctx.Done():
            close(jobCh)
            wg.Wait()
            return errors.Join(ctx.Err(), allErrors())
        case jobCh <- id:
        }
    }

    close(jobCh)
    wg.Wait()
    return allErrors()
}
~~~

示例需要导入 <code>context</code>、<code>errors</code>、<code>sync</code> 和 <code>time</code>。

这段代码练到的重点：

- worker 数量固定，避免无限创建 goroutine。
- 只有发送方关闭 channel。
- WaitGroup 保证函数返回前所有 worker 已退出。
- Mutex 保护错误列表。
- 每项任务有独立超时。

这个函数是“内部并发、调用方等待结果”的批处理，不是 fire-and-forget 后台任务。若它从 HTTP Handler 调用，Handler 会等待它结束；若业务需要请求结束后继续执行，应把任务持久化到队列或任务表，并设计独立 worker 的启动、重试、关闭与监控，而不是简单写一行 <code>go SendAll(...)</code>。

## 3. Context 不能强杀阻塞函数

Context 只是取消信号。<code>send</code> 必须主动使用传入的 Context 才能响应超时：

~~~go
request, err := http.NewRequestWithContext(ctx, http.MethodPost, url, body)
~~~

数据库则使用 <code>QueryContext</code>、<code>ExecContext</code>。如果第三方 SDK 不支持 Context，不能假装超时已经生效。

## 4. 验证数据竞争

~~~powershell
go test -race ./...
go test -run TestSendAll -count=100 ./...
~~~

race detector 不能证明所有并发代码都正确，但能抓住很多共享 map、计数器、slice 的并发读写错误。

## 5. 常见坑

- map 不是并发安全的。
- 不要关闭不属于自己的 channel。
- 不要在请求里启动无限生命周期的 goroutine。
- Context 应作为函数第一个参数传递，不要长期存进 struct。

下一章：[14-校园用水告警API-整合与验收.md](14-校园用水告警API-整合与验收.md)
