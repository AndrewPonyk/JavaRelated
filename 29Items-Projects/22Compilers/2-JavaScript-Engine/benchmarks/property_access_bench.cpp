#include "jsengine/runtime/object.h"
#include "jsengine/vm/inline_cache.h"

#include <chrono>
#include <iostream>

int main()
{
    jsengine::runtime::Object object;
    object.setProperty("name", jsengine::runtime::Value::string("runtime"));

    jsengine::vm::PropertyInlineCache cache;
    cache.recordHit(object.shapeId(), 0);

    constexpr int iterations = 100000;
    const auto started = std::chrono::steady_clock::now();
    for (int i = 0; i < iterations; ++i) {
        (void)object.getProperty("name");
    }
    const auto elapsed = std::chrono::steady_clock::now() - started;

    std::cout << "property_access_iterations=" << iterations
              << " elapsed_ns=" << std::chrono::duration_cast<std::chrono::nanoseconds>(elapsed).count()
              << '\n';
    return 0;
}
