# Ubuntu 三节点 Kubernetes 集群搭建教程

## 一、目标架构

三台 Ubuntu 主机：

| 主机        | Kubernetes 角色          | 建议主机名        | 说明                                      |
| --------- | ---------------------- | ------------ | --------------------------------------- |
| 本地 Ubuntu | Control Plane / Master | `k8s-master` | 运行 API Server、Scheduler、Controller、etcd |
| 云服务器 1    | Worker                 | `node1`      | 运行业务 Pod                                |
| 云服务器 2    | Worker                 | `node2`      | 运行业务 Pod                                |

最终网络结构：

```text
                       Tailscale 私有网络
              ┌────────────────────────────────┐
              │                                │
本地 Ubuntu    │       云服务器 node1           │
k8s-master     │       Worker                   │
100.x.x.1  ────┼────── 100.x.x.2                │
     │         │                                │
     └─────────┼────── 100.x.x.3                │
              │       云服务器 node2           │
              │       Worker                   │
              └────────────────────────────────┘

Kubernetes Node 网络：Tailscale
Pod 网络：Calico VXLAN
容器运行时：containerd
集群初始化：kubeadm
```

本教程采用：

* Kubernetes `v1.36`
* containerd
* kubeadm
* Calico `v3.32.1`
* Tailscale 组建三台机器的虚拟内网
* Calico VXLAN 封装跨节点 Pod 流量

截至 **2026 年 7 月 11 日**，Kubernetes 最新稳定分支为 `1.36`，最新补丁版本为 `1.36.2`。下面使用 `v1.36` 软件仓库，实际安装时会获取该分支的最新补丁版本。

---

# 二、为什么必须先配置 VPN

你的 Master 在本地，node1、node2 在云端，三台机器不处于同一个局域网。

如果直接使用：

* 本地 `192.168.x.x` 地址
* 云服务器公网 IP
* 路由器端口映射

会遇到以下问题：

1. 云服务器无法主动访问本地内网地址。
2. 本地公网 IP 可能变化。
3. 路由器 NAT 和运营商 CGNAT 可能阻止入站连接。
4. kubelet、Calico、API Server 需要双向通信。
5. 将 `6443`、`10250` 等 Kubernetes 管理端口直接暴露到公网不安全。

Kubernetes 要求节点之间具有完整网络连通性，可以是公网网络，也可以是私有网络。这里使用 Tailscale 为三台机器创建稳定的私有网络。

> 后续 Kubernetes 的所有节点通信都使用 Tailscale 的 `100.x.x.x` 地址，不使用云服务器公网 IP，也不使用本地的 `192.168.x.x` 地址。

---

# 三、前置条件

建议每台机器至少满足：

| 项目  |             Master |             Worker |
| --- | -----------------: | -----------------: |
| CPU |              2 核以上 |              2 核以上 |
| 内存  |            4 GB 以上 |            2 GB 以上 |
| 磁盘  |           30 GB 以上 |           30 GB 以上 |
| 系统  | Ubuntu 22.04/24.04 | Ubuntu 22.04/24.04 |
| 网络  |             可访问互联网 |             可访问互联网 |

Kubernetes 官方最低建议是每台机器至少 2 GB 内存，Control Plane 至少 2 个 CPU。

以下操作默认使用具有 `sudo` 权限的普通用户。

---

# 四、配置三台机器的主机名

## 4.1 本地 Master

```bash
sudo hostnamectl set-hostname k8s-master
```

## 4.2 云服务器 node1

```bash
sudo hostnamectl set-hostname node1
```

## 4.3 云服务器 node2

```bash
sudo hostnamectl set-hostname node2
```

重新打开终端，确认：

```bash
hostname
```

三台机器应分别输出：

```text
k8s-master
node1
node2
```

每个 Kubernetes 节点必须有唯一的主机名、MAC 地址和 `product_uuid`。

---

# 五、安装 Tailscale 私有网络

下面的操作在三台机器上都执行。

## 5.1 安装 Tailscale

