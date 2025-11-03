```c
#include <linux/module.h>
#include <linux/fs.h>
#include <linux/uaccess.h>
#define DEV_NAME "mychardev"
static char msg[100] = "Hello from kernel!";
static int major;
// 打开设备
static int dev_open(struct inode *inode, struct file *file) {
    printk(KERN_INFO "mychardev: opened\n");
    return 0;
}
// 读操作
static ssize_t dev_read(struct file *file, char __user *buf, size_t len, loff_t *off) {
    if (copy_to_user(buf, msg, strlen(msg)))
        return -EFAULT;
    return strlen(msg);
}
// 写操作
static ssize_t dev_write(struct file *file, const char __user *buf, size_t len, loff_t *off) {
    if (len > sizeof(msg) - 1)
        len = sizeof(msg) - 1;
    if (copy_from_user(msg, buf, len))
        return -EFAULT;
    msg[len] = '\0';
    printk(KERN_INFO "mychardev: new msg = %s\n", msg);
    return len;
}
// 释放设备
static int dev_release(struct inode *inode, struct file *file) {
    printk(KERN_INFO "mychardev: closed\n");
    return 0;
}
static struct file_operations fops = {
    .owner = THIS_MODULE,
    .open = dev_open,
    .read = dev_read,
    .write = dev_write,
    .release = dev_release,
};
// 初始化
static int __init chardev_init(void) {
    major = register_chrdev(0, DEV_NAME, &fops);
    printk(KERN_INFO "mychardev: registered with major %d\n", major);
    return 0;
}
// 卸载
static void __exit chardev_exit(void) {
    unregister_chrdev(major, DEV_NAME);
    printk(KERN_INFO "mychardev: unregistered\n");
}
module_init(chardev_init);
module_exit(chardev_exit);
MODULE_LICENSE("GPL");
```

