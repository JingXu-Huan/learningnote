`computeIfAbsent` 是 Java `Map` 接口（从 Java 8 起）提供的一个便捷方法，用来**在 key 不存在（或已映射到 `null`）时**，用给定的函数计算并插入一个值，然后返回该值；如果 key 已有非空值，则直接返回该值并**不调用**函数。它常用于「按 key 懒创建并收集/聚合容器」的场景（例如把字符串分组到 `List` 中）。

------

### 方法签名

```java
V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)
```

### 行为要点（简洁版）

- 如果 `map` 中 **没有** `key`，或 `key` 对应的 value 是 `null`，则调用 `mappingFunction.apply(key)` 计算一个 value：
  - 如果 `mappingFunction` 返回非 `null`，将该 value 放入 `map` 并返回它。
  - 如果 返回 `null`，**不会**把映射放入 `map`，返回 `null`。
- 如果 `map` 中已有非 `null` 的 value，直接返回该 value，不会调用 `mappingFunction`。
- `mappingFunction` 应尽量**纯粹、快速、无副作用**（不要在映射函数里修改同一个 map，否则会引发难以预料的问题）。

------

### 常见用法（最常见的场景：分组）

把字符串按某个 key 分组（等价于 `map.get(key)` 为 null 时先 put 一个新 `ArrayList`）：

```java
Map<String, List<String>> map = new HashMap<>();
for (String s : strs) {
    String key = /* 通过排序或计数得到 key */;
    map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
}
```

上面等同于下面冗长但等价的写法：

```java
if (!map.containsKey(key) || map.get(key) == null) {
    map.put(key, new ArrayList<>());
}
map.get(key).add(s);
```

但 `computeIfAbsent` 更简洁、线程安全语义在 `ConcurrentHashMap` 上表现更好（在并发 map 上它能以原子方式创建/返回值）。注意：在普通 `HashMap` 上并不是线程安全的。

------

### 常见误区 / 注意事项

- `computeIfAbsent` 会在 value 为 `null` 时也触发计算 — 如果你只想读取但 **不** 想插入，请用 `get` 或 `getOrDefault`。
- `mappingFunction` 返回 `null` 时，不会放值进 map。
- 不要在 `mappingFunction` 内对同一 `map` 进行修改（可能引发不可预测的行为或死循环）。
- 若在高并发场景使用，请选用 `ConcurrentHashMap` 并理解其并发语义。

---

## 🔗 相关笔记

- [[源码的分析/总结源码😘😘🎉]] —— HashMap 源码分析（扩容、红黑树转换）
- [[lambda表达式]] —— computeIfAbsent 的第二个参数就是 Function 接口的 Lambda
- [[项目与成长/面经/美团校招Java后端一面]] —— 面试中 ConcurrentHashMap 相关考点
