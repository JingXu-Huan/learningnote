# 03：函数、多返回值、错误与 defer

> 预计：45～60 分钟<br>
> 本章目标：写出可复用函数，并理解 Go 为什么把 error 当作普通返回值。

------

## 1. 函数与多返回值

Go 函数把返回值类型写在参数列表之后：

~~~go
func add(a int, b int) int {
    return a + b
}

func divide(a float64, b float64) (float64, error) {
    if b == 0 {
        return 0, errors.New("divisor cannot be zero")
    }
    return a / b, nil
}
~~~

调用时同时接收结果和错误：

~~~go
result, err := divide(10, 2)
if err != nil {
    fmt.Println("计算失败：", err)
    return
}
fmt.Println(result)
~~~

这和 Java 的异常模型不同：Go 要求调用者在发生错误的位置决定如何处理，而不是把普通业务失败交给全局异常机制“兜底”。

## 2. 一个可以直接运行的例子

~~~go
package main

import (
    "errors"
    "fmt"
)

func divide(a float64, b float64) (float64, error) {
    if b == 0 {
        return 0, errors.New("除数不能为 0")
    }
    return a / b, nil
}

func main() {
    result, err := divide(10, 0)
    if err != nil {
        fmt.Println("计算失败：", err)
        return
    }
    fmt.Println("结果：", result)
}
~~~

## 3. 错误包装：补充上下文，不丢根因

当下层函数返回错误时，可以用 <code>fmt.Errorf</code> 补充“哪个操作失败了”：

~~~go
value, err := divide(10, 0)
if err != nil {
    return fmt.Errorf("计算告警阈值: %w", err)
}
~~~

<code>%w</code> 会保留原始错误，后续可用 <code>errors.Is</code> 或 <code>errors.As</code> 判断根因。初学阶段记住一句即可：**错误要有上下文，但不要重复打印同一个错误。**

## 4. defer：当前函数结束前再做清理

<code>defer</code> 常用于关闭文件、关闭数据库连接、释放锁、调用 cancel：

~~~go
package main

import "fmt"

func trace() {
    defer fmt.Println("函数结束，执行清理")
    fmt.Println("函数开始")
}

func main() {
    trace()
}
~~~

输出顺序：

~~~text
函数开始
函数结束，执行清理
~~~

多个 defer 后进先出。不要把 defer 写在一个很大的循环中，否则资源会在整个函数结束时才释放。

## 5. 作用域与 := 的阴影变量

~~~go
err := doSomething()
if err != nil {
    return err
}

if enabled {
    // 这里的 err 是内层新变量，会遮蔽外层 err。
    value, err := loadValue()
    if err != nil {
        return err
    }
    _ = value
}

// 若希望复用外层 err，应先声明 value，再用 = 赋值。
var value string
value, err = loadValue()
_ = value
~~~

同名变量在内层作用域中可能遮蔽外层变量。写业务代码时，让函数短一些、变量名更具体，能避免很多难找的问题。

补充：文件、<code>rows</code> 等短生命周期资源通常用 defer 关闭；但 <code>*sql.DB</code> 是连接池，一般在程序启动时创建、在整个程序退出时关闭，不要为每一次查询新建和关闭它。

## 6. 动手练习

1. 写一个 <code>parseLevel</code> 函数：只接受 high、medium、low，其他输入返回 error。
2. 写一个 <code>defer</code> 示例，观察两个 defer 的执行顺序。
3. 把错误用 <code>fmt.Errorf</code> 包装一次，再打印它。

## 7. 本章验收

- [ ] 能解释 <code>(value, error)</code> 的含义。
- [ ] 知道普通业务失败不应该用 panic。
- [ ] 知道 defer 常用于资源清理。

下一章：[04-结构体方法与指针.md](04-结构体方法与指针.md)
