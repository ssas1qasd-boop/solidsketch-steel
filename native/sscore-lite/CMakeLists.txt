cmake_minimum_required(VERSION 3.22...4.1)
project(sscore_lite CXX)

# Manifold (Apache-2.0), pinned in tools/native/fetch_manifold.sh. Only the 3D core is built:
# no CrossSection (Clipper2), no parallel backend (TBB), no tests, no bindings, no downloads.
set(SSCORE_MANIFOLD_DIR "${CMAKE_CURRENT_LIST_DIR}/../../third_party/manifold" CACHE PATH "Manifold source")
if(NOT TARGET manifold)
    if(NOT EXISTS "${SSCORE_MANIFOLD_DIR}/CMakeLists.txt")
        message(FATAL_ERROR "Manifold not found in ${SSCORE_MANIFOLD_DIR}. Run tools/native/fetch_manifold.sh (Gradle runs it as :fetchManifold).")
    endif()
    foreach(opt MANIFOLD_CROSS_SECTION MANIFOLD_PAR MANIFOLD_TEST MANIFOLD_DOWNLOADS MANIFOLD_CBIND MANIFOLD_PYBIND MANIFOLD_JSBIND MANIFOLD_DEBUG BUILD_SHARED_LIBS)
        set(${opt} OFF CACHE BOOL "" FORCE)
    endforeach()
    add_subdirectory("${SSCORE_MANIFOLD_DIR}" "${CMAKE_BINARY_DIR}/manifold" EXCLUDE_FROM_ALL)
endif()

# Static so each JNI library that needs it links its own copy; no shared-lib
# name clashes between modules. Replaced by the main port's sscore at merge.
add_library(sscore_lite STATIC src/sscore_lite.cpp)
target_include_directories(sscore_lite PUBLIC include)
target_compile_features(sscore_lite PUBLIC cxx_std_17)
target_link_libraries(sscore_lite PRIVATE manifold::manifold)
set_target_properties(sscore_lite PROPERTIES POSITION_INDEPENDENT_CODE ON)
