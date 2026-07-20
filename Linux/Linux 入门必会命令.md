# Linux 入门必会命令

Linux 命令通常遵循： command [options] [arguments]。例如 ls -lah /var/log 表示以详细、显示隐藏文件、可读大小的形式查看目录。

> 注意：rm、mv、chmod、chown、sudo 等命令可能影响系统或删除数据，执行前先确认路径和权限。

------

## 一、命令帮助与终端基础

```bash
man ls                 # 查看命令手册
ls --help              # 查看简要帮助
whatis ls              # 查看一句话说明
apropos network        # 按关键词搜索手册页
which python           # 查找可执行文件路径
command -v python      # 更通用的查找方式
type cd                # 查看命令类型
alias                  # 查看当前别名
clear                  # 清屏
history                # 查看历史命令
!!                     # 执行上一条命令
exit                   # 退出当前 Shell
```

man 或 less 中：Space 向下翻页，b 向上翻页，/keyword 搜索，n 查找下一个，q 退出。

| 快捷键 | 作用 |
| --- | --- |
| Ctrl + C | 中断当前命令 |
| Ctrl + Z | 暂停当前进程 |
| Ctrl + D | 输入结束或退出 Shell |
| Ctrl + L | 清屏 |
| Ctrl + A / Ctrl + E | 移动到行首 / 行尾 |
| Ctrl + R | 反向搜索历史命令 |
| Tab | 自动补全 |

------

## 二、路径和目录操作

路径符号：/ 是根目录，. 是当前目录，.. 是上一级目录，~ 是当前用户家目录，- 是上一次所在目录。

```bash
pwd                     # 显示当前工作目录
pwd -P                  # 显示解析软链接后的物理路径
cd /etc                 # 进入绝对路径
cd ..                   # 返回上一级
cd ~                    # 回到家目录
cd -                    # 回到上一次目录
mkdir logs              # 创建目录
mkdir -p a/b/c          # 连同父目录一起创建
mkdir -m 750 private    # 创建时指定权限
ls                      # 列出文件和目录
ls -l                   # 详细列表
ls -a                   # 包含隐藏文件
ls -lah                 # 详细显示全部文件
ls -lt                  # 按修改时间排序
ls -lS                  # 按文件大小排序
```

------

## 三、文件和链接操作

```bash
touch app.log                    # 创建空文件；存在时更新时间
file app.log                     # 判断文件类型
stat app.log                     # 查看文件详细状态
cp a.txt b.txt                   # 复制文件
cp -i a.txt b.txt                # 覆盖前询问
cp -r project backup              # 递归复制目录
cp -a project backup              # 尽量保留权限和时间
mv old.txt new.txt               # 重命名文件
mv app.log logs/                 # 移动文件
mv -i a.txt b.txt                # 覆盖前询问
rm file.txt                      # 删除文件
rm -i file.txt                   # 删除前询问
rm -r old_dir                    # 递归删除目录
rm -ri old_dir                   # 递归删除前逐项询问
ln -s /opt/app/current app       # 创建软链接
readlink -f app                  # 查看软链接最终路径
```

删除前先确认目标：

```bash
pwd
ls -la ./old_dir
```

不要对不明确的路径使用 rm -rf，尤其不要把 /、~ 或未知变量作为目标。

------

## 四、查看和处理文本

```bash
cat file.txt                    # 一次性输出全文
cat -n file.txt                 # 显示行号
less file.txt                   # 分页查看大文件
head -n 20 file.txt             # 查看前 20 行
tail -n 50 file.txt             # 查看末尾 50 行
tail -f app.log                 # 持续追踪日志
wc -l file.txt                  # 统计行数
sort -u names.txt               # 排序并去重
cut -d ':' -f 1 /etc/passwd     # 按冒号分隔取第 1 列
tr 'a-z' 'A-Z' < file.txt       # 转换大小写
tee result.txt                  # 同时输出到屏幕和文件
```

退出 less 使用 q，结束 tail -f 使用 Ctrl + C。

### 管道和重定向

```bash
command > out.txt               # 覆盖写入标准输出
command >> out.txt              # 追加写入标准输出
command 2> error.log            # 重定向错误输出
command > all.log 2>&1          # 标准输出和错误输出都写入文件
command1 | command2             # 管道
ps aux | grep '[j]ava'
grep -i error app.log | tail -n 50
```

### grep、find、sed、awk

