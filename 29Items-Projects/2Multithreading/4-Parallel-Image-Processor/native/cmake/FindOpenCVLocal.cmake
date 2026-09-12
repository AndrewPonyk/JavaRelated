# ============================================================================
#  FindOpenCVLocal.cmake — help find_package(OpenCV) on machines where OpenCV
#  was unpacked rather than installed.
#
#  Include this BEFORE find_package(OpenCV) to seed OpenCV_DIR from the places a
#  hand-installed OpenCV actually ends up:
#
#      include(cmake/FindOpenCVLocal.cmake)
#      find_package(OpenCV REQUIRED COMPONENTS core imgproc photo)
#
#  Why this file exists rather than "just install OpenCV properly": on Windows
#  there is no package manager doing it for you. The official distribution is a
#  self-extracting archive, and after extraction nothing on the system knows where
#  it went. find_package then fails with the famously unhelpful
#
#      Could not find a package configuration file provided by "OpenCV"
#
#  which sends people to Stack Overflow to be told to set OpenCV_DIR -- to a path
#  whose correct spelling (.../build/x64/vc16/lib) is not guessable. This does the
#  guessing, and when it fails it says what to set and to what.
#
#  Precedence, highest first:
#    1. -DOpenCV_DIR=... on the command line, or the OpenCV_DIR environment
#       variable. An explicit choice is never overridden.
#    2. $OPENCV_DIR — set by OpenCV's own Windows installer.
#    3. The candidate list below.
# ============================================================================

if(DEFINED OpenCV_DIR AND EXISTS "${OpenCV_DIR}")
  message(STATUS "FindOpenCVLocal: using the OpenCV_DIR already set (${OpenCV_DIR})")
  return()
endif()

if(DEFINED ENV{OpenCV_DIR} AND EXISTS "$ENV{OpenCV_DIR}")
  set(OpenCV_DIR "$ENV{OpenCV_DIR}" CACHE PATH "OpenCV config directory" FORCE)
  message(STATUS "FindOpenCVLocal: using OpenCV_DIR from the environment (${OpenCV_DIR})")
  return()
endif()

set(_pip_candidates "")

# The Windows installer sets OPENCV_DIR to .../build/x64/vc16 — one level above
# the directory holding OpenCVConfig.cmake.
if(DEFINED ENV{OPENCV_DIR})
  list(APPEND _pip_candidates "$ENV{OPENCV_DIR}" "$ENV{OPENCV_DIR}/lib")
endif()

if(WIN32)
  # vc17 = VS2022, vc16 = VS2019. Newest first: an OpenCV built with an older
  # toolset than the one compiling pip_enhance links, but mixing MSVC runtimes
  # across major versions produces heap corruption at the library boundary, so
  # preferring the newest available is the safer default.
  foreach(_root
      "C:/opencv" "C:/Programs/opencv" "C:/tools/opencv"
      "D:/opencv" "$ENV{USERPROFILE}/opencv")
    foreach(_vc vc17 vc16 vc15)
      list(APPEND _pip_candidates "${_root}/build/x64/${_vc}/lib")
    endforeach()
    list(APPEND _pip_candidates "${_root}/build")
  endforeach()
elseif(APPLE)
  # Homebrew: /opt/homebrew on Apple Silicon, /usr/local on Intel.
  foreach(_root "/opt/homebrew" "/usr/local")
    list(APPEND _pip_candidates
      "${_root}/lib/cmake/opencv4"
      "${_root}/share/opencv4"
      "${_root}/opt/opencv/lib/cmake/opencv4")
  endforeach()
else()
  # Debian/Ubuntu put the config in an arch-specific directory; Fedora does not.
  list(APPEND _pip_candidates
    "/usr/lib/x86_64-linux-gnu/cmake/opencv4"
    "/usr/lib/aarch64-linux-gnu/cmake/opencv4"
    "/usr/lib64/cmake/opencv4"
    "/usr/lib/cmake/opencv4"
    "/usr/share/OpenCV"
    "/usr/local/lib/cmake/opencv4"
    "/usr/local/share/OpenCV")
endif()

foreach(_candidate IN LISTS _pip_candidates)
  # OpenCVConfig.cmake is the file find_package actually wants. Testing for the
  # directory alone would "succeed" on an empty leftover folder and then fail
  # inside find_package with the same unhelpful message this file exists to avoid.
  if(EXISTS "${_candidate}/OpenCVConfig.cmake")
    set(OpenCV_DIR "${_candidate}" CACHE PATH "OpenCV config directory" FORCE)
    message(STATUS "FindOpenCVLocal: found OpenCVConfig.cmake in ${OpenCV_DIR}")
    return()
  endif()
endforeach()

# Not a fatal error: find_package(OpenCV REQUIRED) will fail on its own a moment
# later, and it prints the full list of paths it searched, which is more useful
# than anything this file can say. All that is added here is the fix.
message(STATUS
  "FindOpenCVLocal: no OpenCV found automatically.\n"
  "  If the next step fails, point CMake at the directory containing\n"
  "  OpenCVConfig.cmake, for example:\n"
  "    Windows: cmake -S native -B native/build -DOpenCV_DIR=C:/opencv/build/x64/vc17/lib\n"
  "    Linux:   cmake -S native -B native/build -DOpenCV_DIR=/usr/lib/x86_64-linux-gnu/cmake/opencv4\n"
  "    macOS:   cmake -S native -B native/build -DOpenCV_DIR=/opt/homebrew/lib/cmake/opencv4")
