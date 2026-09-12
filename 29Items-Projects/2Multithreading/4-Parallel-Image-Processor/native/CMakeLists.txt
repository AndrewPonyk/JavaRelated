# ============================================================================
#  pip_enhance — the OpenCV/JNI shim.
#
#  This build is deliberately OUTSIDE Maven. Reasons, in order of how much
#  trouble each one saves:
#
#    1. `mvn verify` must work on a machine with no C++ toolchain and no OpenCV.
#       Nine developers out of ten never touch this directory, and making them
#       install Visual Studio or apt-get libopencv-dev to run the unit tests
#       would be a self-inflicted wound.
#    2. The output is a per-platform binary. A jar is portable; a .dll is not.
#       The artefact lands in pip-native/src/main/resources/native/<os>-<arch>/
#       and is packaged from there, so a release can bundle several platforms'
#       libraries built on several machines (see .github/workflows/native-build.yml).
#    3. CMake already knows how to find OpenCV and the JNI headers on every OS.
#       Reimplementing that in a Maven plugin configuration is not an improvement.
#
#  Build:
#      cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
#      cmake --build native/build --config Release
#      cmake --install native/build
#
#  Or use scripts/build-native.{cmd,sh}, which do exactly that plus the JAVA_HOME
#  check whose absence is the single most common first failure.
# ============================================================================

cmake_minimum_required(VERSION 3.20)
project(pip_enhance LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Position-independent code: a shared library that the JVM dlopen()s must be PIC,
# and on some Linux toolchains this is not the default for object files.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Hide everything that is not an explicit JNIEXPORT. Two benefits: the dynamic
# symbol table stays small (faster load), and an OpenCV symbol cannot leak out of
# this library to collide with a differently-versioned OpenCV loaded elsewhere in
# the same process -- which is a genuinely baffling class of crash.
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

# ----------------------------------------------------------------------------
#  JNI
#
#  find_package(JNI) uses JAVA_HOME. It must be the SAME JDK that compiles
#  pip-native: jni.h from JDK 11 and a library loaded by a JDK 21 JVM happen to
#  be compatible today, but the failure mode when they are not is a crash inside
#  the JVM with no Java frames, so the version is pinned rather than hoped for.
# ----------------------------------------------------------------------------
find_package(JNI REQUIRED)

# ----------------------------------------------------------------------------
#  OpenCV
#
#  `core imgproc photo` is the minimum: core for Mat, imgproc for cvtColor and
#  CLAHE, photo for fastNlMeansDenoisingColored. dnn_superres lives in
#  opencv_contrib and is genuinely optional -- a distribution package usually
#  lacks it. When it is missing the library still builds and SUPER_RESOLUTION
#  reports itself unsupported, which the Java side handles as a pass-through.
# ----------------------------------------------------------------------------
include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/FindOpenCVLocal.cmake)
find_package(OpenCV REQUIRED COMPONENTS core imgproc photo)
find_package(OpenCV QUIET COMPONENTS dnn_superres)

set(PIP_SOURCES src/pip_enhance.cpp)

add_library(pip_enhance SHARED ${PIP_SOURCES})

target_include_directories(pip_enhance
  PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include   # javac-generated JNI header
    ${JNI_INCLUDE_DIRS}
    ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(pip_enhance PRIVATE ${OpenCV_LIBS})

if(OpenCV_dnn_superres_FOUND OR TARGET opencv_dnn_superres)
  target_compile_definitions(pip_enhance PRIVATE PIP_WITH_DNN_SUPERRES)
  message(STATUS "pip_enhance: dnn_superres found, SUPER_RESOLUTION enabled")
else()
  message(STATUS "pip_enhance: dnn_superres NOT found, SUPER_RESOLUTION will pass through")
endif()

# No "lib" prefix on Windows, "lib" prefix everywhere else -- which is what
# System.mapLibraryName() expects, and NativeLibraryLoader builds the resource
# path from exactly that call. CMake's defaults already match; this is here so
# nobody "tidies up" the output name and silently breaks the lookup.
set_target_properties(pip_enhance PROPERTIES
  OUTPUT_NAME "pip_enhance"
  DEBUG_POSTFIX ""          # a _d suffix would not match mapLibraryName either
)

# Warnings on, and treated seriously. This file is the one place in the project
# where an uninitialised variable is a crash rather than an exception.
if(MSVC)
  target_compile_options(pip_enhance PRIVATE /W4 /permissive-)
else()
  target_compile_options(pip_enhance PRIVATE -Wall -Wextra -Wpedantic)
endif()

# ----------------------------------------------------------------------------
#  Install straight into the module's resources
#
#  Not into a staging directory that someone then has to copy: the whole point is
#  that `mvn package` afterwards picks the library up with no manual step. The
#  <os>-<arch> subdirectory must agree with NativeLibraryLoader.platformDirectory().
# ----------------------------------------------------------------------------
string(TOLOWER "${CMAKE_SYSTEM_NAME}" PIP_OS)
if(PIP_OS STREQUAL "darwin")
  set(PIP_OS "macos")
endif()

set(PIP_ARCH "${CMAKE_SYSTEM_PROCESSOR}")
string(TOLOWER "${PIP_ARCH}" PIP_ARCH)
if(PIP_ARCH MATCHES "^(amd64|x86_64|x64)$")
  set(PIP_ARCH "x86_64")
elseif(PIP_ARCH MATCHES "^(aarch64|arm64)$")
  set(PIP_ARCH "aarch64")
endif()

set(PIP_RESOURCE_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../pip-native/src/main/resources/native/${PIP_OS}-${PIP_ARCH}")

message(STATUS "pip_enhance: will install to ${PIP_RESOURCE_DIR}")

install(TARGETS pip_enhance
  RUNTIME DESTINATION "${PIP_RESOURCE_DIR}"   # Windows .dll
  LIBRARY DESTINATION "${PIP_RESOURCE_DIR}"   # Linux .so, macOS .dylib
)
