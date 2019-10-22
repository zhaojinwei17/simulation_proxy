package com.zjw;

import java.util.Map;

public abstract class User2Service {
    public void addUser(Map<String,String> user){
        System.out.println(user);
        System.out.println("add user success");
    }
    public abstract int addUser2(Map<String,String> user);
}
