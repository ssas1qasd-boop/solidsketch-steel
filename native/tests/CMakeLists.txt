# Host build of the native tests:  cmake -S native/tests -B build/native-tests && cmake --build build/native-tests && ctest --test-dir build/native-tests
cmake_minimum_required(VERSION 3.22...4.1)
project(native_tests CXX)
enable_testing()
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../sssteel ${CMAKE_CURRENT_BINARY_DIR}/sssteel)
add_executable(core_smoke_test core_smoke_test.cpp)
target_link_libraries(core_smoke_test PRIVATE sssteel)
add_test(NAME core_smoke_test COMMAND core_smoke_test)
