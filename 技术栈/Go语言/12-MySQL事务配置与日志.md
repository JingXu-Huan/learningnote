# 12：MySQL、事务、配置与日志

> 预计：90 分钟<br>
> 本章目标：把内存数据升级为 MySQL 数据，建立数据库访问的正确基本习惯。

------

## 1. 安装 MySQL 驱动

~~~powershell
go get github.com/go-sql-driver/mysql
go mod tidy
~~~

Go 中标准库 <code>database/sql</code> 负责通用数据库接口；具体 MySQL 协议由驱动实现。

## 2. 正确打开数据库

~~~go
package database

import (
    "context"
    "database/sql"
    "fmt"
    "time"

    _ "github.com/go-sql-driver/mysql"
)

func Open(dsn string) (*sql.DB, error) {
    db, err := sql.Open("mysql", dsn)
    if err != nil {
        return nil, err
    }

    db.SetMaxOpenConns(20)
    db.SetMaxIdleConns(10)
    db.SetConnMaxLifetime(3 * time.Minute)

    ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
    defer cancel()

    if err := db.PingContext(ctx); err != nil {
        _ = db.Close()
        return nil, fmt.Errorf("ping MySQL: %w", err)
    }
    return db, nil
}
~~~

<code>*sql.DB</code> 是连接池，不是一条连接。<code>sql.Open</code> 不代表已经成功连库，必须用 <code>PingContext</code> 验证。

## 3. 参数化 SQL

下面是一个完整的 Repository 查询方法。把它放在单独的 <code>internal/alert/mysql_repository.go</code> 文件中；实际项目中可以把 <code>AlertRow</code> 映射为前面业务包中的 Alert：

~~~go
package alert

import (
    "context"
    "database/sql"
    "errors"
    "fmt"
)

// 复用第 09 章 store.go 中已经定义的 ErrAlertNotFound。

type AlertRow struct {
    ID       int64
    DeviceID string
    Level    string
    Status   string
}

type Repository struct {
    db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
    return &Repository{db: db}
}

func (r *Repository) FindByID(ctx context.Context, id int64) (AlertRow, error) {
    row := r.db.QueryRowContext(
        ctx,
        "SELECT id, device_id, level, status FROM water_alert WHERE id = ?",
        id,
    )

    var item AlertRow
    if err := row.Scan(&item.ID, &item.DeviceID, &item.Level, &item.Status); err != nil {
        if errors.Is(err, sql.ErrNoRows) {
            return AlertRow{}, ErrAlertNotFound
        }
        return AlertRow{}, fmt.Errorf("find alert %d: %w", id, err)
    }
    return item, nil
}
~~~

永远使用占位符传参数，不要用字符串拼接用户输入。查询多行时，记得关闭 <code>rows</code> 并检查 <code>rows.Err()</code>。

## 4. 事务模板

~~~go
// 放在上一个 mysql_repository.go 文件中，复用其 imports。
var ErrAlertStateConflict = errors.New("alert status conflict")

func Resolve(ctx context.Context, db *sql.DB, id int64) error {
    tx, err := db.BeginTx(ctx, nil)
    if err != nil {
        return fmt.Errorf("begin transaction: %w", err)
    }
    defer func() {
        _ = tx.Rollback()
    }()

    var status string
    err = tx.QueryRowContext(
        ctx,
        "SELECT status FROM water_alert WHERE id = ? FOR UPDATE",
        id,
    ).Scan(&status)
    if errors.Is(err, sql.ErrNoRows) {
        return ErrAlertNotFound
    }
    if err != nil {
        return fmt.Errorf("lock alert: %w", err)
    }
    if status != "open" {
        return ErrAlertStateConflict
    }

    result, err := tx.ExecContext(
        ctx,
        "UPDATE water_alert SET status = ? WHERE id = ?",
        "resolved",
        id,
    )
    if err != nil {
        return fmt.Errorf("resolve alert: %w", err)
    }

    affected, err := result.RowsAffected()
    if err != nil {
        return fmt.Errorf("read affected rows: %w", err)
    }
    if affected == 0 {
        return ErrAlertNotFound
    }
    return tx.Commit()
}
~~~

事务里的所有 SQL 都必须使用同一个 <code>tx</code>。这里先用 <code>FOR UPDATE</code> 锁定并读取状态，因此可以区分“不存在”和“状态冲突”：Handler 可将 <code>ErrAlertNotFound</code> 映射为 404，把 <code>ErrAlertStateConflict</code> 映射为 409。

## 5. 环境变量和日志

~~~go
package config

import (
    "errors"
    "os"
)

type Config struct {
    HTTPAddr string
    MySQLDSN string
}

func LoadConfig() (Config, error) {
    addr := os.Getenv("HTTP_ADDR")
    if addr == "" {
        addr = ":8080"
    }

    dsn := os.Getenv("MYSQL_DSN")
    if dsn == "" {
        return Config{}, errors.New("MYSQL_DSN is required")
    }
    return Config{
        HTTPAddr: addr,
        MySQLDSN: dsn,
    }, nil
}
~~~

真实密码不能提交到 Git。提供 <code>.env.example</code> 说明变量名即可。

日志优先记录结构化字段，而不是拼大字符串：

~~~go
slog.Info("alert resolved", "alert_id", id, "request_id", requestID)
~~~

## 6. 动手练习

1. 建一张 <code>water_alert</code> 表并写 migration 文件。
2. 把内存 Store 替换为 MySQL Repository。
3. 实现按状态分页查询。
4. 用请求 Context 执行全部 SQL。

官方数据库教程：[Accessing a relational database](https://go.dev/doc/tutorial/database-access)

下一章：[13-goroutine-channel与并发治理.md](13-goroutine-channel与并发治理.md)