```bash
curl -fsSL https://tailscale.com/install.sh | sh
```

启动并登录：

```bash
sudo tailscale up
```

终端会输出一个登录地址。在浏览器打开该地址，让三台机器登录到**同一个 Tailscale 账号或 Tailnet**。

这是 Tailscale 官方提供的 Ubuntu/Linux 安装方式。

## 5.2 查看 Tailscale IP

```bash
tailscale ip -4
```

例如得到：

| 节点         | Tailscale IP，示例 |
| ---------- | --------------- |
| k8s-master | `100.80.10.1`   |
| node1      | `100.80.10.2`   |
| node2      | `100.80.10.3`   |

记录你自己的真实地址：

```text
MASTER_IP=____100.80.218.87____________
NODE1_IP=_____100.104.188.117____________
NODE2_IP=_____100.66.122.87____________
```

也可以查看所有节点：

```bash
tailscale status
```

Tailscale 会为加入 Tailnet 的设备分配私有 IPv4 和 IPv6 地址，可通过 `tailscale ip` 和 `tailscale status` 查看。

## 5.3 测试节点互通

在 Master 上：

```bash
ping -c 4 <NODE1_IP>
ping -c 4 <NODE2_IP>
```

在 node1 上：

```bash
ping -c 4 <MASTER_IP>
ping -c 4 <NODE2_IP>
```

在 node2 上：

```bash
ping -c 4 <MASTER_IP>
ping -c 4 <NODE1_IP>
```

也可以使用 Tailscale 自带命令：

```bash
tailscale ping <对端Tailscale-IP>
```

三台机器必须全部能够相互访问。

## 5.4 处理 UFW

先检查：

```bash
sudo ufw status
```

如果输出：

```text
Status: active
```

则在三台机器上允许 Tailscale 网卡流量：

```bash
sudo ufw allow in on tailscale0
sudo ufw allow out on tailscale0
sudo ufw reload
```

这里允许的是 `tailscale0` 私网接口，不是公网接口。

## 5.5 云平台安全组

使用 Tailscale 后：

* 不需要向公网开放 Kubernetes `6443`
* 不需要向公网开放 `10250`
* 不建议向公网开放 `30000-32767`
* SSH 的 `22` 端口最好只允许你自己的 IP
* 可以允许 UDP `41641`，帮助 Tailscale 建立直连，但不是硬性要求

Kubernetes 默认使用的主要端口包括 Control Plane 的 `6443`、`2379-2380`、`10250`、`10257`、`10259`，以及 Worker 的 `10250`、`10256` 和 NodePort 范围 `30000-32767`。本方案通过私有 Tailscale 接口放行，不向公网开放。

## 5.6 防止服务器认证过期

对于长期运行的服务器，可以在 Tailscale 管理页面中为这三台可信机器关闭 Key Expiry。

关闭密钥过期会降低一定安全性，所以只应用于你自己控制的服务器。

---

# 六、三台机器执行通用环境初始化

本章所有命令都需要在：

* k8s-master
* node1
* node2

分别执行一次。

---

## 6.1 更新系统并安装工具

```bash
sudo apt-get update

sudo apt-get install -y \
  ca-certificates \
  curl \
  gpg \
  apt-transport-https \
  conntrack \
  socat \
  ipset \
  jq \
  netcat-openbsd
```

不建议在搭建过程中执行跨 Ubuntu 大版本升级。

---

## 6.2 关闭 Swap

Kubelet 默认检测到 Swap 时会拒绝启动，因此这里直接关闭 Swap。

立即关闭：

```bash
sudo swapoff -a
```

永久关闭：

```bash
sudo sed -ri '/^[^#].*\sswap\s/s/^/#/' /etc/fstab
```

检查：

```bash
swapon --show
```

没有任何输出即表示 Swap 已关闭。

也可以检查：

```bash
free -h
```

Swap 应显示为 `0B`。

---

## 6.3 加载内核模块

```bash
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF
```

立即加载：

```bash
sudo modprobe overlay
sudo modprobe br_netfilter
```

检查：

