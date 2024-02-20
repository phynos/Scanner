cmake_minimum_required(VERSION 3.10.2)

project(phynos C)

add_library (phynos SHARED phynos.c)

target_link_libraries(phynos
        log
        )