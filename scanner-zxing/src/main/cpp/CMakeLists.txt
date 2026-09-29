cmake_minimum_required(VERSION 3.22)

project(scanner-zxing)

# zxing-cpp 构建选项（详见 zxing-cpp/VENDOR.md）
# 只 add_subdirectory(zxing-cpp/core)，绕开上游根 CMakeLists 的 docs/example/wrappers
# （wrappers/ 目录已在 vendor 时裁剪，ZXING_C_API 必须 OFF）
set(ZXING_READERS ON)
set(ZXING_WRITERS ON)          # 新版 writer，基于自带的 core/src/libzint，无需联网
set(ZXING_C_API OFF)
set(ZXING_EXAMPLES OFF)
set(ZXING_EXAMPLES_QT OFF)
set(ZXING_BLACKBOX_TESTS OFF)
set(ZXING_UNIT_TESTS OFF)
set(BUILD_SHARED_LIBS OFF)     # 静态核心链入 JNI so
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(zxing-cpp/core zxing-cpp-core)

add_library(ZXingCppDecoder SHARED jnizxing/zxing_jni.cpp)

target_link_libraries(ZXingCppDecoder ZXing log)
