#include "jsengine/vm/bytecode.h"
#include "jsengine/vm/virtual_machine.h"

#include <cassert>
#include <iostream>

namespace {

jsengine::vm::ExecutionResult run(const jsengine::vm::BytecodeModule& module) {
    jsengine::vm::VirtualMachine vm;
    return vm.execute(module);
}

void coversInvalidOperands() {
    jsengine::vm::BytecodeModule module;
    module.emit(jsengine::vm::Opcode::LoadConst, 99);
    assert(!run(module).ok);

    jsengine::vm::BytecodeModule nameModule;
    nameModule.emit(jsengine::vm::Opcode::LoadName, 99);
    assert(!run(nameModule).ok);

    jsengine::vm::BytecodeModule declareModule;
    declareModule.emit(jsengine::vm::Opcode::LoadConst, declareModule.addConstant(jsengine::runtime::Value::number(1)));
    declareModule.emit(jsengine::vm::Opcode::DeclareName, 99);
    assert(!run(declareModule).ok);
}

void coversStackAndReferenceErrors() {
    jsengine::vm::BytecodeModule addModule;
    addModule.emit(jsengine::vm::Opcode::Add);
    assert(!run(addModule).ok);

    jsengine::vm::BytecodeModule referenceModule;
    referenceModule.emit(jsengine::vm::Opcode::LoadName, referenceModule.addName("missing"));
    assert(!run(referenceModule).ok);

    jsengine::vm::BytecodeModule duplicateModule;
    const auto name = duplicateModule.addName("x");
    duplicateModule.emit(jsengine::vm::Opcode::LoadConst,
                         duplicateModule.addConstant(jsengine::runtime::Value::number(1)));
    duplicateModule.emit(jsengine::vm::Opcode::DeclareName, name);
    duplicateModule.emit(jsengine::vm::Opcode::LoadConst,
                         duplicateModule.addConstant(jsengine::runtime::Value::number(2)));
    duplicateModule.emit(jsengine::vm::Opcode::DeclareName, name);
    assert(!run(duplicateModule).ok);
}

void coversTypeErrorsAndArithmetic() {
    jsengine::vm::BytecodeModule getModule;
    getModule.emit(jsengine::vm::Opcode::LoadConst, getModule.addConstant(jsengine::runtime::Value::number(1)));
    getModule.emit(jsengine::vm::Opcode::GetProperty, getModule.addName("x"));
    assert(!run(getModule).ok);

    jsengine::vm::BytecodeModule setModule;
    setModule.emit(jsengine::vm::Opcode::LoadConst, setModule.addConstant(jsengine::runtime::Value::number(1)));
    setModule.emit(jsengine::vm::Opcode::LoadConst, setModule.addConstant(jsengine::runtime::Value::number(2)));
    setModule.emit(jsengine::vm::Opcode::SetProperty, setModule.addName("x"));
    assert(!run(setModule).ok);

    jsengine::vm::BytecodeModule arithmetic;
    arithmetic.emit(jsengine::vm::Opcode::LoadConst, arithmetic.addConstant(jsengine::runtime::Value::null()));
    arithmetic.emit(jsengine::vm::Opcode::LoadConst, arithmetic.addConstant(jsengine::runtime::Value::boolean(true)));
    arithmetic.emit(jsengine::vm::Opcode::Add);
    arithmetic.emit(jsengine::vm::Opcode::LoadConst, arithmetic.addConstant(jsengine::runtime::Value::number(2)));
    arithmetic.emit(jsengine::vm::Opcode::Divide);
    arithmetic.emit(jsengine::vm::Opcode::Negate);
    arithmetic.emit(jsengine::vm::Opcode::Return);
    const auto result = run(arithmetic);
    assert(result.ok);
    assert(result.value.toString() == "-0.5");
}

} // namespace

int main() {
    coversInvalidOperands();
    coversStackAndReferenceErrors();
    coversTypeErrorsAndArithmetic();
    std::cout << "vm error tests passed\n";
    return 0;
}
