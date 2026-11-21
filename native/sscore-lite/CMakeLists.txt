cmake_minimum_required(VERSION 3.22...4.1)
project(sscore_lite CXX)

# Static so each JNI library that needs it links its own copy; no shared-lib
# name clashes between modules. Replaced by the main port's sscore at merge.
add_library(sscore_lite STATIC src/sscore_lite.cpp)
target_include_directories(sscore_lite PUBLIC include)
target_compile_features(sscore_lite PUBLIC cxx_std_17)
set_target_properties(sscore_lite PROPERTIES POSITION_INDEPENDENT_CODE ON)
