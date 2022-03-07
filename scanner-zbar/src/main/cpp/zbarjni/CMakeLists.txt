

cmake_minimum_required(VERSION 3.10.2)

project(ZBarDecoder C)

add_library (ZBarDecoder SHARED zbarjni.c)

target_link_libraries(ZBarDecoder
        iconv
        zbar
        log
        )