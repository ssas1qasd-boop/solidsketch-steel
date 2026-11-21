cmake_minimum_required(VERSION 3.22...4.1)
project(sssteel_jni CXX)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../native/sssteel ${CMAKE_CURRENT_BINARY_DIR}/sssteel)

add_library(sssteel_jni SHARED sssteel_jni.cpp)
target_link_libraries(sssteel_jni PRIVATE sssteel)
