#include <exception>
#include <iostream>

namespace jsengine::tools {
int runCli(int argc, char** argv);
}

int main(int argc, char** argv) {
    try {
        return jsengine::tools::runCli(argc, argv);
    } catch (const std::exception& error) {
        std::cerr << "fatal: " << error.what() << '\n';
        return 1;
    }
}
