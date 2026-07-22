# Master 离线的影响

本地 Master 关机、休眠或者断网后：

* `kubectl` 无法操作集群
* API Server 不可用
* Scheduler 不可用
* Controller Manager 不可用
* etcd 不可用
* 无法创建、删除、扩容或重新调度 Pod
* Worker 上已经运行的容器通常仍会继续运行
* 故障 Pod 无法被控制面正常重新调度

生产环境通常会把 Control Plane 部署到多台长期在线的机器上，以实现容错和高可用。

