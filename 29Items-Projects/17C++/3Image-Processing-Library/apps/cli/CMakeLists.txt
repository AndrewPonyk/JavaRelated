# apps/cli/CMakeLists.txt
add_executable(imgproc-cli main.cpp)
target_link_libraries(imgproc-cli PRIVATE imgproc::imgproc)
imgproc_set_warnings(imgproc-cli)

if(IMGPROC_WITH_ONNX)
    imgproc_stage_onnxruntime(imgproc-cli)
endif()

install(TARGETS imgproc-cli RUNTIME DESTINATION bin)
