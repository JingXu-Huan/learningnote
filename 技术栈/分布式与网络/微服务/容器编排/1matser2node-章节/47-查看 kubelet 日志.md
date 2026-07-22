# 查看 kubelet 日志

在对应节点执行：

```bash
sudo journalctl -u kubelet -n 200 --no-pager
```

实时查看：

```bash
sudo journalctl -u kubelet -f
```

