#include "jsengine/engine.h"

#include <iostream>
#include <sstream>
#include <string>

namespace jsengine::tools {

int runCli(int argc, char** argv) {
    std::string source;
    if (argc > 1) {
        source = argv[1];
    } else {
        std::ostringstream buffer;
        buffer << std::cin.rdbuf();
        source = buffer.str();
    }

    Engine engine;
    const auto result = engine.evaluate(source);
    if (!result.ok) {
        std::cerr << result.error << '\n';
        return 1;
    }

    std::cout << result.value << '\n';
    return 0;
}

} // namespace jsengine::tools