```bash
grep 'error' app.log                         # 搜索文本
grep -in 'error' app.log                    # 忽略大小写并显示行号
grep -r 'TODO' src/                          # 递归搜索
grep -v 'debug' app.log                     # 排除匹配行
find . -name '*.log'                         # 查找日志文件
find . -type f -size +100M                   # 查找大文件
find . -mtime -1                             # 查找最近一天修改的文件
find . -type f -name '*.log' -exec grep -l 'ERROR' {} \;
sed -n '1,20p' file.txt                     # 查看指定行
sed -i.bak 's/old/new/g' file.txt           # 替换并生成备份
awk '{print $1}' file.txt                   # 输出第 1 列
awk -F ':' '{print $1}' /etc/passwd         # 指定分隔符
```

批量删除前先查看结果，确认无误后再使用 find ... -delete。

------

## 五、权限、用户和身份

```bash
whoami                          # 当前用户名
id                              # UID、组和附加组
who                             # 当前登录用户
groups                          # 所属用户组
sudo command                    # 以管理员身份执行一条命令
sudo -i                         # 进入 root Shell
su - username                   # 切换用户
sudo -u www-data command        # 以指定用户运行命令
ls -l file.txt                  # 查看权限和所有者
chmod u+x script.sh             # 增加执行权限
chmod 644 file.txt              # 普通文件常用权限
chmod 755 script.sh              # 脚本常用权限
chown user:group file.txt       # 修改所有者和所属组
chown -R user:group project/    # 递归修改，谨慎使用
umask                           # 查看默认权限掩码
```

权限数字：r = 4（读）、w = 2（写）、x = 1（执行）。755 表示所有者 rwx，其他用户 r-x。

------

## 六、进程、任务和资源

```bash
ps aux                          # 所有用户的详细进程
ps -ef                          # 完整格式查看进程
pgrep -af java                  # 按名称查找进程
top                             # 实时查看进程和资源
htop                            # 更友好的 top，需安装
command &                       # 后台启动
jobs                            # 查看当前 Shell 后台任务
fg %1                           # 切回前台
bg %1                           # 后台继续
nohup command > app.log 2>&1 &  # 退出终端后仍运行
kill PID                        # 请求进程正常退出
kill -15 PID                    # 发送 TERM 信号
kill -9 PID                     # 强制结束，最后使用
free -h                         # 查看内存和 Swap
uptime                          # 查看运行时间和系统负载
nproc                           # CPU 逻辑核心数
lsof -i :8080                   # 查看端口占用进程
lsof /var/log/app.log           # 查看使用文件的进程
```

先用 ps 或 pgrep 确认 PID，避免误杀其他服务。

------

## 七、磁盘和文件系统

```bash
df -h                           # 查看文件系统剩余空间
df -T                           # 同时显示文件系统类型
du -sh .                        # 当前目录总大小
du -sh *                        # 当前目录各项大小
du -h --max-depth=1 /var        # 查看 /var 一级目录大小
lsblk                           # 查看磁盘和分区
mount                           # 查看已挂载文件系统
findmnt                         # 查看挂载点
```

排查空间不足：

```bash
df -h
du -xhd1 /var | sort -h
find /var/log -type f -size +100M -ls
```

------

## 八、网络命令

```bash
ip addr                         # 查看网卡和 IP
ip link                         # 查看网卡状态
ip route                        # 查看路由表
hostname                        # 查看主机名
hostname -I                     # 查看本机 IP
ping -c 4 8.8.8.8               # 测试 IP 连通性
ping -c 4 example.com            # 测试 DNS 和网络
ss -lntp                        # 查看监听 TCP 端口和进程
ss -lunp                        # 查看监听 UDP 端口和进程
ss -ant                         # 查看全部 TCP 连接
nc -zv 127.0.0.1 8080           # 测试端口
getent hosts example.com        # 使用系统解析器查询 DNS
curl https://example.com        # 发起 GET 请求
curl -I https://example.com     # 只查看响应头
wget -c https://example.com/app.tar.gz # 断点续传
ssh user@server                 # 远程登录
scp app.jar user@server:/opt/    # 上传文件
scp user@server:/var/log/app.log . # 下载文件
rsync -avz project/ user@server:/opt/project/ # 同步目录
```

------

## 九、压缩和归档

