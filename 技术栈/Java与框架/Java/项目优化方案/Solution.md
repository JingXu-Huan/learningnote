# 优化方案

## 关于方法接受的形参过多,不利于后期维护的Solution

* 可以将接受的方法形参封装至一个对象当中。

  这样当我们需要修改传递的参数时,不需要在控制层`Controller`和对应的业务逻辑层`Service`分别修改代码。

  只需要在实体类中更改修改即可。对外使用`Get`和`Set`方法。

  ```java
  @NoArgsConstructor
  @AllArgsConstructor
  @Data
  public class EmpQueryParam {
      private Integer page = 1;
      private Integer pageSize = 10;
      private String name;
      private Integer gender;
      @DateTimeFormat(pattern = "yyyy-MM-dd")
      private LocalDateTime begin;
      @DateTimeFormat(pattern = "yyyy-MM-dd")
      private LocalDateTime end;
  }
  ```

  