```bash
lsmod | grep -E 'overlay|br_netfilter'
```

---

## 6.4 配置内核网络参数

```bash
cat <<EOF | sudo tee /etc/sysctl.d/99-kubernetes.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF
```

加载：

```bash
sudo sysctl --system
```

检查 IP 转发：

```bash
sysctl net.ipv4.ip_forward
```

应输出：

```text
net.ipv4.ip_forward = 1
```

Kubernetes 容器网络需要启用 IPv4 转发。

---

## 6.5 检查 cgroup v2

执行：

```bash
stat -fc %T /sys/fs/cgroup
```

正常应输出：

```text
cgroup2fs
```

Kubernetes 1.36 在 Linux 上默认要求使用 cgroup v2；Ubuntu 22.04 和 Ubuntu 24.04 的默认配置通常已经满足。

如果输出不是 `cgroup2fs`，先不要继续安装。可以编辑：

```bash
sudo nano /etc/default/grub
```

在 `GRUB_CMDLINE_LINUX` 中添加：

```text
systemd.unified_cgroup_hierarchy=1
```

例如：

```text
GRUB_CMDLINE_LINUX="systemd.unified_cgroup_hierarchy=1"
```

然后执行：

```bash
sudo update-grub
sudo reboot
```

重启后重新检查。

---

# 七、安装和配置 containerd

在三台机器执行。

## 7.1 安装 containerd

```bash
sudo apt-get install -y containerd
```

创建配置目录：

```bash
sudo mkdir -p /etc/containerd
```

生成默认配置：

```bash
containerd config default |
  sudo tee /etc/containerd/config.toml >/dev/null
```

## 7.2 使用 systemd cgroup

执行：

```bash
sudo sed -i \
  's/SystemdCgroup = false/SystemdCgroup = true/' \
  /etc/containerd/config.toml
```

确认：

```bash
grep -n "SystemdCgroup" /etc/containerd/config.toml
```

应该看到：

```text
SystemdCgroup = true
```

Kubelet 与容器运行时必须使用相同的 cgroup driver；在 Ubuntu/systemd 环境中推荐两者都使用 `systemd`。

## 7.3 确认 CRI 没被禁用

检查：

```bash
grep -n "disabled_plugins" /etc/containerd/config.toml
```

如果看到：

```toml
disabled_plugins = ["cri"]
```

需要删除 `cri`：

```bash
sudo sed -i \
  's/disabled_plugins = \["cri"\]/disabled_plugins = []/' \
  /etc/containerd/config.toml
```

Kubernetes 通过 CRI 与 containerd 通信，`cri` 插件不能被禁用。

## 7.4 启动 containerd

```bash
sudo systemctl enable containerd
sudo systemctl restart containerd
```

检查：

```bash
sudo systemctl status containerd --no-pager
```

应显示：

```text
Active: active (running)
```

检查 CRI 插件：

```bash
sudo ctr plugins ls | grep cri
```

至少应该有相关插件处于 `ok` 状态。

---

# 八、安装 kubeadm、kubelet、kubectl

在三台机器执行。

## 8.1 添加 Kubernetes v1.36 软件仓库

创建密钥目录：

```bash
sudo mkdir -p -m 755 /etc/apt/keyrings
```

添加签名密钥：

```bash
curl -fsSL \
  https://pkgs.k8s.io/core:/stable:/v1.36/deb/Release.key |
  sudo gpg --dearmor --yes \
    -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
```

添加仓库：

```bash
echo \
  'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.36/deb/ /' |
  sudo tee /etc/apt/sources.list.d/kubernetes.list
```

旧的 `apt.kubernetes.io` 仓库已经废弃并冻结，当前应使用按 Kubernetes 次版本划分的 `pkgs.k8s.io` 仓库。

## 8.2 安装组件

```bash
sudo apt-get update

sudo apt-get install -y \
  kubelet \
  kubeadm \
  kubectl
```

锁定版本，防止普通系统升级意外升级 Kubernetes：

```bash
sudo apt-mark hold kubelet kubeadm kubectl
```

