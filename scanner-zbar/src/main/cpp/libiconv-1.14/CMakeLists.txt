
cmake_minimum_required(VERSION 3.10.2)

#字符编码转换
project(iconv C)

set(SRC_LISTS
        lib/iconv.c
        libcharset/lib/localcharset.c
        lib/relocatable.c
        )

#第一个参数为你需要构建的dll的名字，第二个为类型
add_library (iconv SHARED ${SRC_LISTS})
target_include_directories(iconv
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/libcharset
        ${CMAKE_CURRENT_SOURCE_DIR}/libcharset/include)
target_link_libraries(iconv
        log
        )
target_compile_options(iconv
        PRIVATE
        -Wno-multichar
        -DANDROID
        -D_ANDROID
        -DLIBDIR=\"c\"
        -DBUILDING_LIBICONV
        -DBUILDING_LIBCHARSET
        -DIN_LIBRARY
)