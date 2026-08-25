# 10：小项目——内存告警管理器（JSON 与测试）

> 预计：60～90 分钟<br>
> 本章目标：让小项目能保存 JSON，并为核心业务写单元测试。

------

## 1. 为什么先做 JSON，再做数据库

JSON 文件能让你练到：

~~~text
序列化与反序列化
文件读写
错误处理
单元测试
~~~

它比 MySQL 少了连接、表结构、迁移和 SQL 这些额外复杂度，适合作为第一个项目的持久化步骤。

## 2. 保存与读取告警

创建 <code>internal/alert/persistence.go</code>：

~~~go
package alert

import (
    "encoding/json"
    "errors"
    "fmt"
    "os"
)

func Save(path string, alerts []Alert) error {
    data, err := json.MarshalIndent(alerts, "", "  ")
    if err != nil {
        return fmt.Errorf("encode alerts: %w", err)
    }
    if err := os.WriteFile(path, data, 0644); err != nil {
        return fmt.Errorf("write alerts: %w", err)
    }
    return nil
}

func Load(path string) ([]Alert, error) {
    data, err := os.ReadFile(path)
    if err != nil {
        return nil, fmt.Errorf("read alerts: %w", err)
    }

    var alerts []Alert
    if err := json.Unmarshal(data, &alerts); err != nil {
        return nil, fmt.Errorf("decode alerts: %w", err)
    }
    return alerts, nil
}

func LoadStore(path string) (*Store, error) {
    alerts, err := Load(path)
    if err != nil {
        return nil, err
    }
    return NewStoreFromAlerts(alerts), nil
}

func LoadOrNewStore(path string) (*Store, error) {
    store, err := LoadStore(path)
    if errors.Is(err, os.ErrNotExist) {
        return NewStore(), nil
    }
    if err != nil {
        return nil, err
    }
    return store, nil
}
~~~

入口程序应先加载，再按需修改、最后保存：

把 <code>cmd/alert-cli/main.go</code> 替换为下面完整版本：

~~~go
package main

import (
    "log"

    "example.com/alert-cli/internal/alert"
)

func main() {
    if err := run(); err != nil {
        log.Fatal(err)
    }
}

func run() error {
    store, err := alert.LoadOrNewStore("alerts.json")
    if err != nil {
        return err
    }

    // 在这里创建、处理或删除告警。

    if err := alert.Save("alerts.json", store.List()); err != nil {
        return err
    }
    return nil
}
~~~

<code>LoadOrNewStore</code> 会在文件第一次不存在时返回空 Store；已有文件时恢复数据，并把下一条 ID 设为已存在的最大 ID。这样重启后再创建告警不会重复 ID，也不会先用空数据覆盖旧文件。

## 3. 第一个单元测试

创建 <code>internal/alert/store_test.go</code>：

~~~go
package alert

import (
    "errors"
    "testing"
)

func TestStoreResolve(t *testing.T) {
    store := NewStore()
    item, err := store.Create("dorm-1-101", "high")
    if err != nil {
        t.Fatalf("Create() error = %v", err)
    }

    resolved, err := store.Resolve(item.ID)
    if err != nil {
        t.Fatalf("Resolve() error = %v", err)
    }
    if resolved.Status != StatusResolved {
        t.Fatalf("status = %s, want %s", resolved.Status, StatusResolved)
    }

    _, err = store.Resolve(item.ID)
    if !errors.Is(err, ErrAlreadyResolved) {
        t.Fatalf("second Resolve() error = %v, want ErrAlreadyResolved", err)
    }
}
~~~

执行：

~~~powershell
go fmt ./...
go test ./...
go vet ./...
~~~

## 4. 表驱动测试

当测试案例变多时，用表驱动形式组织：

~~~go
tests := []struct {
    name     string
    deviceID string
    level    string
    wantErr  bool
}{
    {name: "valid", deviceID: "dorm-1-101", level: "high"},
    {name: "empty device", deviceID: "", level: "high", wantErr: true},
}
~~~

每一行是一组输入和预期，适合测试参数校验、状态转换和边界情况。

## 5. 本项目阶段验收

- [ ] <code>go run ./cmd/alert-cli</code> 能创建并处理告警。
- [ ] 生成 <code>alerts.json</code>，重启后可读取。
- [ ] <code>go test ./...</code> 通过。
- [ ] 能解释为什么错误要使用 <code>errors.Is</code> 判断。

完成本章后，你已经完成了“语法 → 数据结构 → 简单小项目”的第一轮闭环。后面章节会把同一个项目升级为 HTTP 服务、数据库和并发通知。

下一章：[11-net-http与第一个REST-API.md](11-net-http与第一个REST-API.md)
