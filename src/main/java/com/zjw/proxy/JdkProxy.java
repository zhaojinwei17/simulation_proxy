package com.zjw.proxy;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.util.*;

public class JdkProxy {

    /**
     * 传入接口或者类的class和InvocationHandler对象，返回代理对象
     * @param clazz 接口或者类的class
     * @param invocationHandler InvocationHandler对象
     * @param <T> 代理对象
     * @return 返回代理对象
     */
    public static<T> T newProxyInstance(Class<T> clazz,InvocationHandler invocationHandler){
        Class<T> proxyClass = getProxyClass(clazz);
        try {
            T t = proxyClass.newInstance();
            if(!Modifier.isAbstract(clazz.getModifiers()) && !Modifier.isInterface(clazz.getModifiers())){
                Field target = proxyClass.getDeclaredField("target");
                target.setAccessible(true);
                target.set(t,clazz.newInstance());
            }
            Field ih = proxyClass.getDeclaredField("invocationHandler");
            ih.setAccessible(true);
            ih.set(t,invocationHandler);
            return t;
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成java源代码，编译，生成class字节码
     * @param clazz 传入被代理的类字节码
     * @param <T> 被代理类型
     * @return 返回字节码对象
     */
    private static <T> Class<T> getProxyClass(Class<T> clazz){

        //获取本类的所有方法，包括私有
        Method[] declaredMethods = clazz.getDeclaredMethods();
        //获取所有公共方法，包括父类的
        Method[] methods = clazz.getMethods();

        //讲所有方法存入set
        Set<Method> allMethods=new HashSet<>();
        allMethods.addAll(Arrays.asList(declaredMethods));
        allMethods.addAll(Arrays.asList(methods));

        //StringBuilder用于存储java源代码
        StringBuilder clstr=new StringBuilder();
        clstr.append(clazz.getPackage()+";");

        if(clazz.isInterface()){
            clstr.append("public class Proxy$"+clazz.getSimpleName()+" implements "+clazz.getName()+"{");
        }else {
            clstr.append("public class Proxy$"+clazz.getSimpleName()+" extends "+clazz.getName()+"{");
        }
        clstr.append("private "+InvocationHandler.class.getName()+" invocationHandler;");
        clstr.append("private Object target = new "+clazz.getName()+"(){");
        for (Method method:
                allMethods) {
            addMethodImpl(clstr,method);
        }
        clstr.append("};");

        //调用addProxyMethod给代理类添加代理方法
        for (Method method:
             allMethods) {
            addProxyMethod(clstr,method,clazz);
        }
        clstr.append("}");
        //将java源代码写入.java文件
        File file=new File(clazz.getClassLoader().getResource("").getPath()+clazz.getPackage().getName().replace(".","/")+"/Proxy$"+clazz.getSimpleName()+".java");
        try {
            PrintWriter writer=new PrintWriter(file);
            writer.println(clstr);
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //编译.java文件生成.class
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> javaFileObjects = standardFileManager.getJavaFileObjects(file);
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, standardFileManager, null, null, null, javaFileObjects);
        task.call();
        //删除源代码文件
        file.delete();
        try {
            standardFileManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            return (Class<T>) Class.forName(clazz.getPackage().getName()+".Proxy$"+clazz.getSimpleName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 传入被代理类的Method，生成代理类重写的方法，并且将代码append加入到代理类中
     * @param clstr 源代码字符串
     * @param method 被代理类的方法
     */
    private static<T> void addProxyMethod(StringBuilder clstr,Method method,Class<T> clazz){
        //获取方法修饰符
        int modifiers = method.getModifiers();
        //判断如果是native，抽象，final,静态方法就不做代理（重写）处理
        if(Modifier.isNative(modifiers) || Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)){
            return;
        }
        //开始重写方法
        if(Modifier.isPublic(modifiers)){
            clstr.append("public ");
        }
        if(Modifier.isProtected(modifiers)){
            clstr.append("protected ");
        }
        if(Modifier.isPrivate(modifiers)){
            clstr.append("private ");
        }
        //获取返回值类型
        Class<?> returnType = method.getReturnType();
        //获取所有参数
        Class<?>[] parameterTypes = method.getParameterTypes();
        clstr.append(returnType.getName()+" "+method.getName()+"(");
        for (int i=0;i<parameterTypes.length;i++) {
            Class<?> parameterType = parameterTypes[i];

            clstr.append(parameterType.getName()+" p"+i);
            if(i!=(parameterTypes.length-1)){
                clstr.append(",");
            }
        }
        clstr.append(") {");
        clstr.append("try {");

        clstr.append(List.class.getName()+"<Object> args=new "+ArrayList.class.getName()+"<>();");
        for (int i=0;i<parameterTypes.length;i++) {
            clstr.append("args.add(p"+i+");");
        }
        clstr.append(Method.class.getName()+" method=Class.forName(\""+clazz.getName()+"\").getMethod(\""+method.getName()+"\"");
        for (Class<?> parameterType:
                parameterTypes) {
            clstr.append(","+parameterType.getName()+".class");
        }
        clstr.append(");");

        clstr.append("Object invoke= null;");
        clstr.append("if("+Modifier.class.getName()+".isAbstract(method.getModifiers())){");
        clstr.append("try{");
        clstr.append("invoke= invocationHandler.invoke(null, method, args.toArray());");
        clstr.append("} catch (Throwable e) {");
        clstr.append("throw new NoSuchMethodException();");
        clstr.append("}");
        clstr.append("} else {");
        clstr.append("invoke= invocationHandler.invoke(target, method, args.toArray());");
        clstr.append("}");



        if(!returnType.equals(void.class)){
            clstr.append("return ("+returnType.getName()+")invoke;");
        }
        clstr.append("} catch (Throwable e) {");
        clstr.append("e.printStackTrace();");
        clstr.append("}");
        if(!returnType.equals(void.class)){
            if(returnType.isPrimitive()){
                clstr.append("class ReturnData { public "+returnType.getName()+" i; }");
                clstr.append("return new ReturnData().i;");
            }else {
                clstr.append("return null;");
            }
        }
        clstr.append("}");
    }
    /**
     * 传入被代理类的Method，生成代理类重写的方法，并且将代码append加入到代理类中
     * @param clstr 源代码字符串
     * @param method 被代理类的方法
     */
    private static<T> void addMethodImpl(StringBuilder clstr,Method method){
        //获取方法修饰符
        int modifiers = method.getModifiers();
        //判断如果是native，抽象，final,静态方法就不做代理（重写）处理
        if(Modifier.isAbstract(modifiers)){
            //开始重写方法
            if(Modifier.isPublic(modifiers)){
                clstr.append("public ");
            }
            if(Modifier.isProtected(modifiers)){
                clstr.append("protected ");
            }
            if(Modifier.isPrivate(modifiers)){
                clstr.append("private ");
            }
            //获取返回值类型
            Class<?> returnType = method.getReturnType();
            //获取所有参数

            clstr.append(returnType.getName()+" "+method.getName()+"(");
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i=0;i<parameterTypes.length;i++) {
                Class<?> parameterType = parameterTypes[i];

                clstr.append(parameterType.getName()+" p"+i);
                if(i!=(parameterTypes.length-1)){
                    clstr.append(",");
                }
            }
            clstr.append(") {");
            if(!returnType.equals(void.class)){
                if(returnType.isPrimitive()){
                    clstr.append("class ReturnData { public "+returnType.getName()+" i; }");
                    clstr.append("return new ReturnData().i;");
                }else {
                    clstr.append("return null;");
                }
            }
            clstr.append("}");

        }
    }
}