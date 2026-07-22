# 如果本地 Ubuntu 是 WSL2

如果你的“本地 Ubuntu”实际上是 WSL2：

* Windows 关机后 Control Plane 必然停止
* Windows 休眠时集群不可管理
* WSL 自动退出可能中断 kubelet
* WSL 网络和宿主机生命周期会影响集群
* 不建议把它当作长期运行的 Master

用于学习可以，但需要保证：

```bash
systemctl is-system-running
systemctl status tailscaled
systemctl status containerd
systemctl status kubelet
```

都能正常工作。

更加稳定的方式是：

```text
本地原生 Ubuntu
或
长期运行的 Ubuntu 虚拟机
或
将 Control Plane 也迁移到云服务器
```

---

# 二十四、最终验收清单

在 Master 执行：

```bash
kubectl get nodes -o wide
```

必须满足：

```text
[ ] k8s-master 为 Ready
[ ] node1 为 Ready
[ ] node2 为 Ready
[ ] 三个 InternalIP 都是 100.x.x.x
```

检查系统 Pod：

```bash
kubectl get pods -A
```

必须满足：

```text
[ ] kube-apiserver Running
[ ] kube-controller-manager Running
[ ] kube-scheduler Running
[ ] etcd Running
[ ] coredns Running
[ ] kube-proxy Running
[ ] calico-node Running
[ ] calico-kube-controllers Running
```

检查应用：

```bash
kubectl get pods -o wide
kubectl get svc
```

必须满足：

```text
[ ] nginx Pod 分布在 node1/node2
[ ] NodePort 能通过 node1 Tailscale IP 访问
[ ] NodePort 能通过 node2 Tailscale IP 访问
```

全部满足后，这个三节点 Kubernetes 集群就已经搭建完成。
