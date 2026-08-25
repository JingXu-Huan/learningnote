# 11：net/http 与第一个 REST API

> 预计：60～90 分钟<br>
> 本章目标：把内存告警管理器升级为 HTTP 服务，先理解标准库，再考虑 Gin。

------

## 1. 请求会经过哪些层

~~~text
浏览器 / Postman
       ↓
http.Server
       ↓
Router / Middleware
       ↓
Handler
       ↓
Service / Store
~~~

先用 <code>net/http</code>，你才能理解 Gin 这类框架替你简化了哪些部分。

## 2. 添加 API 入口

在项目中新增 <code>cmd/api/main.go</code>。下面代码假定模块名仍是 <code>example.com/alert-cli</code>：

~~~go
package main

import (
    "encoding/json"
    "log"
    "net/http"
    "time"

    "example.com/alert-cli/internal/alert"
)

func main() {
    store := alert.NewStore()
    _, _ = store.Create("dorm-1-101", "high")

    mux := http.NewServeMux()
    mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
        writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
    })
    mux.HandleFunc("GET /api/v1/alerts", func(w http.ResponseWriter, r *http.Request) {
        writeJSON(w, http.StatusOK, store.List())
    })

    server := &http.Server{
        Addr:              ":8080",
        Handler:           mux,
        ReadHeaderTimeout: 5 * time.Second,
    }

    log.Println("API listening on :8080")
    log.Fatal(server.ListenAndServe())
}

func writeJSON(w http.ResponseWriter, status int, value any) {
    w.Header().Set("Content-Type", "application/json; charset=utf-8")
    w.WriteHeader(status)
    if err := json.NewEncoder(w).Encode(value); err != nil {
        log.Printf("write JSON: %v", err)
    }
}
~~~

Go 1.22 及以上支持示例中的“HTTP 方法 + 路径”路由模式。若你使用更旧 SDK，升级到当前稳定版，或暂时改为普通路径并在 Handler 内判断方法。

## 3. 启动并请求

~~~powershell
go run ./cmd/api
Invoke-RestMethod http://localhost:8080/healthz
Invoke-RestMethod http://localhost:8080/api/v1/alerts
~~~

看到 JSON 响应后，你已经把第一个 Go 小项目从 CLI 升级为 API。

当前示例只在服务启动前写入一次 Store，随后只读，因此没有并发写问题。你增加 POST、PATCH、DELETE 等会修改 Store 的接口后，HTTP 请求会并发执行；此时要给内存 Store 加 Mutex，或尽快切换到数据库 Repository，不能继续把它当成并发安全实现。

当前的 <code>/healthz</code> 只是“进程还活着”的 liveness 检查。接入 MySQL 后，如果你希望数据库不可用时返回非健康状态，需要用 <code>PingContext</code> 做 readiness 检查，例如：

~~~go
func readiness(db *sql.DB) http.HandlerFunc {
    return func(w http.ResponseWriter, r *http.Request) {
        ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
        defer cancel()

        if err := db.PingContext(ctx); err != nil {
            writeJSON(w, http.StatusServiceUnavailable, map[string]string{
                "status": "unavailable",
            })
            return
        }
        writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
    }
}
~~~

数据库连接准备好后，可以把它注册到 <code>GET /readyz</code>，或按团队约定替换原来的 <code>/healthz</code>。示例需要额外导入 <code>context</code> 与 <code>database/sql</code>。

## 4. Context 从请求开始

真实 Handler 调用业务层时，应把 <code>r.Context()</code> 传下去：

~~~go
func (h *Handler) GetAlert(w http.ResponseWriter, r *http.Request) {
    item, err := h.service.GetByID(r.Context(), 1)
    if err != nil {
        writeError(w, err)
        return
    }
    writeJSON(w, http.StatusOK, item)
}
~~~

客户端取消请求后，Context 会通知下游数据库、Redis、外部 HTTP 调用停止无效工作。下一章接数据库时会继续用到它。

## 5. Handler 的边界

Handler 负责：

- 解析路径、查询参数和 JSON 请求体。
- 调用业务方法。
- 把成功结果或业务错误转换为 HTTP 响应。

Handler 不应直接塞进复杂 SQL、事务或所有业务判断。小项目先用 Store；项目变大后拆出 Service 与 Repository。

## 6. 动手练习

1. 增加 <code>POST /api/v1/alerts</code>。
2. 用 <code>json.Decoder</code> 读取 JSON 请求体。
3. 参数不合法时返回 400，而不是 panic。
4. 增加 <code>GET /api/v1/alerts/{id}</code>。

## 7. 什么时候用 Gin

当路由、绑定、校验、中间件逐渐变多时，再学习 Gin。它简化 HTTP 层，不替代业务建模、错误处理、Context 或 SQL。官方的 [Gin REST API 教程](https://go.dev/doc/tutorial/web-service-gin) 可作为下一步参考。

下一章：[12-MySQL事务配置与日志.md](12-MySQL事务配置与日志.md)