启动 kubelet：

```bash
sudo systemctl enable kubelet
```

在执行 `kubeadm init` 或 `kubeadm join` 之前，kubelet 可能会不断重启，因为它还没有取得集群配置。这属于正常现象。

## 8.3 查看版本

```bash
kubeadm version -o short
kubelet --version
kubectl version --client
```

三个组件的次版本应该一致，例如：

```text
v1.36.2
Kubernetes v1.36.2
Client Version: v1.36.2
```

---

# 九、保证 kubelet 在 Tailscale 后启动

三台机器执行：

```bash
sudo mkdir -p /etc/systemd/system/kubelet.service.d
```

创建 systemd 配置：

```bash
mkdir -p /etc/systemd/system/kubelet.service.d

cat > /etc/systemd/system/kubelet.service.d/20-tailscale.conf <<'EOF'
[Unit]
Wants=network-online.target tailscaled.service
After=network-online.target tailscaled.service containerd.service
EOF
```

重新加载：

```bash
sudo systemctl daemon-reload
```

这样机器重启时，kubelet 会在 Tailscale 和 containerd 之后启动。

---

# 十、规划 Pod 和 Service 网段

在三台机器上先查看已有路由：

```bash
ip -4 route
```

本教程使用：

```text
Pod CIDR:     172.20.0.0/16
Service CIDR: 172.21.0.0/16
Tailscale:    100.64.0.0/10
```

必须保证 `172.20.0.0/16` 和 `172.21.0.0/16` 没有与以下网络重叠：

* 本地局域网
* 云服务器 VPC
* Docker 网桥
* 公司 VPN
* 其他已有路由

Kubernetes 官方明确要求 Pod 网络不能与宿主机已有网络重叠。

如果运行：

```bash
ip -4 route | grep -E '172\.20|172\.21'
```

发现已有路由，应在初始化前换成其他没有冲突的网段。

---

# 十一、初始化 Master 节点

以下操作只在本地 `k8s-master` 执行。

## 11.1 获取 Master 的 Tailscale IP

```bash
MASTER_IP=$(tailscale ip -4)
echo "$MASTER_IP"
```

确认输出的是 `100.x.x.x` 地址。

设置网络变量：

```bash
POD_CIDR="172.20.0.0/16"
SERVICE_CIDR="172.21.0.0/16"
K8S_VERSION=$(kubeadm version -o short)
```

检查：

```bash
echo "Master:  $MASTER_IP"
echo "Pod:     $POD_CIDR"
echo "Service: $SERVICE_CIDR"
echo "Version: $K8S_VERSION"
```

## 11.2 创建 kubeadm 配置文件

```bash
cat <<EOF | tee "$HOME/kubeadm-init.yaml"
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration

localAPIEndpoint:
  advertiseAddress: "${MASTER_IP}"
  bindPort: 6443

nodeRegistration:
  name: k8s-master
  criSocket: unix:///run/containerd/containerd.sock
  kubeletExtraArgs:
    - name: node-ip
      value: "${MASTER_IP}"

---
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration

kubernetesVersion: "${K8S_VERSION}"
clusterName: jingxu-k8s

controlPlaneEndpoint: "${MASTER_IP}:6443"

networking:
  podSubnet: "${POD_CIDR}"
  serviceSubnet: "${SERVICE_CIDR}"
  dnsDomain: cluster.local

apiServer:
  certSANs:
    - "${MASTER_IP}"
    - "k8s-master"

---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration

cgroupDriver: systemd
EOF
```

这里显式指定：

* API Server 使用 Master 的 Tailscale IP
* kubelet 使用 Tailscale IP 作为 Node InternalIP
* containerd 为 CRI
* Pod 和 Service 独立网段
* kubelet 与 containerd 都使用 systemd cgroup

当主机有多个网络接口时，可以通过 kubeadm 的 `nodeRegistration.kubeletExtraArgs` 指定 `node-ip`，避免 kubelet错误选择公网 IP 或本地局域网 IP。

