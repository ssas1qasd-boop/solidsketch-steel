cmake_minimum_required(VERSION 3.22...4.1)
project(sssteel CXX)

# The core it links. At merge time point SSCORE_DIR at the main port's sscore.
set(SSCORE_DIR "${CMAKE_CURRENT_LIST_DIR}/../sscore-lite" CACHE PATH "Geometry core used by sssteel")
if(NOT TARGET sscore_lite)
    add_subdirectory(${SSCORE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/sscore-lite)
endif()

add_library(sssteel STATIC src/sssteel.cpp)
target_include_directories(sssteel PUBLIC include)
target_link_libraries(sssteel PUBLIC sscore_lite)
target_compile_features(sssteel PUBLIC cxx_std_17)
set_target_properties(sssteel PROPERTIES POSITION_INDEPENDENT_CODE ON)
