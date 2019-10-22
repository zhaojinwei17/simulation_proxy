package com.zjw;

import com.zjw.proxy.InvocationHandler;
import com.zjw.proxy.JdkProxy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Test {
    public static void main(String[] args) {
        UserService userService = JdkProxy.newProxyInstance(UserService.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                System.out.println("执行方法" + method.getName() + "之前。。。。");
                System.out.println("开启事务。。。");
                Object invoke = method.invoke(proxy, args);
                System.out.println("执行方法" + method.getName() + "之后。。。。");
                System.out.println("提交事务。。。");
                return invoke;
            }
        });
        Map<String,String> user=new HashMap<>();
        user.put("name","user1");
        user.put("pwd","111111");
        userService.addUser(user);
    }
}