## 11.3 检查配置

```bash
cat "$HOME/kubeadm-init.yaml"
```

特别确认：

```yaml
advertiseAddress: "100.x.x.x"
controlPlaneEndpoint: "100.x.x.x:6443"
value: "100.x.x.x"
```

不能是：

```text
127.0.0.1
192.168.x.x
云服务器公网IP
```

## 11.4 预拉取镜像

```bash
sudo kubeadm config images pull \
  --config "$HOME/kubeadm-init.yaml"
```

如果成功，会下载：

* kube-apiserver
* kube-controller-manager
* kube-scheduler
* etcd
* CoreDNS
* kube-proxy
* pause

## 11.5 初始化集群

```bash
sudo kubeadm init \
  --config "$HOME/kubeadm-init.yaml"
```

初始化成功后会显示：

```text
Your Kubernetes control-plane has initialized successfully!
```

以及一条类似下面的加入命令：

```bash
kubeadm join 100.x.x.x:6443 \
  --token xxxxxx.xxxxxxxxxxxxxxxx \
  --discovery-token-ca-cert-hash sha256:xxxxxxxx
```

保存这条命令。

Kubeadm 初始化完成后会生成管理员 kubeconfig 和 Worker 加入命令。

---

# 十二、配置 kubectl

仍然只在 Master 执行。

如果当前是普通用户：

```bash
mkdir -p "$HOME/.kube"

sudo cp /etc/kubernetes/admin.conf \
  "$HOME/.kube/config"

sudo chown "$(id -u):$(id -g)" \
  "$HOME/.kube/config"
```

测试：

```bash
kubectl get nodes
```

此时可能看到：

```text
NAME         STATUS     ROLES           AGE   VERSION
k8s-master   NotReady   control-plane   1m    v1.36.x
```

`NotReady` 是因为还没有安装 CNI 网络插件。

不要把 `/etc/kubernetes/admin.conf` 分享给其他人，它具有集群管理员权限。

---

# 十三、安装 Calico 网络

这里使用 Calico Operator，并采用：

* VXLAN 全封装
* 禁用 BGP
* Node IP 从 Kubernetes `NodeInternalIP` 获取
* NodeInternalIP 已被固定为 Tailscale IP

VXLAN 更适合当前这种“本地机器 + 多台不同云服务器”的异构网络，因为底层网络不需要知道 Pod 网段的路由。

当前 Calico 最新文档版本为 `3.32`，官方安装资源使用 `v3.32.1`。

## 13.1 安装 Tigera Operator

在 Master 执行：

```bash
kubectl create -f \
  https://raw.githubusercontent.com/projectcalico/calico/v3.32.1/manifests/tigera-operator.yaml
```

等待 Installation CRD 创建：

```bash
kubectl wait \
  --for=condition=Established \
  crd/installations.operator.tigera.io \
  --timeout=180s
```

## 13.2 创建 Calico 配置

确保当前终端仍有变量：

```bash
echo "$POD_CIDR"
```

如果为空，重新设置：

```bash
POD_CIDR="172.20.0.0/16"
```

创建配置：

```bash
cat <<EOF | tee "$HOME/calico-installation.yaml"
apiVersion: operator.tigera.io/v1
kind: Installation

metadata:
  name: default

spec:
  calicoNetwork:
    bgp: Disabled

    nodeAddressAutodetectionV4:
      kubernetes: NodeInternalIP

    ipPools:
      - name: default-ipv4-ippool
        blockSize: 26
        cidr: ${POD_CIDR}
        encapsulation: VXLAN
        natOutgoing: Enabled
        nodeSelector: all()
EOF
```

安装：

```bash
kubectl create -f "$HOME/calico-installation.yaml"
```

Calico Operator 支持将地址检测方式设置为 Kubernetes `NodeInternalIP`；VXLAN 的 Operator 配置值为 `encapsulation: VXLAN`。

## 13.3 查看 Calico 状态

```bash
kubectl get pods -n calico-system -o wide
```

持续查看：

```bash
watch kubectl get pods -n calico-system
```

