cmake_minimum_required(VERSION 3.27)
project(OS2faktorPasswordValidationFilter VERSION 2.0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${CMAKE_GENERATOR_PLATFORM})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${CMAKE_GENERATOR_PLATFORM})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${CMAKE_GENERATOR_PLATFORM})

set(CXX_FLAGS "")
set(CXX_FLAGS "${CXX_FLAGS} -D_UNICODE")
set(CXX_FLAGS "${CXX_FLAGS} -DWIN32_LEAN_AND_MEAN")
set(CXX_FLAGS "${CXX_FLAGS} -DNOMINMAX")
# Disable exceptions
if(MSVC)
    set(CXX_FLAGS "${CXX_FLAGS} /EHsc")
endif()
# mingw COFF object files hit a 65535-section limit on large TUs; -mbig-obj raises it
if(MINGW)
    set(CXX_FLAGS "${CXX_FLAGS} -Wa,-mbig-obj")
endif()
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CXX_FLAGS}")

set(OPENSSL_USE_STATIC_LIBS TRUE)

find_package(OpenSSL REQUIRED)

set(HTTPLIB_USE_ZLIB_IF_AVAILABLE OFF)
set(HTTPLIB_USE_BROTLI_IF_AVAILABLE OFF)

find_package(httplib CONFIG REQUIRED)

find_package(spdlog CONFIG REQUIRED)

find_package(Boost REQUIRED COMPONENTS json)

file(GLOB_RECURSE SRC src/*)

add_library(${PROJECT_NAME} SHARED ${SRC})

target_link_libraries(${PROJECT_NAME}
    PRIVATE secur32 
    PRIVATE shlwapi
    PRIVATE credui
    PRIVATE crypt32
    PRIVATE spdlog::spdlog_header_only
    PRIVATE httplib::httplib
    PRIVATE OpenSSL::SSL
    PRIVATE OpenSSL::Crypto
    PRIVATE ntdll
    PRIVATE Boost::json
)

target_compile_definitions(${PROJECT_NAME} PRIVATE
    $<$<CONFIG:Debug>:SPDLOG_ACTIVE_LEVEL=SPDLOG_LEVEL_DEBUG>
)

add_subdirectory(Installer)

option(BUILD_TESTING "Build tests" ON)

if(BUILD_TESTING)
    enable_testing()
    add_subdirectory(test)
endif()
