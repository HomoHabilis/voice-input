
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("voiceinput")

set(CMAKE_CXX_FLAGS "-O3 -Wall -Wextra -g3")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    voiceinput.cpp
        ggml/whisper.cpp
        ggml/ggml.c
        ggml/ggml-alloc.c
        ggml/ggml-backend.c
        ggml/ggml-quants.c
        jni_common.cpp
        whistle_jni.cpp)

if(ANDROID_ABI STREQUAL armeabi-v7a)
    set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES COMPILE_FLAGS -mfpu=neon)
endif()


# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
    android
    log)

# Cactus Needle engine (runs the Whistle model). scripts/fetch-native-deps.sh downloads the
# prebuilt static library from Hugging Face into third_party/needle/<platform>/. Without it the
# app still builds and Whistle reports itself as unavailable.
if(ANDROID_ABI STREQUAL arm64-v8a)
    set(NEEDLE_PLATFORM android-arm64)
elseif(ANDROID_ABI STREQUAL armeabi-v7a)
    set(NEEDLE_PLATFORM android-armv7)
endif()
set(NEEDLE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/needle/${NEEDLE_PLATFORM})
file(GLOB NEEDLE_STATIC_LIBS ${NEEDLE_DIR}/*.a)
if(NEEDLE_PLATFORM AND NEEDLE_STATIC_LIBS)
    message(STATUS "Linking Cactus Needle engine: ${NEEDLE_STATIC_LIBS}")
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE HAVE_NEEDLE=1)
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${NEEDLE_DIR})
    target_link_libraries(${CMAKE_PROJECT_NAME} ${NEEDLE_STATIC_LIBS} m dl)
else()
    message(WARNING "Cactus Needle engine not found in ${NEEDLE_DIR}; Whistle will be unavailable")
endif()
