# **Java的反射**

## 反射是什么
* Java 反射（Reflection）是一个强大的特性，它允许程序在运行时查询、访问和修改类、接口、字段和方法的信息。

## 如何使用

### 获取class对象的三种方式

* ```java
  Class.forName("全类名") //源代码阶段,全类名就是包名加类名
  ```
* ```java
  类名.class //加载阶段 使用此方法不会调用类的静态代码块
  ```
* ```java
  对象.getClass(); //运行阶段
  ```

### 获取字段的方法

* ```java
  clazz.getDeclaredFields(); //获取类的全部字段 返回Field数组,无法获得父类字段
  
  clazz.getFields(); //获取类的public字段 去掉Declared即可,可以获取父类public字段(如果有)
  
  //如果想要获得父类private字段 使用以下方法先获取父类的字节码对象,再获取所有字段
  clazz.getSuperclass().getDeclaredFields(); 
  
  //带Declared的方法用于访问一个类中声明的的所有成员,不管其权限如何
  //不带Declared的方法仅用于获得public成员,包括其父类的pubilc成员
  
  field = clazz.getDeclaredFields(); //可以指定字段,获取指定的字段的成员名
  ```

### 获取类的方法和注解

* ```java
  field.getDeclaredAnnotation(); //获取注解
  Method[] methods = clazz.getDeclaredMethods(); //获取方法
  
  Method method = MyClass.class.getMethod("sayHello");
  method.invoke(myObject);  // 调用 myObject 对象的 sayHello 方法 
  
  //invoke() 是 java.lang.reflect.Method 类中的方法，用来通过反射调用方法。
  //如果是static方法 我们使用invoke(null);
  //如需传递参数  invoke(,"在这里写args")
  //否则 需要传递实例对象myObject
  
  //如需访问private方法,需要先设置权限
  method.setAccessible(true);
  ```

  在反射中，我们通常使用类的构造器来创建类的实例。

### 使用反射创建类的实例
  * ```java
    //1.获取构造器
    Constructor<?> constructor = clazz.getDeclaredConstructor() //参数是可选的 例如: String.class , int.class
        
    //2.实例化对象 newInstance();
    Object obj = constructor.newInstance(); //对象的类型是运行时确定的,所以在编译阶段无法确定具体的类型 选用Object
    //也可以进行类型转换 例如:
    if (obj instanceof User){
        User user = (User)obj;
    }
    field.get(); //获取字段的值 如果是static,传递 null 否则传递 obj
    //如需访问private字段,需要先设置权限
    field.setAccessible(true);
    ```

    

  

  

