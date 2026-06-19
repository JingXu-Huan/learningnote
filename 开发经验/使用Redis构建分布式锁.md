## 使用Redis构建分布式锁🎉🎉🎉

## 1.原理❤️

* `redis`的`String`数据类型：

  由于 我们使用 `SET nx` 这样的命令时：

  ​	若 key 不存在 ：建立一个这样的新 key;

  ​	若 key 存在 ：则**不会**建立一个新的 key;

  保证了锁的唯一性。

* 流程：(多线程并发分析)

  线程1 和 线程 2 同时请求：

  ​	线程 1 获取到🔒 -> 执行业务逻辑 -> 完成业务逻辑 ->释放🔒。

  ​	此时线程2 无法获取到🔒，保证了并发安全问题。

## 2.要注意的问题😢

* 死锁：

  由于`redis`并不能帮助我们管理🔒，因此这要我们自己做。

  为什么会出现死锁？

  * 业务出现宕机 导致🔒没有被正常释放 下一个线程永远无法获取到🔒 出现死锁。

* 解决方案：

  ```
  设置🔒的过期时间 TTL (时间应该稍长一些)
  ```

* 🔒的误删问题：

  ![微信图片_20250902090859_240_53](D:\24053\Pictures\微信图片_20250902090859_240_53.jpg)

若线程 1 业务阻塞时间过长：

​	导致**TTL到期**，🔒被**自动释放**。

在🔒被释放之后：

​	线程 2 来了 ， 由于此时🔒已经被释放，线程 2 会获取到分布式🔒。

​	当线程 1 执行完了它的业务逻辑 ，会去释放锁。 

​	**注意**！**线程 1 释放的是 线程 2 的**🔒！

​	现在🔒被释放了 但是线程 2 可能并没有完成它的业务逻辑。会接连导致其它线程涌入。可能破坏**数据一致性**问题。

* 解决方案：

  ```java
  给🔒加标识：
  通过UUID的唯一性给🔒加上标识,当线程要释放🔒的时候：
  	检查一下是不是自己的🔒：
  		若是 则删除。
  		否则 不能删除。
  示例代码：
  public class RedisLock implements ILock {
      private final String name;
      private final String LOCK_PREF = "lock:";
      //记录UUID和当前的线程ID
      private final String ID_PREF = UUID.randomUUID().toString(true) + "-";
      private final StringRedisTemplate stringRedisTemplate;
  
      public RedisLock(String name, StringRedisTemplate stringRedisTemplate) {
          this.name = name;
          this.stringRedisTemplate = stringRedisTemplate;
      }
  
      @Override
      public boolean tryLock(Long timeOutSec) {
          //向Redis中写入标识
          String threadId = ID_PREF + Thread.currentThread().getId();
          Boolean success = stringRedisTemplate
                  .opsForValue()
                  .setIfAbsent(LOCK_PREF + name, threadId, timeOutSec, TimeUnit.SECONDS);
          //注意空指针
          return Boolean.TRUE.equals(success);
      }
  
      @Override
      public void unLock() {
          //当前线程标识
          String threadId = ID_PREF + Thread.currentThread().getId();
          //数据库标识
          String id = stringRedisTemplate.opsForValue().get(LOCK_PREF + name);
          if (threadId.equals(id)) {
              //若两个标识一致，则释放锁
              stringRedisTemplate.delete(LOCK_PREF + name);
          }
      }
  }
  ```

---

## 🔗 相关笔记

- [[../Redis/Redisson/redisson_lock_guide]] —— 生产级 Redisson 分布式锁（可重入、看门狗续期）
- [[../Redis/数据结构]] —— String 类型的 SETNX 命令是分布式锁的基石
- [[关于使用缓存]] —— Redis 缓存模式与实战经验
- [[../面经/如何解决缓存和数据库的数据不一致性]] —— 缓存与数据库一致性方案
- [[../多线程/线程池七大核心参数]] —— 多线程并发是分布式锁要解决的核心问题  