```bash
tar -cvf app.tar app/             # 创建 tar 归档
tar -xvf app.tar                  # 解开 tar 归档
tar -czvf app.tar.gz app/         # 创建 gzip 压缩包
tar -xzvf app.tar.gz              # 解开 gzip 压缩包
tar -cjvf app.tar.bz2 app/        # 创建 bzip2 压缩包
tar -xjvf app.tar.bz2             # 解开 bzip2 压缩包
tar -tzvf app.tar.gz              # 查看压缩包内容
zip -r app.zip app/               # 压缩目录
unzip app.zip                     # 解压 zip
unzip -l app.zip                  # 查看 zip 内容
gzip app.log                      # 压缩为 .gz
gunzip app.log.gz                 # 解压 gzip
```

tar 参数：c 创建、x 解压、t 查看、v 显示过程、f 指定文件、z 使用 gzip、j 使用 bzip2。

------

## 十、环境变量和 Shell

```bash
env                              # 查看环境变量
printenv PATH                    # 查看指定变量
echo "$PATH"                     # 输出变量
export APP_ENV=prod              # 设置并导出变量
unset APP_ENV                    # 删除变量
source ~/.bashrc                 # 重新加载 Bash 配置
echo "$SHELL"                    # 查看默认 Shell
command1 && command2             # 前者成功后执行后者
command1 || command2             # 前者失败后执行后者
echo $?                          # 查看上一条命令退出码
```

常见配置文件：~/.bashrc、~/.bash_profile、~/.zshrc、/etc/profile。

------

## 十一、服务、日志和软件包

### systemd 服务

```bash
systemctl status nginx             # 查看服务状态
sudo systemctl start nginx         # 启动
sudo systemctl stop nginx          # 停止
sudo systemctl restart nginx       # 重启
sudo systemctl reload nginx        # 重新加载配置
sudo systemctl enable nginx        # 开机启动
sudo systemctl disable nginx       # 取消开机启动
journalctl -u nginx                # 查看服务日志
journalctl -u nginx -f             # 持续追踪日志
journalctl -u nginx -n 100         # 查看最近 100 行
journalctl -p err -b               # 查看本次启动错误
```

### Debian / Ubuntu：apt

```bash
sudo apt update                     # 更新索引
sudo apt upgrade                    # 升级软件
sudo apt install nginx              # 安装
sudo apt remove nginx               # 卸载但保留配置
sudo apt purge nginx                # 卸载并删除配置
sudo apt autoremove                 # 删除无用依赖
apt search keyword                  # 搜索软件包
apt show nginx                      # 查看信息
```

### CentOS / RHEL / Fedora：dnf

```bash
sudo dnf makecache                  # 更新缓存
sudo dnf upgrade                    # 升级软件
sudo dnf install nginx              # 安装
sudo dnf remove nginx               # 卸载
dnf search keyword                  # 搜索
dnf info nginx                      # 查看信息
```

------

## 十二、定时任务和常见排障

```bash
date                                # 查看时间
date '+%Y-%m-%d %H:%M:%S'            # 格式化时间
timedatectl                         # 查看时间和时区
crontab -e                          # 编辑当前用户定时任务
crontab -l                          # 查看定时任务
```

Cron 格式是：分钟 小时 日 月 星期 command。每天凌晨 2:30 备份：

```cron
30 2 * * * /opt/scripts/backup.sh >> /var/log/backup.log 2>&1
```

查端口和服务：

```bash
ss -lntp | grep ':8080'
lsof -i :8080
systemctl status myapp --no-pager
journalctl -u myapp -n 100 --no-pager
```

查日志和磁盘：

```bash
grep -iEn 'error|exception|failed' app.log | tail -n 100
df -h
du -xhd1 /var | sort -h
find /var/log -type f -size +100M -ls
```

区分网络、DNS、端口问题：

```bash
ping -c 4 8.8.8.8
getent hosts example.com
nc -zv example.com 443
curl -I https://example.com
```

------

## 十三、入门记忆清单

```text
pwd / ls / cd                         路径和目录
mkdir / touch / cp / mv / rm          文件操作
cat / less / head / tail              查看文件
grep / find / sort / wc               搜索和文本处理
chmod / chown / sudo                  权限
ps / top / kill / jobs                进程
df / du / free                        资源和磁盘
ip / ss / ping / curl / ssh           网络
tar / zip / unzip                     压缩
systemctl / journalctl                服务和日志
apt / dnf                             软件包
crontab                               定时任务
man / --help                          查帮助
```

遇到不会的命令，优先查看帮助：

```bash
man command
command --help
```

