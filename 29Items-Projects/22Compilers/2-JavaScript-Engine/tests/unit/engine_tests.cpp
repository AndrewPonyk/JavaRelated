#include "jsengine/engine.h"

#include <cassert>
#include <iostream>

namespace {

void evaluatesArithmeticSource() {
    jsengine::Engine engine;
    const auto result = engine.evaluate("let x = 10; x = x + 5; x * 2;");
    assert(result.ok);
    assert(result.value == "30");
}

void evaluatesObjects() {
    jsengine::Engine engine;
    const auto result = engine.evaluate("let user = { name: 'Ada', score: 40 + 2 }; user.score;");
    assert(result.ok);
    assert(result.value == "42");
}

void enforcesConstants() {
    jsengine::Engine engine;
    const auto result = engine.evaluate("const answer = 42; answer = 7;");
    assert(!result.ok);
    assert(result.error.find("constant") != std::string::npos);
}

void evaluatesDecimalsAndStrings() {
    jsengine::Engine engine;
    const auto result = engine.evaluate("let value = 1.5 + 2.25; 'total=' + value;");
    assert(result.ok);
    assert(result.value == "total=3.75");
}

void reportsErrors() {
    jsengine::Engine engine;
    const auto result = engine.evaluate("missing + 1;");
    assert(!result.ok);
    assert(result.error.find("ReferenceError") != std::string::npos);
}

} // namespace

int main() {
    evaluatesArithmeticSource();
    evaluatesObjects();
    enforcesConstants();
    evaluatesDecimalsAndStrings();
    reportsErrors();
    std::cout << "engine tests passed\n";
    return 0;
}
