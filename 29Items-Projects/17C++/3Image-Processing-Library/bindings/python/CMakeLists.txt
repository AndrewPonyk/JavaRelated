# bindings/python/CMakeLists.txt
pybind11_add_module(_imgproc module.cpp)
target_link_libraries(_imgproc PRIVATE imgproc::imgproc)

# Install the compiled extension next to the Python package sources.
install(TARGETS _imgproc LIBRARY DESTINATION imgproc)
