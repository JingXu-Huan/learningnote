# Go 堆内存与 Java 内存对照：从对象分配到 GC 与 happens-before

配套交互页：[Go × Java 内存实验室](https://jingxu-huan.github.io/learningnote/visualizations/go-java-memory/)。

面向已有 Java 基础、正在学习 Go 的读者。先回答对象存在哪里、为什么进入堆、怎样分配与回收，再单独比较 Go Memory Model 和 Java JMM。

**范围**：Go 实现以标准工具链和 go1.26.0 的 runtime 源码为基线；Java 规范引用 Java SE 25，具体分配与 GC 示例采用 HotSpot / G1。这些版本用于固定依据，不表示当前最新版本，也不能代表其它 Go 实现和 JVM 回收器。

## 目录

- [一、先区分两个层次的内存问题](#一先区分两个层次的内存问题)
- [二、Go 与 Java 的内存全景](#二go-与-java-的内存全景)
- [三、沿一个对象理解逃逸分析](#三沿一个对象理解逃逸分析)
- [四、Go 堆分配器的组织方式](#四go-堆分配器的组织方式)
- [五、mcache 与 Java TLAB 怎样类比](#五mcache-与-java-tlab-怎样类比)
- [六、Go GC 怎样找到并回收垃圾](#六go-gc-怎样找到并回收垃圾)
- [七、与 G1 的分代和疏散对照](#七与-g1-的分代和疏散对照)
- [八、小切片为什么可能保留大数组](#八小切片为什么可能保留大数组)
- [九、Go Memory Model 与 Java JMM](#九go-memory-model-与-java-jmm)
- [十、诊断时分别观察哪些指标](#十诊断时分别观察哪些指标)
- [十一、参数与优化应怎样理解](#十一参数与优化应怎样理解)
- [十二、常见误区与复习问题](#十二常见误区与复习问题)
- [十三、官方资料与源码入口](#十三官方资料与源码入口)

------

## 一、先区分两个层次的内存问题

| 层次             | 核心问题                                 | Go                                     | Java                                       |
| ---------------- | ---------------------------------------- | -------------------------------------- | ------------------------------------------ |
| 运行时存储与回收 | 值存在哪里、如何分配、何时可复用         | goroutine 栈、堆、逃逸分析、分配器、GC | JVMS 数据区、HotSpot 分配、TLAB、具体 GC   |
| 并发读写的顺序   | 一个执行者的写入何时能被另一个可靠地观察 | Go Memory Model、channel、锁、原子操作 | Java JMM、volatile、监视器、happens-before |

“对象在共享堆上”没有建立安全发布关系。“GC 有写屏障”也不意味着业务读写已经同步。

官方 [Go Memory Model](https://go.dev/ref/mem) 与 [JLS 第 17 章](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html) 主要属于第二层。把 JMM 直接画成堆、栈、方法区，会漏掉它真正约束的同步与可见性。

## 二、Go 与 Java 的内存全景

### 2.1 Go：每个 goroutine 有栈，运行时统一管理对象堆

函数调用使用栈帧。参数、局部值与中间结果可能使用栈，也可能被优化到寄存器；不能给每个源码变量都机械地分配一个物理格子。

堆承载需要动态存储的对象。堆对象不绑定某次函数调用：创建它的函数返回后，只要仍被需要，就可以继续存在。

- 栈上的指针可以指向堆对象，指针与被指向对象不在同一位置很正常。
- struct 是值类型，可以嵌入另一个 struct、数组或切片的元素中，不必每层都单独分配。
- 全局变量自身不应一律描述为“逃逸到堆”。全局数据中的引用可以保留堆对象。
- 进程还包含代码、栈、运行时元数据、映射等，对象堆不等于进程总内存。
- 堆 GC 不移动存活堆对象，与 goroutine 栈可能被复制调整并不矛盾。

依据：[Go FAQ](https://go.dev/doc/faq#stack_or_heap)、[Go GC Guide](https://go.dev/doc/gc-guide)、[runtime/malloc.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/malloc.go)。

### 2.2 Java：JVMS 逻辑区域与 HotSpot 实现分开看

| 规范区域   | 主要职责                     | 边界                             |
| ---------- | ---------------------------- | -------------------------------- |
| PC         | 每线程的执行位置等信息       | native 方法对应规则需按规范理解  |
| JVM 栈     | 由栈帧组织调用               | 局部变量表里的引用不等于对象本体 |
| 本地方法栈 | 支持本地方法执行             | 具体实现由 JVM 决定              |
| 堆         | 类实例和数组的规范级分配区域 | JIT 可能消除实际分配             |
| 方法区     | 类结构、运行时常量池等       | 逻辑定义不等于全部 Metaspace     |

HotSpot 的 Metaspace、Code Cache、直接内存是实现或 API 层面的概念，不能全部塞进“方法区”。直接缓冲区可以同时涉及堆上的包装对象与堆外的数据存储。

交互页的线程示意以平台线程为例。虚拟线程涉及挂载与栈块等实现，不能推导“每个虚拟线程永久占用一个固定本地线程栈”。

依据：[JVMS §2.5](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5)、[JVMS §2.6](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6)、[JEP 444](https://openjdk.org/jeps/444)。

## 三、沿一个对象理解逃逸分析

### 3.1 Go 为什么可以返回局部值的地址

```go
type User struct {
    Name string
    Age  int
}

func newUser() *User {
    u := User{Name: "小明", Age: 21}
    return &u
}
```

调用者在函数返回后还要使用这个值，编译器需要安排足够长的存储生命周期。存在实际堆分配时，可以直接为它分配堆存储。

不能解释成“先放栈上，return 时才发现需要搬到堆”。逃逸分析在编译期分析引用流向，再决定存储策略。调用被内联后，分析上下文改变，也可能消除原本预期的堆分配。

### 3.2 new、make 和取地址不是位置选择开关

```go
func calc() int {
    p := new(int)
    *p = 21
    return *p * 2
}
```

这里的值不跨调用使用，可以避免真实堆分配，甚至直接优化成计算结果。反过来，普通局部值也可能因引用被长期保存而进入堆：

```go
var saved *User

func save() {
    u := User{Name: "小明"}
    saved = &u
}
```

函数结束后，全局引用仍保留对象。GC 不会因为创建函数返回就回收它。对象大小、长度计算、调用边界等也可能影响分配，不应背一张脱离版本和上下文的“必逃逸语法清单”。

### 3.3 Java 逃逸分析不等于完整对象上栈

```java
int calc() {
    User u = new User(21);
    return u.age * 2;
}
```

HotSpot JIT 可能把字段拆成独立值，消除完整对象分配，即标量替换。不能因此宣称“最终一定在栈上放了一个完整 User”。

| 观察点             | Go                         | HotSpot                |
| ------------------ | -------------------------- | ---------------------- |
| 本文关注的优化阶段 | 编译期                     | JIT                    |
| 可能结果           | 栈分配、堆分配、优化消除等 | 分配消除、标量替换等   |
| 仅看指针或 new     | 无法断定最终机器分配       | 无法断定一定产生堆对象 |

依据：[Go FAQ](https://go.dev/doc/faq#stack_or_heap)、[HotSpot Escape Analysis](https://docs.oracle.com/en/java/javase/25/vm/java-hotspot-virtual-machine-performance-enhancements.html)。

## 四、Go 堆分配器的组织方式

### 4.1 核心结构

| 名称     | 管理内容                      | 可以怎样记                     |
| -------- | ----------------------------- | ------------------------------ |
| mspan    | 一段连续的运行时页及分配状态  | 内存管理单位，不是用户对象类型 |
| mcache   | 当前 P 用于分配的 span 缓存等 | 常见小对象本地快速路径         |
| mcentral | 按规格组织 span               | 向本地缓存补充合适 span        |
| mheap    | 全局页级堆空间                | 需要新页时向这里申请           |

G 是 goroutine，M 对应执行用的 OS 线程等资源，P 是执行 Go 代码需要的调度资源。**mcache 属于 P**，不是每个 goroutine 一个，也不能直接等同于每个 OS 线程一个。

### 4.2 小对象路径

```text
确定 size class 与扫描属性
→ 当前 P 的 mcache 中找槽位
→ 没有可用槽位时向 mcentral 获取合适 span
→ 需要页时从 mheap 取得
→ 全局空间不足时，必要地向 OS 扩展
```

这是存在提前返回的路径。mcache 已有槽位时，不需要每次走完全部层级。

size class 用固定规格组织小对象，减少管理复杂度，也可能带来内部碎片。含指针与无指针对象还会影响扫描分类；不能只凭字节数相同判断扫描成本相同。

### 4.3 大对象、tiny 与页

本文源码基线中：

- 小对象上限是 32 KiB。超过上限的对象跳过常规小对象缓存路径，采用页级分配。
- tiny 路径面向满足条件的微小无指针对象，可以将多个分配装进同一 block。小于 16 B 不能单独作为充分条件。
- 运行时页大小为 8 KiB，不是对 OS 页大小的声明。

交互页用 48 B、8 B、40 KiB 解释分支，不表示真实业务对象布局，也未测量性能收益。

源码：[malloc.go 总览](https://github.com/golang/go/blob/go1.26.0/src/runtime/malloc.go)、[mcache.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mcache.go)、[mcentral.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mcentral.go)、[mheap.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mheap.go)。

## 五、mcache 与 Java TLAB 怎样类比

TLAB（Thread Local Allocation Buffer）是 HotSpot 常见对象分配快速路径使用的线程本地分配资源，**空间仍位于 Java 堆中**。

```text
Java 堆
├─ 线程 A 当前使用的 TLAB
├─ 线程 B 当前使用的 TLAB
└─ 其它堆空间
```

常见快速路径是检查容量并推进分配指针。空间不足需要补充，或者采用其它路径。

| 维度         | Go mcache        | HotSpot TLAB     |
| ------------ | ---------------- | ---------------- |
| 归属         | P                | 线程本地分配资源 |
| 管理形式     | 按规格缓存 span  | 堆内分配缓冲空间 |
| 常见快速路径 | 对应规格的空闲槽 | 推进分配指针     |
| 对象访问范围 | 不由缓存归属决定 | 不由分配线程决定 |

“在线程 A 的 TLAB 中出生”不意味着对象只能由 A 使用。分配资源的局部性与对象的安全共享是不同问题。

依据：[HotSpot ThreadLocalAllocBuffer 源码](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.hpp)。

## 六、Go GC 怎样找到并回收垃圾

### 6.1 从根追踪对象图

```text
根 → A → B

C ↔ D
```

A、B 有根路径，继续存活。C、D 互相引用，但没有来自根的路径，可以回收。可达性追踪能够处理这种不可达环。

根包括 goroutine 栈、全局数据，以及某些运行时结构中的堆指针。栈不按普通堆对象逐个回收，但需要扫描其中的引用。

### 6.2 标记状态

交互图中白色表示尚未发现，灰色表示已发现待扫描，蓝色表示已扫描（算法中的黑色）。颜色不代表新生代、老年代或对象年龄。真实 runtime 的位图和扫描组织比三色教学图复杂。

### 6.3 一轮 GC 的主要工作

| 工作               | 并发情况         | 关注点                            |
| ------------------ | ---------------- | --------------------------------- |
| 清扫终止、标记准备 | 有 STW 协调      | 切换阶段、启用写屏障等            |
| 根扫描、标记       | 大部分与业务并发 | 扫描某个 goroutine 栈时需要暂停它 |
| 标记终止           | 有 STW 协调      | 终止判断与收尾                    |
| 清扫               | 与业务并发       | 按 span 使未标记对象空间可复用    |

分配方还可能进行 mark assist，帮助完成标记。并发 GC 仍消耗 CPU，并影响分配路径，不能理解为“后台免费回收”。

### 6.4 写屏障服务于 GC 正确性

并发标记期间，业务仍会改变对象图，算法需要处理引用变化，以免漏掉存活对象。写屏障参与这项工作，不替代业务锁、channel 和原子操作。

本文基线是非分代、非压缩的并发标记清扫。版本可以改进扫描和调度，教学图不能当作精确机器时间线。

依据：[mgc.go 算法总览](https://github.com/golang/go/blob/go1.26.0/src/runtime/mgc.go)、[mbarrier.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mbarrier.go)。

## 七、与 G1 的分代和疏散对照

### 7.1 Region 角色

G1 按 Region 管理堆，Region 可以承担 Eden、Survivor、Old 等角色，不能将它们理解为永远固定面积的三块连续物理空间。

年轻代回收复制存活对象到 Survivor，或按年龄、容量和策略等晋升 Old。原 Region 可以整体复用，不意味着每经历一次 Young GC 就必须晋升。

### 7.2 一条进入 Mixed 的代表性序列

```text
Young 疏散
→ 需要时进入 Concurrent Start（随年轻代暂停）
→ 并发根 Region 扫描与并发标记等
→ Remark
→ 清理、统计和候选选择
→ 后续 Mixed 暂停回收年轻代和选中的 Old Region
```

这里选择一条教学序列，实际可以穿插业务分配和其它年轻代回收。Mixed 不会一次收走整个 Old；Full GC 也不是每轮固定的最后一步。

### 7.3 对照边界

| 维度         | Go 标准 runtime 基线 | HotSpot G1 示例          |
| ------------ | -------------------- | ------------------------ |
| 分代         | 非分代               | 分代                     |
| 移动存活对象 | 堆 GC 保留原位       | 疏散时复制并更新引用     |
| 空间复用     | 清扫对象槽位         | 释放源 Region            |
| 并发工作     | 标记、清扫等         | 并发标记等；疏散通常 STW |
| 停顿         | 存在                 | 存在                     |

不能凭这些机制直接断定某种语言在所有业务下延迟更低、吞吐更高或更省内存。需要相同负载、资源和指标口径下的测量。

依据：[JDK 25 G1 指南](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html)。

## 八、小切片为什么可能保留大数组

### 8.1 Go slice 共享底层数组

```go
func prefix() []byte {
    buf := make([]byte, 10<<20) // 10 MiB
    return buf[:10]
}
```

结果长度为 10，但仍引用原数组，GC 不能仅凭“只用前十字节”释放剩余部分。需要独立小结果时复制：

```go
func prefixCopy() []byte {
    buf := make([]byte, 10<<20)
    // 省略填充数据的业务逻辑
    return append([]byte(nil), buf[:10]...)
}
```

即使使用 <code>buf[:10:10]</code> 限制容量，也没有切断旧数组引用，只限制后续 append 的复用范围。

### 8.2 Java ByteBuffer 的存储保留类比

```java
import java.nio.ByteBuffer;

static ByteBuffer prefixView() {
    byte[] buf = new byte[10 << 20];
    return ByteBuffer.wrap(buf, 0, 10);
}

static byte[] prefixCopy() {
    ByteBuffer view = prefixView();
    byte[] small = new byte[view.remaining()];
    view.get(small);
    return small;
}
```

wrap 后备存储仍是原数组，remaining 为 10 不意味着只保留十字节。这里只比较“视图保留底层存储”，不声称它与 Go slice 的 API 语义完全相同。

复制期间新旧存储会暂时同时存在；旧数组能否回收取决于其它有效引用。普通对象的存活边界也不等于源码变量最后一个花括号的位置。

依据：[Go Slice types](https://go.dev/ref/spec#Slice_types)、[ByteBuffer.wrap API](<https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/ByteBuffer.html#wrap(byte%5B%5D,int,int)>)。

## 九、Go Memory Model 与 Java JMM

### 9.1 Go channel 的一次发布

```go
package main

import "fmt"

func main() {
    var data int
    done := make(chan struct{})
    go func() {
        data = 42
        close(done)
    }()
    <-done
    fmt.Println(data)
}
```

顺序链：

```text
写 data → close(done) → 接收方观察到关闭 → 读 data
```

关闭与这种接收间有同步关系。本例没有发布后的并发修改，读取不必因为跨 goroutine 就一律使用原子类型。换成普通 bool 忙等，则不会继承 channel 的保证，并会产生数据竞争。Sleep 也不是安全发布。

### 9.2 Java volatile 的一次发布

```java
public class PublishDemo {
    private int data;
    private volatile boolean ready;

    void write() {
        data = 42;
        ready = true;
    }

    int read() {
        while (!ready) {
            Thread.onSpinWait();
        }
        return data;
    }
}
```

前提是实例已正确交给两个线程。volatile 写与后续相应读有 happens-before 关系，观察到这次 true 后可安全读取此前发布的 data。

onSpinWait 只是自旋提示，不会把普通字段变成 volatile。实际任务等待可以按需求使用锁、条件和 Future 等。

### 9.3 可见性不等于复合操作原子性

volatile count 的自增依然包含读、计算、写，不能保证整体原子。Go 共享整数直接自增也不会因 goroutine 调度或 GC 自动安全。

| 目标           | Go 常用机制            | Java 常用机制                |
| -------------- | ---------------------- | ---------------------------- |
| 互斥访问       | sync.Mutex             | synchronized / Lock          |
| 通知或一次发布 | channel 等协议         | volatile 配合协议、Future 等 |
| 单一原子更新   | sync/atomic 的合适操作 | AtomicInteger 等             |
| 多字段一致性   | 锁或完整并发协议       | 锁或完整并发协议             |

依据：[Go Synchronization](https://go.dev/ref/mem#Synchronization)、[JLS §17.4.5](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.5)。

## 十、诊断时分别观察哪些指标

### 10.1 Go 分析决策与实际分配

```powershell
go version
go build -gcflags="-m=2" .
go test -bench=. -benchmem -run="^$" .
```

编译器输出解释分析决策；B/op、allocs/op 反映某个 benchmark 的实际观测。需要防止结果被优化掉，并覆盖真实调用上下文。使用 all=-m=2 会增加依赖分析输出，日常先聚焦目标包。

并发路径可用 <code>go test -race ./...</code> 检测。无报告不等于穷尽所有执行，带检测的性能也不代表生产性能。

### 10.2 对象堆、页和进程内存

| 指标         | 含义                                           | 边界                 |
| ------------ | ---------------------------------------------- | -------------------- |
| HeapAlloc    | 当前分配的堆对象字节，包含尚未清扫的不可达对象 | 不等于 RSS           |
| HeapInuse    | 堆在用 span 页空间                             | 其中不一定全是活对象 |
| HeapIdle     | 闲置 span 空间                                 | 不等于已全部归还 OS  |
| HeapReleased | 归还 OS 的闲置堆物理内存口径                   | 不代表全部非堆内存   |
| StackInuse   | 栈所用 span 空间                               | 不在 HeapAlloc 中    |

字段可由 runtime.ReadMemStats 获取。持续观察可用 runtime/metrics；定位分配来源可结合 pprof 的 heap / allocs，留意采样、统计时点和存量 / 累计量。

依据：[MemStats](https://pkg.go.dev/runtime#MemStats)、[runtime/metrics](https://pkg.go.dev/runtime/metrics)、[Diagnostics](https://go.dev/doc/diagnostics)。

### 10.3 Java 堆与本地内存

GC 日志回答阶段、停顿和空间变化；堆分析回答对象保留链；Native Memory Tracking 回答 HotSpot 可追踪的本地内存分类。例如：

```powershell
java -XX:NativeMemoryTracking=summary -Xlog:gc* -jar app.jar
jcmd <pid> GC.heap_info
jcmd <pid> VM.native_memory summary
```

NMT 需要启用，不覆盖任意第三方本地代码的全部分配，不能直接当成完整 RSS 分解。具体命令需按使用中的 JDK 核对。

依据：[jcmd](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html)、[NMT](https://docs.oracle.com/en/java/javase/25/vm/native-memory-tracking.html)。

## 十一、参数与优化应怎样理解

Go 的 GOGC 控制堆增长目标，GOMEMLIMIT 提供运行时软内存限制，不等于“对象最大寿命”和“进程绝不突破的 RSS 上限”。外部内存并非都受它约束；大量仍可达数据也不能靠频繁 GC 清掉。

Java 的 -Xmx 主要限定最大 Java 堆，同样不等于进程总内存。

排查时先区分分配速度高、存活数据大、异常保留和其它内存增长，再根据热点减少多余分配、复制或引用。重点查看大数组视图、无界缓存、未结束 goroutine 和长期保留的对象图。最后结合负载调参数，测量延迟、CPU、内存和错误率。

sync.Pool 是某些临时对象复用的工具，不是具有持久性保证的业务缓存。不能为了避免进堆而无条件引入复杂池化。

依据：[runtime 环境变量](https://pkg.go.dev/runtime#hdr-Environment_Variables)、[SetMemoryLimit](https://pkg.go.dev/runtime/debug#SetMemoryLimit)、[sync.Pool](https://pkg.go.dev/sync#Pool)。

## 十二、常见误区与复习问题

| 说法                          | 更准确的表述                             |
| ----------------------------- | ---------------------------------------- |
| new 出来一定在堆              | 位置由上下文与编译器决定，分配还可能消除 |
| 返回局部值地址就是悬空指针    | Go 安排足够长的存储生命周期              |
| 每 goroutine 有一个 mcache    | mcache 属于 P                            |
| TLAB 是线程栈                 | 它是堆内分配资源                         |
| Java 逃逸分析就是完整对象上栈 | 可能是标量替换与分配消除                 |
| Go 不移动，所以栈也不移动     | 堆 GC 和 goroutine 栈机制不同            |
| 环形引用无法回收              | 无根路径的环可回收                       |
| GC 后 RSS 立即下降            | 对象复用、页归还、进程统计不同           |
| G1 每轮必然 Full GC           | Full GC 不是固定末尾                     |
| JMM 是堆栈方法区布局          | JMM 约束并发同步顺序                     |
| GC 写屏障让业务读写安全       | GC 正确性不替代业务同步                  |
| volatile 使自增整体原子       | 自增仍是复合操作                         |

用交互页自测：

1. 调用结束后，堆对象为什么仍存在？
2. 局部计算的 Java 图能否证明真的分配过对象？
3. mcache 命中时，为什么不用访问全局堆？
4. C、D 互相引用，为什么还能被回收？
5. G1 中对象身份不变但位置改变发生在哪步？
6. 通过同步点后，怎样说出完整 happens-before 链？

## 十三、官方资料与源码入口

| 资料                                                                                                                                                                                                                                            | 建议关注                 |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| [Go FAQ](https://go.dev/doc/faq#stack_or_heap)                                                                                                                                                                                                  | 变量位置与优化           |
| [Go GC Guide](https://go.dev/doc/gc-guide)                                                                                                                                                                                                      | 追踪、成本和参数取舍     |
| [Go 1.26.0 malloc.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/malloc.go)                                                                                                                                                         | 文件头总览与分配路径     |
| [mcache.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mcache.go) / [mcentral.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mcentral.go) / [mheap.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mheap.go) | 缓存、span 与页          |
| [mgc.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mgc.go) / [mbarrier.go](https://github.com/golang/go/blob/go1.26.0/src/runtime/mbarrier.go)                                                                                     | GC 阶段和屏障            |
| [Go Memory Model](https://go.dev/ref/mem)                                                                                                                                                                                                       | 同步关系                 |
| [JVMS 第 2 章](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html)                                                                                                                                                                 | 数据区与栈帧             |
| [HotSpot Performance Enhancements](https://docs.oracle.com/en/java/javase/25/vm/java-hotspot-virtual-machine-performance-enhancements.html)                                                                                                     | 逃逸分析、标量替换       |
| [TLAB 源码](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/threadLocalAllocBuffer.hpp)                                                                                                                               | 线程本地堆分配           |
| [G1 指南](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html)                                                                                                                                          | 分代、Region、标记和疏散 |
| [JLS 第 17 章](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html)                                                                                                                                                                  | JMM 与 happens-before    |

相关笔记：[结构体、方法与指针](04-结构体方法与指针.md)、[数组与切片](05-数组与切片.md)、[并发治理](13-goroutine-channel与并发治理.md)、[channel 模拟 Java 并发工具](Go-channel模拟Java并发工具.md)、[JMM](../Java与框架/JVM/JMM（Java内存模型）.md)、[JVM 数据区](../Java与框架/JVM/JVM运行时数据区.md)、[GC 垃圾回收](../Java与框架/JVM/GC垃圾回收.md)。
