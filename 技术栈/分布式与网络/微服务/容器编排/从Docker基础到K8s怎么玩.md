# 从 Docker 基础到 K8s 怎么玩 😎😎😎

如果你已经有 `Docker` 基础，那么学 `Kubernetes` 最好的方式不是重新背一套新概念，而是先想清楚：

> `Docker` 解决的是“应用怎么装进容器里跑”，  
> `K8s` 解决的是“很多容器怎么稳定地、批量地、可维护地跑”。

所以，`K8s` 本质上就是一套**容器编排平台**。

------

## 目录

- [1. 先用一句话理解 K8s](#1-先用一句话理解-k8s)
- [2. 站在 Docker 视角理解 K8s](#2-站在-docker-视角理解-k8s)
- [3. K8s 最核心的几个对象](#3-k8s-最核心的几个对象)
- [4. 本地学习 K8s 怎么搭环境](#4-本地学习-k8s-怎么搭环境)
- [5. 一个 Spring Boot 服务如何上 K8s](#5-一个-spring-boot-服务如何上-k8s)
- [6. 微服务项目里 K8s 一般怎么落地](#6-微服务项目里-k8s-一般怎么落地)
- [7. 平时最常用的 kubectl 命令](#7-平时最常用的-kubectl-命令)
- [8. Docker Compose 和 K8s 的区别](#8-docker-compose-和-k8s-的区别)
- [9. 学 K8s 的推荐路线](#9-学-k8s-的推荐路线)
- [10. 面试里可以怎么讲](#10-面试里可以怎么讲)

------

## 1. 先用一句话理解 K8s

`K8s` = 帮你管理容器集群的系统。

它主要帮你做这几件事：

- 自动部署应用
- 应用挂了自动重启
- 一个服务跑多个副本做高可用
- 给服务做负载均衡
- 配置和密钥统一管理
- 滚动发布、回滚版本
- 资源隔离和调度

如果只有一个容器，`docker run` 就够了。  
如果你有很多服务、很多实例、经常发版，那就该上 `K8s` 了。

------

## 2. 站在 Docker 视角理解 K8s

你可以这样类比：

| Docker 里的概念 | K8s 里的对应理解 |
| --- | --- |
| `Dockerfile` | 镜像构建规则 |
| `docker build` | 构建镜像 |
| `docker run` | 启动一个容器 |
| 容器 `container` | `Pod` 里的一个容器 |
| `docker-compose.yml` | 多服务编排，但能力比 K8s 弱很多 |
| 端口映射 `-p 8080:8080` | `Service` / `Ingress` 暴露服务 |
| 环境变量 `-e` | `ConfigMap` / `Secret` / `env` |
| 重启策略 | `Deployment` 帮你维护副本数 |

最关键的一点：

> 在 `K8s` 里，你通常不是直接管“容器”，而是管“Pod”和“Deployment”。

------

## 3. K8s 最核心的几个对象

### 1）`Pod`

`Pod` 是 `K8s` 里最小部署单元。

一个 `Pod` 里可以放一个或多个容器，但平时最常见的是：

- 一个 `Pod`
- 一个业务容器

你可以把它理解成：

> `Pod` 才是 K8s 真正调度的“房子”，容器只是住在房子里的人。

### 2）`Deployment`

如果说 `Pod` 是房子，`Deployment` 就是物业管理员。

它负责：

- 维护副本数
- 滚动更新
- 回滚版本
- Pod 挂了重新拉起

比如你声明：

```yaml
replicas: 3
```

那么即使挂了一个，`K8s` 也会想办法再补一个出来。

### 3）`Service`

`Pod` 的 IP 会变，所以不能直接把某个 `Pod` IP 写死。

这时就需要 `Service`：

- 给一组 `Pod` 提供统一访问入口
- 自动做服务发现
- 自动做负载均衡

常见类型：

- `ClusterIP`：集群内部访问
- `NodePort`：暴露到节点端口
- `LoadBalancer`：云平台常用

### 4）`ConfigMap`

放普通配置，比如：

- `spring.profiles.active`
- `nacos` 地址
- 日志级别

这样就不用把配置写死在镜像里。

### 5）`Secret`

放敏感信息，比如：

- 数据库密码
- `Redis` 密码
- Token

### 6）`Ingress`

如果你有很多 HTTP 服务，不想每个都暴露一个端口，就可以用 `Ingress` 统一做入口转发。

比如：

- `/api/user` 转发到 `user-service`
- `/api/order` 转发到 `order-service`

这就很像网关的入口层。

### 7）`Namespace`

做资源隔离用。

比如你可以分：

- `dev`
- `test`
- `prod`

不同环境互不影响。

------

## 4. 本地学习 K8s 怎么搭环境

如果你已经有 `Docker` 基础，我更推荐用 `kind` 学习：

`kind` = `Kubernetes in Docker`

它的优点：

- 轻量
- 本地起得快
- 基于 Docker，学习成本低
- 很适合练 `kubectl` 和 YAML

### 1）先准备工具

- `Docker Desktop`
- `kubectl`
- `kind`

### 2）创建集群

```bash
kind create cluster --name learning-k8s
kubectl cluster-info --context kind-learning-k8s
kubectl get nodes
```

### 3）删除集群

```bash
kind delete cluster --name learning-k8s
```

如果你只是为了学习命令、资源对象、部署流程，`kind` 已经够用了。

------

## 5. 一个 Spring Boot 服务如何上 K8s

假设你现在有一个 Spring Boot 服务，原来是这样启动的：

```bash
docker run -d \
  --name user-service \
  -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e NACOS_ADDR=192.168.1.100:8848 \
  your-registry/user-service:1.0
```

到了 `K8s`，通常会拆成几个资源：

- `Deployment`：管副本和发布
- `Service`：提供访问入口
- `ConfigMap`：放普通配置
- `Secret`：放敏感配置

### 1）配置文件 `ConfigMap`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
  namespace: dev
data:
  SPRING_PROFILES_ACTIVE: "dev"
  NACOS_ADDR: "nacos.dev.svc.cluster.local:8848"
```

### 2）敏感配置 `Secret`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: user-service-secret
  namespace: dev
type: Opaque
stringData:
  DB_USERNAME: root
  DB_PASSWORD: 123456
```

### 3）部署应用 `Deployment`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: dev
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: your-registry/user-service:1.0
          ports:
            - containerPort: 8081
          envFrom:
            - configMapRef:
                name: user-service-config
            - secretRef:
                name: user-service-secret
          resources:
            requests:
              cpu: "200m"
              memory: "256Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            initialDelaySeconds: 15
            periodSeconds: 5
```

### 4）暴露服务 `Service`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: dev
spec:
  selector:
    app: user-service
  ports:
    - port: 8081
      targetPort: 8081
  type: ClusterIP
```

### 5）部署命令

```bash
kubectl create namespace dev
kubectl apply -f user-config.yaml
kubectl apply -f user-secret.yaml
kubectl apply -f user-deployment.yaml
kubectl apply -f user-service.yaml
```

这时候，集群内部别的服务就可以通过：

```text
http://user-service.dev.svc.cluster.local:8081
```

来访问它。

------

## 6. 微服务项目里 K8s 一般怎么落地

如果是你这种后端 / `IoT` / `Spring Cloud` 背景，K8s 的正确打开方式一般是这样的：

### 1）先把每个微服务都镜像化

每个服务都要能独立：

- 打包
- 构建镜像
- 推送镜像仓库

例如：

```bash
docker build -t your-registry/gateway-service:1.0 .
docker push your-registry/gateway-service:1.0
```

### 2）先部署基础中间件，再部署业务服务

典型顺序：

1. `MySQL`
2. `Redis`
3. `Nacos`
4. `RocketMQ` / `Kafka`
5. `Gateway`
6. `user-service`、`device-service`、`order-service` 等业务服务

但要注意：

- 学习阶段可以把中间件也放进 `K8s`
- 真正生产里，数据库这类有状态服务往往更慎重，很多公司会交给云厂商托管

### 3）把配置从镜像里剥离

不要把下面这些写死在 `application.yml` 里再打包：

- 数据库地址
- Redis 地址
- Nacos 地址
- MQ 地址
- 环境开关

而是通过：

- `ConfigMap`
- `Secret`
- 环境变量

传进去。

### 4）给服务做健康检查

对于 `Spring Boot` 服务，最好开启：

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

这样可以配合：

- `livenessProbe`
- `readinessProbe`

让 `K8s` 知道：

- 服务是不是活着
- 服务是不是已经准备好接流量

### 5）服务间调用不要写死 IP

在 `K8s` 里，服务之间调用应该通过 `Service` 名称。

例如 `OpenFeign` 里最终依赖的目标地址，不应该写某台机器 IP，而应该走：

- 服务发现
- 集群 DNS

这也是为什么 `K8s` 和微服务体系天然很搭。

### 6）发布时用滚动更新

你更新镜像版本后：

```bash
kubectl set image deployment/user-service user-service=your-registry/user-service:1.1 -n dev
```

`K8s` 会逐步替换旧 `Pod`，而不是一把全停。

如果新版本有问题，可以回滚：

```bash
kubectl rollout undo deployment/user-service -n dev
```

------

## 7. 平时最常用的 kubectl 命令

### 看资源

```bash
kubectl get pods -A
kubectl get svc -A
kubectl get deploy -A
kubectl get ns
```

### 看详情

```bash
kubectl describe pod user-service-xxxxx -n dev
kubectl describe deployment user-service -n dev
```

### 看日志

```bash
kubectl logs user-service-xxxxx -n dev
kubectl logs -f user-service-xxxxx -n dev
```

### 进入容器

```bash
kubectl exec -it user-service-xxxxx -n dev -- /bin/sh
```

### 应用 YAML

```bash
kubectl apply -f xxx.yaml
kubectl delete -f xxx.yaml
```

### 看发布状态

```bash
kubectl rollout status deployment/user-service -n dev
kubectl rollout history deployment/user-service -n dev
```

------

## 8. Docker Compose 和 K8s 的区别

很多初学者会问：

> 我都会 `docker-compose` 了，为什么还要学 `K8s`？

你可以这样理解：

### `docker-compose`

适合：

- 本地开发
- 单机环境
- 快速把一堆服务拉起来

优点：

- 简单
- 上手快
- 写起来少

缺点：

- 基本就是单机编排
- 自动恢复、扩缩容、滚动发布能力弱
- 不适合真正的大规模集群管理

### `K8s`

适合：

- 多机器
- 多服务
- 经常发版
- 要高可用
- 要自动运维能力

所以一句话总结：

> `docker-compose` 更像开发同学的本地启动器，  
> `K8s` 更像生产环境的容器操作系统。

------

## 9. 学 K8s 的推荐路线

如果你已经有 `Docker` 基础，我建议按这个顺序学：

### 第一阶段：先把核心对象搞懂

先掌握：

- `Pod`
- `Deployment`
- `Service`
- `ConfigMap`
- `Secret`
- `Ingress`
- `Namespace`

### 第二阶段：本地跑起来

用 `kind` 或 `minikube`：

- 创建集群
- 部署一个 `nginx`
- 暴露端口
- 看日志
- 删除重建

### 第三阶段：部署自己的 Java 服务

练这几个动作：

- 打包 `jar`
- 构建镜像
- 写 `Deployment`
- 写 `Service`
- 配置健康检查
- 发布新版本并回滚

### 第四阶段：再补进阶内容

后面再学：

- `StatefulSet`
- 持久卷 `PV` / `PVC`
- 自动扩缩容 `HPA`
- Helm
- Ingress Controller
- 监控：Prometheus + Grafana
- 日志：EFK / Loki

------

## 10. 面试里可以怎么讲

如果面试官问你会不会 `K8s`，你不要一上来背定义，可以这样讲：

> 我有 Docker 基础，理解镜像构建和容器运行。  
> 在这个基础上，我把 K8s 理解为容器编排平台，核心是用 Deployment 管 Pod、副本和发布，用 Service 做服务发现和负载均衡，用 ConfigMap 和 Secret 做配置管理。  
> 如果是 Spring Boot 微服务上 K8s，我会重点处理镜像化、配置外置、健康检查、服务暴露以及滚动发布。  
> 本地我会用 kind 或 minikube 去练部署、日志排查和版本回滚。

这段回答的好处是：

- 不是死背概念
- 能体现工程落地思路
- 能和你现有的 `Docker`、`Spring Cloud`、微服务经验连起来

------

## 一个最小学习闭环

如果你现在就想开始，我建议你今天只做这 4 步：

1. 安装 `kind`、`kubectl`
2. 起一个本地集群
3. 部署一个 `nginx Deployment + Service`
4. 再把你自己的一个 `Spring Boot` 服务放进去

只要你亲手把这套流程走通，`K8s` 就不是“看懂了”，而是真的“会玩了”。

------

## 🔗 相关笔记

- [[../配置中心/nacos配置中心]] —— 配置外置化思路和微服务配置管理
- [[../多个微服务之间如何相互调用/OpenFeign]] —— 微服务服务间调用
- [[../消息队列/kafka-quickstart]] —— 中间件在微服务体系中的接入
- [[项目与成长/开发经验/使用Redis构建分布式锁]] —— 微服务里常见的 Redis 场景

