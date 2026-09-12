#include "jsengine/engine.h"

#include <iostream>

int main()
{
    jsengine::Engine engine;
    const auto result = engine.evaluate("const greeting = 'hello from host';");

    if (!result.ok) {
        std::cerr << result.error << '\n';
        return 1;
    }

    std::cout << "engine returned: " << result.value << '\n';
    return 0;
}
