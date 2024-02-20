//
// Created by Administrator on 2024/2/20.
//
#include <jni.h>
#include <android/log.h>


#define LOG_TAG "stone.stone"
#define slogd(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

JNIEXPORT jstring JNICALL
Java_com_phynos_zbar_MyJniTest_getData(JNIEnv *env, jobject thiz) {
    slogd("test number=%d", 99999);
    return (*env)->NewStringUTF(env, "hello mindeo");
}