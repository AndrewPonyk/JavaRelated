#include "jsengine/runtime/garbage_collector.h"
#include "jsengine/runtime/object.h"
#include "jsengine/runtime/value.h"

#include <cassert>
#include <iostream>
#include <memory>
#include <stdexcept>

namespace {

void coversValues() {
    const auto undefined = jsengine::runtime::Value::undefined();
    const auto nullValue = jsengine::runtime::Value::null();
    const auto falseValue = jsengine::runtime::Value::boolean(false);
    const auto trueValue = jsengine::runtime::Value::boolean(true);
    const auto zero = jsengine::runtime::Value::number(0);
    const auto text = jsengine::runtime::Value::string("text");

    assert(undefined.toString() == "undefined");
    assert(nullValue.toString() == "null");
    assert(falseValue.toString() == "false");
    assert(trueValue.toString() == "true");
    assert(zero.toString() == "0");
    assert(text.asString() == "text");
    assert(!undefined.isTruthy());
    assert(!nullValue.isTruthy());
    assert(!falseValue.isTruthy());
    assert(!zero.isTruthy());
    assert(text.isTruthy());
    assert(trueValue.asBoolean());
    assert(jsengine::runtime::Value::number(3.5).asNumber() == 3.5);

    bool threw = false;
    try {
        (void)text.asNumber();
    } catch (const std::logic_error&) {
        threw = true;
    }
    assert(threw);

    threw = false;
    try {
        (void)jsengine::runtime::Value::object(nullptr);
    } catch (const std::invalid_argument&) {
        threw = true;
    }
    assert(threw);
}

void coversObjects() {
    auto object = std::make_shared<jsengine::runtime::Object>();
    assert(object->setProperty("name", jsengine::runtime::Value::string("Ada")));
    assert(object->getProperty("name")->toString() == "Ada");
    assert(!object->getProperty("missing").has_value());
    assert(object->ownPropertyNames().size() == 1);
    assert(object->shapeId().find("name") != std::string::npos);
    assert(object->inspect().find("Ada") != std::string::npos);

    object->defineProperty("fixed", {.value = jsengine::runtime::Value::number(1), .writable = false});
    assert(!object->setProperty("fixed", jsengine::runtime::Value::number(2)));
    assert(object->getProperty("fixed")->toString() == "1");

    const auto wrapped = jsengine::runtime::Value::object(object);
    assert(wrapped.asObject()->getProperty("name")->toString() == "Ada");
    assert(wrapped.toString().find("name") != std::string::npos);
}

void coversGarbageCollectorAccounting() {
    jsengine::runtime::GarbageCollector gc;
    gc.recordAllocation(64);
    gc.writeBarrier();
    gc.collectNursery();
    gc.startIncrementalMarking();
    auto stats = gc.stats();
    assert(stats.bytesAllocated == 64);
    assert(stats.nurseryCollections == 1);
    assert(stats.majorCollections == 1);
    assert(stats.nurseryBytes == 0);
    assert(stats.oldGenerationBytes == 64);
    assert(stats.incrementalMarking);

    gc.finishIncrementalMarking();
    stats = gc.stats();
    assert(!stats.incrementalMarking);
}

} // namespace

int main() {
    coversValues();
    coversObjects();
    coversGarbageCollectorAccounting();
    std::cout << "runtime tests passed\n";
    return 0;
}