所有 Pod 最终应进入：

```text
Running
```

按 `Ctrl+C` 退出。

检查核心组件：

```bash
kubectl get pods -n kube-system -o wide
```

CoreDNS 应从 `Pending` 变成 `Running`。

再次查看节点：

```bash
kubectl get nodes -o wide
```

Master 应变成：

```text
NAME         STATUS   ROLES           INTERNAL-IP
k8s-master   Ready    control-plane   100.x.x.x
```

---

# 十四、让 node1 加入集群

以下操作在云服务器 `node1` 执行。

## 14.1 确认主机名

```bash
hostname
```

应输出：

```text
node1
```

## 14.2 固定 kubelet 使用 Tailscale IP

```bash
NODE_IP=$(tailscale ip -4)
echo "$NODE_IP"
```

创建 kubelet额外参数：

```bash
echo "KUBELET_EXTRA_ARGS=--node-ip=${NODE_IP}" |
  sudo tee /etc/default/kubelet
```

重新加载：

```bash
sudo systemctl daemon-reload
sudo systemctl restart kubelet
```

## 14.3 测试访问 API Server

```bash
tailscale ping <MASTER_IP>
```

测试端口：

```bash
nc -vz <MASTER_IP> 6443
```

成功应显示类似：

```text
Connection to 100.x.x.x 6443 port [tcp/*] succeeded!
```

## 14.4 执行加入命令

使用 Master 初始化时输出的命令，例如：

```bash
sudo kubeadm join <MASTER_IP>:6443 \
  --token xxxxxx.xxxxxxxxxxxxxxxx \
  --discovery-token-ca-cert-hash sha256:xxxxxxxx \
  --cri-socket unix:///run/containerd/containerd.sock
```

成功后会看到：

```text
This node has joined the cluster
```

---

# 十五、让 node2 加入集群

以下操作在云服务器 `node2` 执行。

## 15.1 固定 Node IP

```bash
NODE_IP=$(tailscale ip -4)
echo "$NODE_IP"
```

```bash
echo "KUBELET_EXTRA_ARGS=--node-ip=${NODE_IP}" |
  sudo tee /etc/default/kubelet
```

```bash
sudo systemctl daemon-reload
sudo systemctl restart kubelet
```

## 15.2 测试 Master

```bash
tailscale ping <MASTER_IP>
nc -vz <MASTER_IP> 6443
```

## 15.3 加入集群

```bash
sudo kubeadm join <MASTER_IP>:6443 \
  --token xxxxxx.xxxxxxxxxxxxxxxx \
  --discovery-token-ca-cert-hash sha256:xxxxxxxx \
  --cri-socket unix:///run/containerd/containerd.sock
```

Kubernetes 官方的 Worker 加入方式就是执行由 `kubeadm init` 生成的 `kubeadm join` 命令。默认加入令牌有效期为 24 小时。

---

# 十六、Token 过期怎么办

如果超过 24 小时，在 Master 上重新生成：

```bash
sudo kubeadm token create --print-join-command
```

它会直接输出完整的加入命令。

在 Worker 执行时追加：

```bash
--cri-socket unix:///run/containerd/containerd.sock
```

---

# 十七、检查集群状态

在 Master 执行：

```bash
kubectl get nodes -o wide
```

理想输出：

```text
NAME         STATUS   ROLES           INTERNAL-IP
k8s-master   Ready    control-plane   100.80.10.1
node1        Ready    <none>          100.80.10.2
node2        Ready    <none>          100.80.10.3
```

重点检查：

1. 三个节点都是 `Ready`
2. `INTERNAL-IP` 都是 Tailscale 的 `100.x.x.x`
3. node1、node2 不能显示公网 IP
4. node1、node2 不能显示云 VPC 内网 IP

查看系统 Pod：

```bash
kubectl get pods -A -o wide
```

所有核心 Pod 应为：

```text
Running
```

---

# 十八、给 Worker 添加角色标签

默认 Worker 的 `ROLES` 可能显示 `<none>`，这不影响调度。

