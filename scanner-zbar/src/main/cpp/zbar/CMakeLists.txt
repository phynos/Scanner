

cmake_minimum_required(VERSION 3.10.2)

project(zbar C)

set(SRC_LISTS
        img_scanner.c
        decoder.c
        image.c
        symbol.c
        convert.c
        config.c
        scanner.c
        error.c
        refcnt.c
        video.c
        video/null.c
        decoder/code128.c
        decoder/code39.c
        decoder/code93.c
        decoder/codabar.c
        decoder/databar.c
        decoder/ean.c
        decoder/i25.c
        decoder/qr_finder.c
        qrcode/bch15_5.c
        qrcode/binarize.c
        qrcode/isaac.c
        qrcode/qrdec.c
        qrcode/qrdectxt.c
        qrcode/rs.c
        qrcode/util.c
        config.h
        )

add_library (zbar SHARED ${SRC_LISTS})
target_include_directories(zbar PUBLIC ../include ./)
target_link_libraries(zbar
        iconv
        )
target_compile_definitions(zbar
        PRIVATE
        -DLIBDIR="c"
        -D_ANDROID)