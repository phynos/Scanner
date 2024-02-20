package com.phynos.zbar;

public class MyJniTest {

    static {
        System.loadLibrary("phynos");
    }

    public static native String getData();

}
