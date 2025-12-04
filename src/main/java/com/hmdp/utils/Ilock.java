package com.hmdp.utils;

public interface Ilock {
    boolean lock(long timeOutSec);
    void unlock();
}
