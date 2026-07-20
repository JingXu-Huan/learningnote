# Java 内存动画（完整版）

请使用 [Java内存全景与GC过程动画.html](Java内存全景与GC过程动画.html)。

这个版本将三类概念分开：

- **JVM 内存全景**：按 JVMS 区分方法区、堆、PC、虚拟机栈、本地方法栈，并将 Metaspace、Code Cache、Direct Memory 放入 HotSpot 常见实现层。
- **JMM**：只讲可见性、有序性、原子性和 happens-before，不把它伪装成 JVM 物理内存布局。
- **GC**：先完整演示一轮 Young GC 的 Root 扫描、复制、晋升和 Survivor 角色交换；再单独说明 G1 的 Concurrent Start、并发标记、Remark、Cleanup 和 Mixed GC。Full GC 仅作为兜底分支。

键盘支持：左右方向键切换步骤；1 / 2 / 3 切换主题；空格播放或暂停；R 重置。
