cmake_minimum_required(VERSION 3.10.2)

project(phynos C)

set(SRC_LISTS
        phynos.c
        )

add_library (phynos SHARED ${SRC_LISTS})

target_link_libraries(phynos
        log
        )