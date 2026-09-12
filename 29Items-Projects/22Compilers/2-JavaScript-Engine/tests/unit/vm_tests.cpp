#include "jsengine/vm/bytecode.h"
#include "jsengine/vm/virtual_machine.h"

#include <cassert>
#include <iostream>

namespace {

void returnsLoadedConstant() {
    jsengine::vm::BytecodeModule module;
    const auto index = module.addConstant(jsengine::runtime::Value::string("hello"));
    module.emit(jsengine::vm::Opcode::LoadConst, index);
    module.emit(jsengine::vm::Opcode::Return);

    jsengine::vm::VirtualMachine vm;
    const auto result = vm.execute(module);

    assert(result.ok);
    assert(result.value.toString() == "hello");
}

void evaluatesArithmetic() {
    jsengine::vm::BytecodeModule module;
    module.emit(jsengine::vm::Opcode::LoadConst, module.addConstant(jsengine::runtime::Value::number(6)));
    module.emit(jsengine::vm::Opcode::LoadConst, module.addConstant(jsengine::runtime::Value::number(7)));
    module.emit(jsengine::vm::Opcode::Multiply);
    module.emit(jsengine::vm::Opcode::Return);

    jsengine::vm::VirtualMachine vm;
    const auto result = vm.execute(module);

    assert(result.ok);
    assert(result.value.toString() == "42");
}

void evaluatesObjects() {
    jsengine::vm::BytecodeModule module;
    const auto name = module.addName("answer");
    module.emit(jsengine::vm::Opcode::MakeObject);
    module.emit(jsengine::vm::Opcode::LoadConst, module.addConstant(jsengine::runtime::Value::number(42)));
    module.emit(jsengine::vm::Opcode::DefineProperty, name);
    module.emit(jsengine::vm::Opcode::GetProperty, name);
    module.emit(jsengine::vm::Opcode::Return);

    jsengine::vm::VirtualMachine vm;
    const auto result = vm.execute(module);

    assert(result.ok);
    assert(result.value.toString() == "42");
}

void rejectsConstReassignment() {
    jsengine::vm::BytecodeModule module;
    const auto name = module.addName("answer");
    module.emit(jsengine::vm::Opcode::LoadConst, module.addConstant(jsengine::runtime::Value::number(42)));
    module.emit(jsengine::vm::Opcode::DeclareConst, name);
    module.emit(jsengine::vm::Opcode::Pop);
    module.emit(jsengine::vm::Opcode::LoadConst, module.addConstant(jsengine::runtime::Value::number(7)));
    module.emit(jsengine::vm::Opcode::StoreName, name);
    module.emit(jsengine::vm::Opcode::Return);

    jsengine::vm::VirtualMachine vm;
    const auto result = vm.execute(module);

    assert(!result.ok);
    assert(result.error.find("constant") != std::string::npos);
}

} // namespace

int main() {
    returnsLoadedConstant();
    evaluatesArithmetic();
    evaluatesObjects();
    rejectsConstReassignment();
    std::cout << "vm tests passed\n";
    return 0;
}
