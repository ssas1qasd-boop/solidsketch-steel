cmake_minimum_required(VERSION 3.22...4.1)
project(sscore_lite_jni CXX)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../native/sscore-lite ${CMAKE_CURRENT_BINARY_DIR}/sscore-lite)

add_library(sscore_lite_jni SHARED sscore_lite_jni.cpp)
target_link_libraries(sscore_lite_jni PRIVATE sscore_lite)
