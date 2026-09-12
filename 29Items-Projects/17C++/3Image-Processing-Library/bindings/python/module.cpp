// bindings/python/module.cpp
// pybind11 bindings exposing the imgproc C++ API to Python — the primary
// user-facing surface for data scientists. Accepts/returns NumPy arrays.
#include <pybind11/numpy.h>
#include <pybind11/pybind11.h>
#include <pybind11/stl.h>

#include <cstring>
#include <stdexcept>

#include "imgproc/core/image.hpp"
#include "imgproc/core/types.hpp"
#include "imgproc/detection/object_detector.hpp"
#include "imgproc/tracking/tracker.hpp"
#include "imgproc/version.hpp"

namespace py = pybind11;
namespace ip = imgproc;

namespace {

// Convert a NumPy uint8 array (H,W) or (H,W,3) into a core::Image.
ip::core::Image toImage(const py::array& arr) {
    auto a = py::array_t<std::uint8_t, py::array::c_style | py::array::forcecast>::ensure(arr);
    if (!a) {
        throw std::invalid_argument("expected a uint8 NumPy array");
    }
    const auto ndim = a.ndim();
    if (ndim != 2 && ndim != 3) {
        throw std::invalid_argument("image must be (H,W) or (H,W,3)");
    }
    const int h = static_cast<int>(a.shape(0));
    const int w = static_cast<int>(a.shape(1));
    const int c = (ndim == 3) ? static_cast<int>(a.shape(2)) : 1;
    if (c != 1 && c != 3) {
        throw std::invalid_argument("channel count must be 1 or 3");
    }
    ip::core::Image img(h, w,
                        c == 3 ? ip::core::PixelFormat::kRgb8 : ip::core::PixelFormat::kGray8);
    std::memcpy(img.data(), a.data(), img.byte_size());
    return img;
}

void raiseIfError(const ip::core::Status& st) {
    if (!st.ok()) {
        throw std::runtime_error(st.message());
    }
}

}  // namespace

PYBIND11_MODULE(_imgproc, m) {
    m.doc() = "CUDA-accelerated computer-vision library (detection & tracking)";
    m.attr("__version__") = ip::kVersionString;

    py::class_<ip::core::BBox>(m, "BBox")
        .def(py::init<>())
        .def_readwrite("x", &ip::core::BBox::x)
        .def_readwrite("y", &ip::core::BBox::y)
        .def_readwrite("width", &ip::core::BBox::width)
        .def_readwrite("height", &ip::core::BBox::height)
        .def("area", &ip::core::BBox::area)
        .def("__repr__", [](const ip::core::BBox& b) {
            return "<BBox x=" + std::to_string(b.x) + " y=" + std::to_string(b.y) +
                   " w=" + std::to_string(b.width) + " h=" + std::to_string(b.height) + ">";
        });

    py::class_<ip::core::Detection>(m, "Detection")
        .def(py::init<>())
        .def_readwrite("box", &ip::core::Detection::box)
        .def_readwrite("class_id", &ip::core::Detection::class_id)
        .def_readwrite("score", &ip::core::Detection::score)
        .def_readwrite("label", &ip::core::Detection::label);

    py::class_<ip::core::Track>(m, "Track")
        .def(py::init<>())
        .def_readwrite("track_id", &ip::core::Track::track_id)
        .def_readwrite("box", &ip::core::Track::box)
        .def_readwrite("class_id", &ip::core::Track::class_id)
        .def_readwrite("velocity_x", &ip::core::Track::velocity_x)
        .def_readwrite("velocity_y", &ip::core::Track::velocity_y);

    py::enum_<ip::detection::DetectorBackend>(m, "DetectorBackend")
        .value("ClassicalBlob", ip::detection::DetectorBackend::kClassicalBlob)
        .value("Model", ip::detection::DetectorBackend::kModel);

    py::class_<ip::detection::DetectorConfig>(m, "DetectorConfig")
        .def(py::init<>())
        .def_readwrite("backend", &ip::detection::DetectorConfig::backend)
        .def_readwrite("score_threshold", &ip::detection::DetectorConfig::score_threshold)
        .def_readwrite("nms_iou_threshold", &ip::detection::DetectorConfig::nms_iou_threshold)
        .def_readwrite("class_labels", &ip::detection::DetectorConfig::class_labels)
        .def_readwrite("blob_threshold", &ip::detection::DetectorConfig::blob_threshold)
        .def_readwrite("min_blob_area", &ip::detection::DetectorConfig::min_blob_area)
        .def_readwrite("denoise", &ip::detection::DetectorConfig::denoise);

    py::class_<ip::detection::ObjectDetector>(m, "ObjectDetector")
        .def(py::init([](const ip::detection::DetectorConfig& cfg) {
                 std::unique_ptr<ip::detection::ObjectDetector> det;
                 raiseIfError(ip::detection::ObjectDetector::create(cfg, det));
                 return det;
             }),
             py::arg("config") = ip::detection::DetectorConfig{})
        .def("detect",
             [](ip::detection::ObjectDetector& self, const py::array& frame) {
                 const ip::core::Image img = toImage(frame);
                 ip::core::Detections out;
                 {
                     py::gil_scoped_release release;  // heavy native work, no Python objects
                     raiseIfError(self.detect(img, out));
                 }
                 return out;
             },
             py::arg("frame"));

    py::class_<ip::tracking::TrackerConfig>(m, "TrackerConfig")
        .def(py::init<>())
        .def_readwrite("iou_threshold", &ip::tracking::TrackerConfig::iou_threshold)
        .def_readwrite("max_age", &ip::tracking::TrackerConfig::max_age)
        .def_readwrite("min_hits", &ip::tracking::TrackerConfig::min_hits);

    py::class_<ip::tracking::Tracker>(m, "Tracker")
        .def(py::init<ip::tracking::TrackerConfig>(),
             py::arg("config") = ip::tracking::TrackerConfig{})
        .def("update",
             [](ip::tracking::Tracker& self, const ip::core::Detections& dets) {
                 ip::core::Tracks tracks;
                 raiseIfError(self.update(dets, tracks));
                 return tracks;
             },
             py::arg("detections"))
        .def("reset", &ip::tracking::Tracker::reset);
}
