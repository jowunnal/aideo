cmake_minimum_required(VERSION 3.22.1)

project(onnx-inference)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ONNX Runtime 헤더 경로 설정
set(ONNXRUNTIME_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/include)
include_directories(${ONNXRUNTIME_INCLUDE_DIR}/headers)

# jniLibs 경로 설정
set(JNILIB_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# ONNX Runtime Pre-Built 라이브러리 추가
add_library(onnx-runtime SHARED IMPORTED)
set_target_properties(onnx-runtime PROPERTIES
        IMPORTED_LOCATION ${JNILIB_PATH}/libonnxruntime.so
)

# SentencePiece Pre-Build 추가
add_library(sentence-piece SHARED IMPORTED)
set_target_properties(sentence-piece PROPERTIES
        IMPORTED_LOCATION ${JNILIB_PATH}/libsentencepiece.so)

# 네이티브 코드 추가
add_library(onnx-inference SHARED
        onnxruntime_inference.cpp
        tokenizer.cpp
        m2m100_translator.cpp
        m2m100_jni.cpp
)

# 라이브러리 링크
target_link_libraries(onnx-inference onnx-runtime sentence-piece android log)
