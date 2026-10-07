# EC2 单机 Kubernetes 与 kind 快速上手

更新时间：2026-10-07。适用于已有 Docker 的 Ubuntu 云服务器。以 2 vCPU、8 GiB 内存、60 GiB 磁盘为示例，先跑通小型 kind 集群，再练习部署、访问、扩缩容、更新、回滚和排障。

**阅读顺序：先完成第 1～8 节。Go 应用和多节点实验是后续练习，可以分两次完成。**

## 目录

- [1. 适用环境与实验目标](#1-适用环境与实验目标)
- [2. kind 与单机实验架构](#2-kind-与单机实验架构)
- [3. 连接服务器，准备 Docker 权限](#3-连接服务器准备-docker-权限)
- [4. 安装 kind 和 kubectl](#4-安装-kind-和-kubectl)
- [5. 创建单节点集群](#5-创建单节点集群)
- [6. 部署第一个应用](#6-部署第一个应用)
- [7. 从 Windows 浏览器访问](#7-从-windows-浏览器访问)
- [8. 完成四个核心实验](#8-完成四个核心实验)
- [9. 常用排障与容易踩的坑](#9-常用排障与容易踩的坑)
- [10. 下一轮：把一个 Go HTTP 服务放进集群](#10-下一轮把一个-go-http-服务放进集群)
- [11. 后续：切换到三个逻辑节点](#11-后续切换到三个逻辑节点)
- [12. 每天开机学习、收工与清理](#12-每天开机学习收工与清理)
- [13. 接下来学习什么？](#13-接下来学习什么)
- [14. 官方参考与验证范围](#14-官方参考与验证范围)

------

## 1. 适用环境与实验目标

| 项目 | 示例环境 |
|---|---|
| EC2 | `m7i-flex.large`，2 vCPU、8 GiB 内存 |
| 系统 | Ubuntu 26.04，Linux / amd64 |
| 磁盘 | 60 GiB gp3；根文件系统容量略小于磁盘容量，执行前用 `df -h /` 检查剩余空间 |
| Docker | 29.1.3，cgroup v2，已经安装并运行 |
| Go | 已安装，`/usr/local/go/bin/go` |
| 公网访问 | 使用实例自己的公网 DNS；需要地址在停止/启动后稳定时绑定弹性 IP |
| SSH 主机别名 | 本文统一使用 `go-server`，连接前按下一节配置 |
| 本机 SSH 入口 | `ssh go-server` |
| 已有数据库 | Docker 容器 `dev-postgres`，绑定服务器 `127.0.0.1:5432` |
| kind / kubectl | 按第 4 节安装，已有安装时先核对版本 |

本文第一轮实验使用独立的 `k8s-lab` 集群和命名空间。应用使用轻量网页服务，已有 PostgreSQL 继续用于原开发环境。

第一轮验收应能够解释并演示：

- 一个 Deployment 如何管理两个 Pod。
- Pod 被删除后为什么会自动补回来。
- Service 如何通过标签找到应用。
- 如何从 Windows 浏览器访问集群里的服务。
- 如何扩容、滚动更新、回滚，以及查看故障原因。

------

## 2. kind 与单机实验架构

kind 来自 **Kubernetes IN Docker**：用 Docker 容器充当 Kubernetes 节点。节点里运行真实的 Kubernetes 组件，应用通过 Kubernetes 管理。kind 负责创建集群，`kubectl` 负责操作集群。[官方介绍](https://kind.sigs.k8s.io/)

第一轮只创建一个节点，在同一节点内运行控制平面和实验应用：

```text
Windows：PowerShell、浏览器、VS Code
          │ SSH
          ▼
EC2：Ubuntu + Docker，2 vCPU / 8 GiB
  ├─ dev-postgres：已有开发数据库
  └─ k8s-lab-control-plane：kind 节点容器
       ├─ Kubernetes 控制平面
       ├─ demo Pod A
       └─ demo Pod B
```

后续可以创建一个控制平面、两个工作节点。三个节点共享同一台 EC2 的资源，适合练调度；EC2 整机故障时三个节点会一起受影响，不能据此验证跨机器高可用。

------

## 3. 连接服务器，准备 Docker 权限

### 3.1 在 Windows PowerShell 执行

先在本机用户目录的 `.ssh/config` 配置主机别名。将 `YOUR_EC2_PUBLIC_DNS` 替换为 EC2 控制台显示的公网 DNS，将 `IdentityFile` 替换为本机私钥路径：

```text
Host go-server
    HostName YOUR_EC2_PUBLIC_DNS
    User ubuntu
    Port 22
    IdentityFile C:/Users/YOUR_USER/.ssh/YOUR_PRIVATE_KEY
    IdentitiesOnly yes
```

公网 DNS 是实例属性，不能直接使用其他机器的域名。若没有绑定弹性 IP，停止再启动后需要重新核对公网地址。已配置代理的 SSH 环境可保留自己的 `ProxyCommand`。

下面的开关机命令适用于 AWS CLI v2 安装在用户目录的环境；若 `aws` 已加入 PATH，也可以直接调用 `aws`。示例 profile 为 `aws-agent`、区域为 `us-east-2`。**执行前将 `i-0123456789abcdef0` 替换为自己的实例 ID，并按实际情况替换 profile、区域及 CLI 路径。**

如果机器尚未开机，先执行下面两条命令；已经运行时可直接连接 SSH：

```powershell
& "$env:LOCALAPPDATA\Programs\Amazon\AWSCLIV2\aws.exe" ec2 start-instances --instance-ids i-0123456789abcdef0 --profile aws-agent --region us-east-2 --no-cli-pager
& "$env:LOCALAPPDATA\Programs\Amazon\AWSCLIV2\aws.exe" ec2 wait instance-running --instance-ids i-0123456789abcdef0 --profile aws-agent --region us-east-2
```

实例进入 `running` 后，系统还可能需要片刻完成启动。再连接：

```powershell
ssh go-server
```

登录成功后，命令提示符应当类似 `ubuntu@ec2-host`。

**从本节接下来的 `bash` 代码块开始，均在 SSH 登录后的 Ubuntu 终端执行。不要直接粘贴到 Windows PowerShell。** 标为 `powershell` 的代码块才在 Windows 上执行。

### 3.2 确认 Docker 正常

```bash
sudo systemctl is-active docker
sudo docker ps
```

正常情况下服务显示 `active`，容器列表中有 `dev-postgres`。

先用 `id` 确认用户组。若 `ubuntu` 不在 Docker 组，先加入，再退出这次 SSH 会话；已在组中且 `docker ps` 正常时可以跳过：

```bash
sudo usermod -aG docker ubuntu
exit
```

回到 Windows PowerShell，重新登录：

```powershell
ssh go-server
```

在新会话中验证：

```bash
id
docker ps
```

现在 `id` 的组列表应包含 `docker`，`docker ps` 无需 `sudo` 即可执行。Docker 组具有管理宿主机的高权限，这里只给自己的开发账号 `ubuntu` 使用。[Docker 权限说明](https://docs.docker.com/engine/install/linux-postinstall/)

后续 kind、kubectl 和 Docker 实验命令都以 `ubuntu` 执行，避免将 kubeconfig 写到 root 用户目录。

------

## 4. 安装 kind 和 kubectl

本文固定使用 kind `v0.33.0`、Kubernetes / kubectl `v1.37.0`。这是编写时已核对的版本组合；kind 的节点镜像还固定了 digest，方便复现。[kind 发布说明](https://github.com/kubernetes-sigs/kind/releases/tag/v0.33.0)

### 4.1 准备工作目录

```bash
mkdir -p /home/ubuntu/k8s-lab/tools
cd /home/ubuntu/k8s-lab/tools
```

### 4.2 安装 kind

```bash
curl -fL --retry 3 \
  https://kind.sigs.k8s.io/dl/v0.33.0/kind-linux-amd64 \
  -o kind

sudo install -m 0755 kind /usr/local/bin/kind
kind version
```

预期输出包含 `kind v0.33.0`。下载失败时先解决网络问题，确认文件完整再执行安装。

### 4.3 安装 kubectl，并校验下载文件

```bash
curl -fL --retry 3 \
  https://dl.k8s.io/release/v1.37.0/bin/linux/amd64/kubectl \
  -o kubectl

curl -fL --retry 3 \
  https://dl.k8s.io/release/v1.37.0/bin/linux/amd64/kubectl.sha256 \
  -o kubectl.sha256

echo "$(cat kubectl.sha256)  kubectl" | sha256sum --check
```

只有校验显示 `kubectl: OK` 时，才继续：

```bash
sudo install -m 0755 kubectl /usr/local/bin/kubectl
kubectl version --client
```

预期客户端版本为 `v1.37.0`。kubectl 与集群的版本要匹配；官方支持客户端与控制平面相差至多一个次版本。[kubectl 安装说明](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/)

------

## 5. 创建单节点集群

### 5.1 写入集群配置

```bash
cd /home/ubuntu/k8s-lab

cat > kind-single.yaml <<'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  apiServerAddress: "127.0.0.1"
  apiServerPort: 16443
nodes:
  - role: control-plane
    image: kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5
EOF
```

`16443` 是集群 API 在服务器本机的端口，`127.0.0.1` 表示只在本机监听。本指南通过 SSH 操作集群，不需要给 EC2 安全组新增 API 公网入口。[kind 网络配置](https://kind.sigs.k8s.io/docs/user/configuration/)

### 5.2 创建并检查

```bash
kind create cluster --name k8s-lab --config kind-single.yaml --wait 5m
kubectl config use-context kind-k8s-lab
kubectl get nodes -o wide
kubectl get pods -A
```

第一次需要下载节点镜像，耗时取决于网络。正常情况下：

- 节点 `k8s-lab-control-plane` 为 `Ready`。
- 核心组件逐渐进入 `Running`。
- CoreDNS 等组件尚未就绪时，可以稍等后再查询。

创建前若已有同名集群，先用 `kind get clusters` 查看，不要重复创建。若报端口占用，用 `sudo ss -lntp` 查明 `16443` 被哪个进程使用。

------

## 6. 部署第一个应用

### 6.1 保存应用 YAML

以下文件包含 Namespace、Deployment 和 Service 三个对象。网页显示版本及 Pod 名字，方便观察更新和访问来源。

```bash
cd /home/ubuntu/k8s-lab

cat > web.yaml <<'EOF'
apiVersion: v1
kind: Namespace
metadata:
  name: k8s-lab
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo
  namespace: k8s-lab
spec:
  replicas: 2
  revisionHistoryLimit: 3
  selector:
    matchLabels:
      app: demo
  template:
    metadata:
      labels:
        app: demo
    spec:
      containers:
        - name: web
          image: nginx:stable-alpine
          imagePullPolicy: IfNotPresent
          env:
            - name: APP_VERSION
              value: v1
          command: ["/bin/sh", "-c"]
          args:
            - |
              printf '<h1>K8s lab %s</h1>\n<p>Pod: %s</p>\n' "$APP_VERSION" "$HOSTNAME" > /usr/share/nginx/html/index.html
              exec nginx -g 'daemon off;'
          ports:
            - name: http
              containerPort: 80
          resources:
            requests:
              cpu: 100m
              memory: 64Mi
            limits:
              cpu: 500m
              memory: 128Mi
          readinessProbe:
            httpGet:
              path: /
              port: http
            initialDelaySeconds: 2
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /
              port: http
            initialDelaySeconds: 10
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: demo
  namespace: k8s-lab
spec:
  selector:
    app: demo
  ports:
    - name: http
      port: 80
      targetPort: http
  type: ClusterIP
EOF

kubectl apply -f web.yaml
kubectl -n k8s-lab rollout status deployment/demo --timeout=180s
kubectl -n k8s-lab get deployment,pods,service -o wide
```

成功标志：Deployment 的就绪副本为 `2/2`，两个 Pod 的就绪容器均为 `1/1`。

### 6.2 先理解这几项

| 配置 | 作用 |
|---|---|
| Namespace `k8s-lab` | 将实验资源集中在一个命名空间 |
| Deployment `demo` | 声明并维持应用副本，管理更新和回滚 |
| `replicas: 2` | 期望运行两个应用 Pod |
| 标签 `app: demo` | 让 Deployment 和 Service 识别相应 Pod |
| readinessProbe | 判断实例是否可以接收 Service 流量 |
| livenessProbe | 检测应用是否失去响应，必要时重启容器 |
| requests | 调度时为容器计算资源需求 |
| limits | 限制容器使用的 CPU、内存 |
| ClusterIP Service | 提供集群内部的稳定服务入口 |

`100m` 表示 0.1 个 CPU。多个 kind 节点仍共享 EC2 的 2 vCPU，声明更多节点不会增加宿主机计算能力。

这里的 `nginx:stable-alpine` 是便于入门的公开镜像标签，日后内容可能更新。学习到发布管理时，再练习固定镜像版本和 digest。

------

## 7. 从 Windows 浏览器访问

访问链路：**Windows 的本地端口 → SSH 隧道 → EC2 本地端口 → Pod**。

需要保留两个终端窗口：一个在服务器运行 port-forward，一个在 Windows 运行 SSH 隧道。

### 7.1 终端 A：Ubuntu，保持运行

```bash
kubectl -n k8s-lab port-forward --address 127.0.0.1 service/demo 8080:80
```

显示 `Forwarding from 127.0.0.1:8080` 后，先保持窗口打开。

### 7.2 终端 B：Windows PowerShell，保持运行

```powershell
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18080:127.0.0.1:8080 go-server
```

没有额外输出通常是正常现象，进程在维持隧道。打开 Windows 浏览器访问：

[http://127.0.0.1:18080](http://127.0.0.1:18080)

应看到 `K8s lab v1` 和某个 Pod 名字。Windows 的 `18080` 与服务器的 `8080` 可以不同。

port-forward 会选中一个 Pod 建立转发，**这条访问方式本身不能验证 Service 的负载分配**。若被选中的 Pod 在更新中退出，重新执行终端 A 的 port-forward；SSH 隧道通常可以继续保留。[port-forward 说明](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/)

### 7.3 在集群内测试 Service

另开一个 SSH 会话，在 Ubuntu 执行：

```bash
kubectl -n k8s-lab exec deployment/demo -- sh -c '
for i in 1 2 3 4 5 6; do
  wget -qO- http://demo.k8s-lab.svc.cluster.local
  echo
done
'
```

这是 Pod 通过集群 DNS 访问 Service。返回内容可能来自不同 Pod，不保证严格交替。这个服务名在 Windows 普通 DNS 环境中不能直接解析。

------

## 8. 完成四个核心实验

建议保留一个 SSH 窗口观察状态，另一个窗口执行变更。

### 实验 A：删除 Pod，观察自愈

在观察窗口执行：

```bash
kubectl -n k8s-lab get pods -w
```

在操作窗口执行：

```bash
POD_NAME=$(kubectl -n k8s-lab get pods -l app=demo -o jsonpath='{.items[0].metadata.name}')
kubectl -n k8s-lab delete pod "$POD_NAME"
```

观察一个旧 Pod 消失、新 Pod 被创建，最终恢复两个副本。这里是 Deployment 下的 ReplicaSet 维持副本数，并非删除命令失败。

### 实验 B：扩缩容

```bash
kubectl -n k8s-lab scale deployment/demo --replicas=3
kubectl -n k8s-lab rollout status deployment/demo --timeout=120s
kubectl -n k8s-lab get pods -o wide

kubectl -n k8s-lab scale deployment/demo --replicas=2
```

这一操作增加或减少应用副本；EC2 仍是 2 vCPU、8 GiB。

### 实验 C：滚动更新与回滚

```bash
kubectl -n k8s-lab set env deployment/demo APP_VERSION=v2
kubectl -n k8s-lab rollout status deployment/demo --timeout=180s
kubectl -n k8s-lab rollout history deployment/demo
```

Pod 模板的环境变量发生变化，会触发新一轮发布。按第 7 节重新运行 port-forward，网页应显示 `v2`。

回滚：

```bash
kubectl -n k8s-lab rollout undo deployment/demo
kubectl -n k8s-lab rollout status deployment/demo --timeout=180s
```

网页应恢复 `v1`。发布后再次执行 `kubectl apply -f web.yaml` 会将配置恢复为文件中声明的值。逐步养成把最终配置写回 YAML 的习惯，避免命令行修改与文件不一致。

### 实验 D：制造镜像拉取错误，定位并恢复

将镜像改成一个不存在的标签：

```bash
kubectl -n k8s-lab set image deployment/demo web=nginx:no-such-tag-k8s-lab
kubectl -n k8s-lab get pods
kubectl -n k8s-lab get events --sort-by=.metadata.creationTimestamp
```

等待片刻再查看，新 Pod 通常会出现 `ErrImagePull` 或 `ImagePullBackOff`。

选出使用错误镜像的 Pod，再查看详细原因：

```bash
FAILED_POD=$(kubectl -n k8s-lab get pods -l app=demo -o jsonpath='{.items[?(@.spec.containers[0].image=="nginx:no-such-tag-k8s-lab")].metadata.name}')
kubectl -n k8s-lab describe pod "$FAILED_POD"
```

对于尚未成功启动的容器，先看 `describe` 的 Events，通常比尝试读取应用日志更有用。

恢复：

```bash
kubectl -n k8s-lab rollout undo deployment/demo
kubectl -n k8s-lab rollout status deployment/demo --timeout=180s
```

**第一轮验收：你能展示网页、维持两个副本、删除 Pod 后自动恢复、发布 v2、回滚到 v1，并解释一次镜像拉取故障。**

------

## 9. 常用排障与容易踩的坑

### 9.1 常用命令

```bash
kubectl get nodes
kubectl -n k8s-lab get pods -o wide
kubectl -n k8s-lab describe deployment demo
kubectl -n k8s-lab logs deployment/demo --tail=50
kubectl -n k8s-lab get service demo
kubectl -n k8s-lab get endpointslices -l kubernetes.io/service-name=demo
kubectl -n k8s-lab get events --sort-by=.metadata.creationTimestamp
```

`logs deployment/demo` 会选择一个 Pod；若有多个副本，排查具体故障时按实际 Pod 名字读取日志。

| 现象 | 优先查看 | 常见原因 |
|---|---|---|
| Docker permission denied | `id`、`docker ps` | Docker 组权限未生效，需重新登录 |
| kubectl 连接失败 | 当前 context、`docker ps -a` | 集群没启动、context 错误或节点容器未运行 |
| Pod Pending | Pod 的 `describe`、Events | 请求资源过大、调度限制、存储尚未满足 |
| ImagePullBackOff | Events | 镜像名字、标签、网络或授权错误 |
| CrashLoopBackOff | 日志、`logs --previous` | 应用退出、启动参数错误或探针失败 |
| Pod 正常，Service 不通 | 标签、EndpointSlice、探针 | selector 不匹配、端口不匹配或 Pod 未就绪 |
| Windows 页面打不开 | 两个转发窗口 | port-forward 或 SSH 隧道已退出 |

可以用以下命令确认正在操作哪个集群：

```bash
kubectl config current-context
```

本文应显示 `kind-k8s-lab`。

### 9.2 创建集群失败

```bash
docker ps -a --filter label=io.x-k8s.kind.cluster=k8s-lab
free -h
df -h /
kind export logs ./kind-logs --name k8s-lab
```

先根据日志定位原因。机器同时运行 Java、开发工具和数据库时，尽量先暂停自己的重型构建任务。第一次练习采用单节点，后续需要调度实验时再切到多节点。

### 9.3 localhost 指向谁？

Windows 的 `127.0.0.1` 指 Windows；EC2 的 `127.0.0.1` 指 EC2；Pod 内的 `127.0.0.1` 指这个 Pod 的网络空间。

因此，Pod 不能通过 `127.0.0.1:5432` 访问当前宿主机上的 `dev-postgres`。数据库连接练习应明确设计访问路径，或使用独立的集群内实验数据库，不能仅照抄原开发连接串。

------

## 10. 下一轮：把一个 Go HTTP 服务放进集群

本节创建独立的演示程序，目录为 `/home/ubuntu/k8s-lab/go-demo`。它返回版本与 Pod 名字，提供健康检查接口，不需要外部数据库或 Go 依赖。

### 10.1 创建并编译程序

```bash
mkdir -p /home/ubuntu/k8s-lab/go-demo
cd /home/ubuntu/k8s-lab/go-demo

cat > main.go <<'EOF'
package main

import (
    "encoding/json"
    "log"
    "net/http"
    "os"
)

func main() {
    mux := http.NewServeMux()
    mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
        w.WriteHeader(http.StatusOK)
        _, _ = w.Write([]byte("ok\n"))
    })
    mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
        hostname, _ := os.Hostname()
        w.Header().Set("Content-Type", "application/json")
        _ = json.NewEncoder(w).Encode(map[string]string{
            "message": "hello Kubernetes",
            "version": os.Getenv("APP_VERSION"),
            "pod":     hostname,
        })
    })
    log.Fatal(http.ListenAndServe(":8080", mux))
}
EOF

CGO_ENABLED=0 GOOS=linux GOARCH=amd64 /usr/local/go/bin/go build -trimpath -o app main.go

cat > Dockerfile <<'EOF'
FROM scratch
COPY app /app
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/app"]
EOF

docker build -t go-k8s-demo:v1 .
kind load docker-image go-k8s-demo:v1 --name k8s-lab
```

宿主机 Docker 的镜像不会自动出现在 kind 节点内部，因此需要 `kind load`。这里使用 `v1` 标签，配合下面的 `IfNotPresent`，避免 kubelet 尝试从公网仓库下载这个本地镜像。[kind 镜像导入说明](https://kind.sigs.k8s.io/docs/user/quick-start/#loading-an-image-into-your-cluster)

如果 Docker 的 containerd 镜像存储导致导入时报 digest 缺失，改为导出单平台镜像后导入：

```bash
docker image save --platform linux/amd64 -o go-k8s-demo-amd64.tar go-k8s-demo:v1
kind load image-archive go-k8s-demo-amd64.tar --name k8s-lab
```

这种方式只处理本次镜像，不需要修改宿主机 Docker 配置或重启已有数据库。[kind 已知问题](https://kind.sigs.k8s.io/docs/user/known-issues/#unable-to-kind-load-docker-images)

### 10.2 部署 Go 应用

```bash
cat > go-app.yaml <<'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: go-api
  namespace: k8s-lab
spec:
  replicas: 2
  selector:
    matchLabels:
      app: go-api
  template:
    metadata:
      labels:
        app: go-api
    spec:
      containers:
        - name: api
          image: go-k8s-demo:v1
          imagePullPolicy: IfNotPresent
          env:
            - name: APP_VERSION
              value: v1
          ports:
            - name: http
              containerPort: 8080
          resources:
            requests:
              cpu: 100m
              memory: 32Mi
            limits:
              cpu: 500m
              memory: 128Mi
          readinessProbe:
            httpGet:
              path: /healthz
              port: http
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /healthz
              port: http
            initialDelaySeconds: 5
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: go-api
  namespace: k8s-lab
spec:
  selector:
    app: go-api
  ports:
    - port: 80
      targetPort: http
  type: ClusterIP
EOF

kubectl apply -f go-app.yaml
kubectl -n k8s-lab rollout status deployment/go-api --timeout=180s
kubectl -n k8s-lab port-forward --address 127.0.0.1 service/go-api 8081:80
```

另开 Windows PowerShell 窗口维持隧道：

```powershell
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18081:127.0.0.1:8081 go-server
```

Windows 浏览器访问 [http://127.0.0.1:18081](http://127.0.0.1:18081)，应得到带有 `message`、`version` 和 `pod` 的 JSON。

至此可把前面的删除 Pod、扩缩容、更新和回滚实验再做一遍，把对象名 `demo` 替换为 `go-api`。

------

## 11. 后续：切换到三个逻辑节点

**完成前两轮后再做本节。** 这会删除当前 `k8s-lab` 练习集群，再用相同名字重建。先保留应用 YAML；删除节点容器会丢失节点内部的临时数据，后续如果做过 PVC 实验，也要先处理数据。

```bash
cd /home/ubuntu/k8s-lab

cat > kind-three.yaml <<'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  apiServerAddress: "127.0.0.1"
  apiServerPort: 16443
nodes:
  - role: control-plane
    image: kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5
  - role: worker
    image: kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5
  - role: worker
    image: kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5
EOF

kind delete cluster --name k8s-lab
kind create cluster --name k8s-lab --config kind-three.yaml --wait 5m
kubectl config use-context kind-k8s-lab
kubectl get nodes
kubectl apply -f web.yaml
kubectl -n k8s-lab rollout status deployment/demo --timeout=180s
kubectl -n k8s-lab get pods -o wide
```

此时通常有 `k8s-lab-control-plane`、`k8s-lab-worker`、`k8s-lab-worker2` 三个节点。应用调度在哪个节点，以 `get pods -o wide` 的实际输出为准。

练习禁止一个工作节点接收新 Pod，再驱逐该节点上的应用：

```bash
kubectl cordon k8s-lab-worker
kubectl drain k8s-lab-worker --ignore-daemonsets --delete-emptydir-data --timeout=180s
kubectl -n k8s-lab get pods -o wide
kubectl uncordon k8s-lab-worker
```

观察应用是否在可用节点重新运行。`--delete-emptydir-data` 允许丢弃被驱逐 Pod 的 emptyDir 临时数据，所以这组命令用于本文的无状态实验应用。

如果重新部署 Go 应用，还需要重新执行 `kind load docker-image go-k8s-demo:v1 --name k8s-lab`，然后应用 `go-demo/go-app.yaml`；重建集群不会自动导入旧节点中的镜像。

------

## 12. 每天开机学习、收工与清理

### 12.1 停止并重新启动 EC2 后

集群是否可用，先检查实际状态，不要反复执行 `kind create cluster`：

```bash
docker ps -a --filter label=io.x-k8s.kind.cluster=k8s-lab
kind get clusters
kubectl --context kind-k8s-lab get nodes
```

单节点容器如果是 `Exited`，可启动已有容器：

```bash
docker start k8s-lab-control-plane
kubectl --context kind-k8s-lab wait --for=condition=Ready node --all --timeout=180s
```

三个节点都停止时，对相应节点执行：

```bash
docker start k8s-lab-control-plane k8s-lab-worker k8s-lab-worker2
kubectl --context kind-k8s-lab wait --for=condition=Ready node --all --timeout=180s
```

随后查询 Pod，再重新运行第 7 节的 port-forward 和 Windows SSH 隧道。隧道进程不会跨 EC2 停机保留。

### 12.2 本次练习结束

用 `Ctrl+C` 结束观察、port-forward 与 SSH 隧道。保存 YAML 与实验笔记，然后按需停止 EC2。

在 Windows PowerShell 上停止实例：

```powershell
& "$env:LOCALAPPDATA\Programs\Amazon\AWSCLIV2\aws.exe" ec2 stop-instances --instance-ids i-0123456789abcdef0 --profile aws-agent --region us-east-2 --no-cli-pager
```

下次启动：

```powershell
& "$env:LOCALAPPDATA\Programs\Amazon\AWSCLIV2\aws.exe" ec2 start-instances --instance-ids i-0123456789abcdef0 --profile aws-agent --region us-east-2 --no-cli-pager
```

AWS CLI 需要有效的 `aws-agent` 登录会话。若提示会话过期，在 Windows PowerShell 运行下面的命令，并完成浏览器登录；之后重试开关机命令：

```powershell
& "$env:LOCALAPPDATA\Programs\Amazon\AWSCLIV2\aws.exe" login --profile aws-agent
```

已绑定的弹性 IP 可在普通停止/启动后保持地址；停机时磁盘和弹性 IP 仍有资源费用。kind 在现有机器内运行，不创建 EKS 集群或额外 EC2。[EC2 开关机说明](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Stop_Start.html)

### 12.3 清理实验资源

删除本文创建的练习集群：

```bash
kind delete cluster --name k8s-lab
```

这会删除该 kind 集群的节点容器。工作目录内的 YAML 和 Go 源文件保留，宿主机上的 `dev-postgres` 是另一个 Docker 容器。

不要为清理本实验直接运行 `docker system prune --volumes`，它可能影响已有开发环境中的其他容器和数据卷。需要回收镜像空间时，先用 `docker system df` 查看，再按具体镜像处理。

------

## 13. 接下来学习什么？

| 顺序 | 学习内容 | 小实验 |
|---|---|---|
| 1 | ConfigMap、Secret | 为 Go 服务注入配置，理解更新生效方式 |
| 2 | PVC、StorageClass、StatefulSet | 用独立实验数据库验证 Pod 重建后数据保留 |
| 3 | requests/limits、调度约束 | 解释 Pending、OOMKilled，控制 Pod 的调度位置 |
| 4 | Job、CronJob | 执行一次性任务与定时任务 |
| 5 | RBAC、ServiceAccount | 建立只能读取指定命名空间的账号 |
| 6 | Helm | 把手写 YAML 转成可参数化、可复现的部署 |
| 7 | 指标与 HPA | 指标组件就绪后观察 Pod 自动扩缩容 |
| 8 | Gateway API、Ingress | 理解 HTTP 入口与控制器的关系 |
| 9 | kubeadm、多台虚拟机 | 学节点加入、CNI、证书、升级和真实机器故障 |

kind 默认网络不应被当作所有网络策略实现的替代品。学习 NetworkPolicy 时，需要选择并验证支持该功能的网络插件。[kind 网络说明](https://kind.sigs.k8s.io/docs/user/configuration/#disable-default-cni)

先完成应用部署和故障排查，再逐个加组件。2 vCPU / 8 GiB 的机器适合小实验；完整监控套件、服务网格和大型应用可以后续分开实践。

------

## 14. 官方参考与验证范围

- [容器编排概念导读](从Docker基础到K8s怎么玩.md)
- [Ubuntu 三节点 Kubernetes 集群搭建教程](1matser2node.md)
- [Kubernetes 基础教程](https://kubernetes.io/docs/tutorials/kubernetes-basics/)
- [kind 快速开始](https://kind.sigs.k8s.io/docs/user/quick-start/)
- [kind v0.33.0 版本与节点镜像](https://github.com/kubernetes-sigs/kind/releases/tag/v0.33.0)
- [kind 配置](https://kind.sigs.k8s.io/docs/user/configuration/)
- [kind 已知问题](https://kind.sigs.k8s.io/docs/user/known-issues/)
- [kubectl Linux 安装](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/)

本文以 Ubuntu 26.04、Docker 29.1.3 和 cgroup v2 环境编写。Linux shell 与 PowerShell 示例已做语法检查，四份 YAML 已解析并检查选择器、端口及探针关系，Go 示例完成 Linux / amd64 编译验证。安装、集群运行及实验现象仍需在目标环境执行后按各节验收标准确认；上述检查不等于完整集群实测。