可以手动添加标签：

```bash
kubectl label node node1 \
  node-role.kubernetes.io/worker=worker
```

```bash
kubectl label node node2 \
  node-role.kubernetes.io/worker=worker
```

重新查看：

```bash
kubectl get nodes
```

输出：

```text
NAME         STATUS   ROLES
k8s-master   Ready    control-plane
node1        Ready    worker
node2        Ready    worker
```

Kubernetes 限制 kubelet 在初始化时自行添加 `node-role.kubernetes.io/*` 标签，因此应在节点加入后使用管理员身份添加。

---

# 十九、部署测试应用

## 19.1 创建 Nginx Deployment

```bash
kubectl create deployment nginx-test \
  --image=nginx:alpine \
  --replicas=4
```

查看 Pod 分布：

```bash
kubectl get pods -o wide
```

因为 Master 默认带有 `NoSchedule` 污点，普通业务 Pod 应主要分布在 node1 和 node2。

## 19.2 创建 NodePort Service

```bash
kubectl expose deployment nginx-test \
  --name=nginx-test \
  --type=NodePort \
  --port=80
```

查看端口：

```bash
kubectl get svc nginx-test
```

例如：

```text
NAME         TYPE       CLUSTER-IP      PORT(S)
nginx-test   NodePort   172.21.10.100   80:31234/TCP
```

这里 NodePort 是：

```text
31234
```

## 19.3 从 Master 测试 node1

```bash
curl http://<NODE1_IP>:31234
```

## 19.4 从 Master 测试 node2

```bash
curl http://<NODE2_IP>:31234
```

如果两次都返回 Nginx HTML，说明：

* Tailscale 节点网络正常
* kube-proxy 正常
* Calico VXLAN 正常
* 跨节点 Pod 网络正常
* NodePort 正常

也可以自动获取 NodePort：

```bash
NODE_PORT=$(
  kubectl get svc nginx-test \
    -o jsonpath='{.spec.ports[0].nodePort}'
)

echo "$NODE_PORT"
```

然后执行：

```bash
curl "http://<NODE1_IP>:${NODE_PORT}"
curl "http://<NODE2_IP>:${NODE_PORT}"
```

---

# 二十、常用检查命令

## 查看节点

```bash
kubectl get nodes -o wide
```

## 查看所有 Pod

```bash
kubectl get pods -A -o wide
```

## 查看某个节点详细信息

```bash
kubectl describe node node1
```

## 查看 Calico

```bash
kubectl get pods -n calico-system -o wide
```

## 查看集群信息

```bash
kubectl cluster-info
```

## 查看 kubelet 日志

在对应节点执行：

```bash
sudo journalctl -u kubelet -n 200 --no-pager
```

实时查看：

```bash
sudo journalctl -u kubelet -f
```

## 查看 containerd

```bash
sudo systemctl status containerd --no-pager
```

## 查看 API Server 端口

Master 执行：

```bash
sudo ss -lntp | grep 6443
```

## 检查 Tailscale

```bash
tailscale status
tailscale ip -4
```

---

# 二十一、常见故障排查

## 21.1 Worker 加入时报 6443 超时

错误类似：

```text
dial tcp 100.x.x.x:6443: i/o timeout
```

Worker 执行：

```bash
tailscale ping <MASTER_IP>
nc -vz <MASTER_IP> 6443
```

Master 执行：

```bash
sudo ss -lntp | grep 6443
sudo systemctl status kubelet --no-pager
```

检查 Master 的 UFW：

```bash
sudo ufw status verbose
```

确保：

```bash
sudo ufw allow in on tailscale0
```

---

## 21.2 节点加入后 InternalIP 是公网 IP

检查：

```bash
kubectl get nodes -o wide
```

如果 node1 显示的是公网 IP，说明加入前没有正确设置：

```bash
KUBELET_EXTRA_ARGS=--node-ip=100.x.x.x
```

在错误节点执行：

```bash
sudo kubeadm reset -f
sudo rm -rf /etc/cni/net.d
```

