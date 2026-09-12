# conanfile.py — Conan 2.x alternative to the vcpkg manifest.
from conan import ConanFile
from conan.tools.cmake import CMakeToolchain, CMakeDeps, cmake_layout


class ImgprocConan(ConanFile):
    name = "imgproc"
    version = "0.1.0"
    description = "CUDA-accelerated computer-vision library (detection & tracking)"
    license = "Apache-2.0"
    settings = "os", "compiler", "build_type", "arch"

    options = {
        "with_cuda": [True, False],
        "build_python": [True, False],
        "build_tests": [True, False],
        "build_bench": [True, False],
    }
    default_options = {
        "with_cuda": True,
        "build_python": True,
        "build_tests": True,
        "build_bench": False,
    }

    def requirements(self):
        self.requires("opencv/4.9.0")
        if self.options.build_python:
            self.requires("pybind11/2.12.0")

    def build_requirements(self):
        if self.options.build_tests:
            self.test_requires("gtest/1.14.0")
        if self.options.build_bench:
            self.test_requires("benchmark/1.8.3")

    def layout(self):
        cmake_layout(self)

    def generate(self):
        deps = CMakeDeps(self)
        deps.generate()
        tc = CMakeToolchain(self)
        tc.variables["IMGPROC_WITH_CUDA"] = bool(self.options.with_cuda)
        tc.variables["IMGPROC_BUILD_PYTHON"] = bool(self.options.build_python)
        tc.variables["IMGPROC_BUILD_TESTS"] = bool(self.options.build_tests)
        tc.variables["IMGPROC_BUILD_BENCH"] = bool(self.options.build_bench)
        tc.generate()
