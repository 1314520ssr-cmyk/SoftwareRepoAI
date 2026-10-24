cmake_minimum_required(VERSION 3.22.1)
project(localai LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp ${CMAKE_BINARY_DIR}/llama.cpp-build)

add_library(localai SHARED llama_bridge.cpp llama_generate.cpp)
target_include_directories(localai PRIVATE llama.cpp/include llama.cpp/ggml/include)
target_link_libraries(localai PRIVATE llama ggml android log)
