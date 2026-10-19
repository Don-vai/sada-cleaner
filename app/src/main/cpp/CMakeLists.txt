
cmake_minimum_required(VERSION 3.22.1)

project(sada_cleaner LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    sada_native
    SHARED
    native_engine.cpp
    rust_bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    sada_native
    PRIVATE
    ${log-lib}
)
