# 06：Map、字符串与 rune

> 预计：45～60 分钟<br>
> 本章目标：用 map 快速查数据，正确处理中文字符串。

------

> 本章的 <code>CountByLevel</code> 示例沿用第 04 章的 Alert 定义；其余 map、string、rune 例子可以独立运行。

## 1. map：Go 的键值对

~~~go
levels := make(map[string]string)
levels["high"] = "高优先级"
levels["medium"] = "中优先级"

name, ok := levels["high"]
if ok {
    fmt.Println(name)
}
~~~

<code>value, ok := map[key]</code> 是 Go 中判断键是否存在的常见写法。

| Java | Go |
| --- | --- |
| Map&lt;Long, Alert&gt; | map[int64]Alert |
| map.get(key) | value, ok := map[key] |
| map.containsKey(key) | ok |
| map.remove(key) | delete(map, key) |

## 2. nil map 不能直接写入

~~~go
var alerts map[int64]Alert
// alerts[1] = Alert{} // 会 panic

alerts = make(map[int64]Alert)
alerts[1] = Alert{ID: 1}
~~~

nil slice 可以 append，nil map 却不能赋值。这是初学 Go 最常见的错误之一。

## 3. map 遍历没有稳定顺序

~~~go
for id, alert := range alerts {
    fmt.Println(id, alert.Level)
}
~~~

不要把 map 的遍历顺序当作业务排序。需要稳定顺序时，把数据放到 slice，再明确排序。

## 4. string、byte 与 rune

Go 的 string 保存的是字节序列。英文通常一个字符一个字节；中文 UTF-8 字符可能占多个字节。

~~~go
package main

import (
    "fmt"
    "unicode/utf8"
)

func main() {
    text := "Go语言"

    fmt.Println(len(text))                // 字节长度
    fmt.Println(utf8.RuneCountInString(text)) // 字符数量

    for _, r := range text {
        fmt.Printf("%c ", r)
    }
}
~~~

处理中文的“第几个字符”时，先考虑 rune。不要直接把字节下标当作中文字符下标。

## 5. 一个实用函数：统计告警级别

~~~go
func CountByLevel(alerts []Alert) map[string]int {
    result := make(map[string]int)
    for _, alert := range alerts {
        result[alert.Level]++
    }
    return result
}
~~~

map 中不存在的 <code>int</code> 值读取出来是零值 0，因此可以直接加一。

## 6. 动手练习

1. 用 map 保存 <code>ID → Alert</code>，实现按 ID 查询。
2. 查询不存在的 ID 时，用 <code>ok</code> 判断，而不是只看 Alert 的零值。
3. 输入一段含中文的文本，分别输出字节长度和字符数量。

## 7. 常见坑

- map 不是并发安全的；多个 goroutine 同时读写时要在后续章节中加锁或调整设计。
- map 的零值是 nil，写入前要 make。
- string 可以按字节索引，但中文业务通常应按 rune 处理。

下一章：[07-包接口与组合.md](07-包接口与组合.md)
