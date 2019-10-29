package com.kmfrog.martlet.feed;

public enum Action {
    
    REPLACE, //更新或替换
    INCREMENT, //递增(可以是负值)
    RESET, // CLEAR + REPLACE
    CLEAR;  //清理。比如重新订阅或去掉某个来源。

}