重新设置：

```bash
NODE_IP=$(tailscale ip -4)

echo "KUBELET_EXTRA_ARGS=--node-ip=${NODE_IP}" |
  sudo tee /etc/default/kubelet
```

然后重新执行 `kubeadm join`。

---

## 21.3 节点一直 NotReady

Master 查看：

```bash
kubectl describe node node1
```

Worker 查看：

```bash
sudo journalctl -u kubelet -n 300 --no-pager
```

查看 Calico：

```bash
kubectl get pods -n calico-system -o wide
```

查看具体 Calico Pod：

```bash
kubectl logs \
  -n calico-system \
  <calico-node-pod名称> \
  --all-containers
```

重点检查：

* Tailscale 是否在线
* Node InternalIP 是否为 `100.x.x.x`
* containerd 是否正常
* Swap 是否关闭
* `net.ipv4.ip_forward` 是否为 1
* UFW 是否允许 `tailscale0`
* Pod CIDR 是否与已有网络重叠

---

## 21.4 Calico Pod Running，但跨节点访问超时

检查 VXLAN 设备：

```bash
ip link show vxlan.calico
```

检查路由：

```bash
ip route | grep 172.20
```

检查 Tailscale 网卡 MTU：

```bash
ip link show tailscale0
```

如果小请求正常、大请求经常超时，可能是双层隧道造成的 MTU 问题。可以将 Calico Pod MTU 调低到 1200：

```bash
kubectl patch installation default \
  --type merge \
  -p '{"spec":{"calicoNetwork":{"mtu":1200}}}'
```

等待 Calico 组件滚动更新：

```bash
kubectl get pods -n calico-system -w
```

VXLAN 会增加额外报文头，Overlay 网络因此会降低可用 MTU。

---

## 21.5 containerd CRI 错误

错误类似：

```text
unknown service runtime.v1.RuntimeService
```

检查：

```bash
grep disabled_plugins /etc/containerd/config.toml
```

不能包含：

```toml
"cri"
```

重新生成配置：

```bash
containerd config default |
  sudo tee /etc/containerd/config.toml >/dev/null
```

重新设置 systemd cgroup：

```bash
sudo sed -i \
  's/SystemdCgroup = false/SystemdCgroup = true/' \
  /etc/containerd/config.toml
```

重启：

```bash
sudo systemctl restart containerd
sudo systemctl restart kubelet
```

---

## 21.6 重建集群

### Worker 重置

```bash
sudo kubeadm reset -f
sudo rm -rf /etc/cni/net.d
sudo rm -rf /var/lib/cni
```

### Master 重置

```bash
sudo kubeadm reset -f
rm -rf "$HOME/.kube"
sudo rm -rf /etc/cni/net.d
sudo rm -rf /var/lib/cni
```

`kubeadm reset` 不会完整清理所有 iptables 和 IPVS 规则，彻底重建时可能还需要额外清理。

---

# 二十二、Master 节点是否运行普通业务

默认情况下，Control Plane 有污点：

```text
node-role.kubernetes.io/control-plane:NoSchedule
```

因此普通应用不会调度到 Master。

你的集群有 node1 和 node2，建议保留该污点，让 Master 只运行控制面组件。

查看污点：

```bash
kubectl describe node k8s-master | grep -i Taints
```

只有在测试资源不足时，才考虑允许 Master 运行普通 Pod：

```bash
kubectl taint nodes k8s-master \
  node-role.kubernetes.io/control-plane-
```

官方默认不在 Control Plane 调度普通工作负载。

---

# 二十三、这套架构的限制

当前架构是：

```text
1 个 Control Plane
2 个 Worker
```

它适合：

* 学习 Kubernetes
* 部署个人项目
* 测试 Java、Spring Boot、Vue 项目
* 学习 Deployment、Service、Ingress、ConfigMap
* 学习 Jenkins 和 Kubernetes 自动部署

但不属于高可用生产集群。

## Master 离线的影响

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

## 如果本地 Ubuntu 是 WSL2

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
