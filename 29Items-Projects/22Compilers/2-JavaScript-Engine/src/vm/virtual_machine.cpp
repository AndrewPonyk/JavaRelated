#include "jsengine/vm/virtual_machine.h"

#include "jsengine/runtime/object.h"

#include <cmath>
#include <limits>
#include <memory>

namespace jsengine::vm {

namespace {

double toNumber(const runtime::Value& value) {
    switch (value.type()) {
    case runtime::ValueType::Undefined:
        return std::numeric_limits<double>::quiet_NaN();
    case runtime::ValueType::Null:
        return 0.0;
    case runtime::ValueType::Boolean:
        return value.asBoolean() ? 1.0 : 0.0;
    case runtime::ValueType::Number:
        return value.asNumber();
    case runtime::ValueType::String:
        try {
            std::size_t read = 0;
            const auto parsed = std::stod(value.asString(), &read);
            return read == value.asString().size() ? parsed : std::numeric_limits<double>::quiet_NaN();
        } catch (...) {
            return std::numeric_limits<double>::quiet_NaN();
        }
    case runtime::ValueType::Object:
        return std::numeric_limits<double>::quiet_NaN();
    }
    return std::numeric_limits<double>::quiet_NaN();
}

bool isStringLikeAdd(const runtime::Value& left, const runtime::Value& right) {
    return left.type() == runtime::ValueType::String || right.type() == runtime::ValueType::String;
}

} // namespace

ExecutionResult VirtualMachine::execute(const BytecodeModule& module) {
    stack_.clear();

    const auto& instructions = module.instructions();
    for (std::size_t ip = 0; ip < instructions.size(); ++ip) {
        const auto& instruction = instructions[ip];
        switch (instruction.opcode) {
        case Opcode::LoadConst:
            if (instruction.operand >= module.constants().size()) {
                return {
                    .ok = false, .value = runtime::Value::undefined(), .error = "Bytecode constant index out of range"};
            }
            stack_.push_back(module.constants()[instruction.operand]);
            break;
        case Opcode::LoadName:
            if (instruction.operand >= module.names().size()) {
                return {.ok = false, .value = runtime::Value::undefined(), .error = "Bytecode name index out of range"};
            } else {
                const auto found = globals_.find(module.names()[instruction.operand]);
                if (found == globals_.end()) {
                    return {.ok = false,
                            .value = runtime::Value::undefined(),
                            .error = "ReferenceError: " + module.names()[instruction.operand] + " is not defined"};
                }
                stack_.push_back(found->second.value);
            }
            break;
        case Opcode::DeclareName: {
            const auto declared = declareName(module, instruction, true);
            if (!declared.ok) {
                return declared;
            }
            break;
        }
        case Opcode::DeclareConst: {
            const auto declared = declareName(module, instruction, false);
            if (!declared.ok) {
                return declared;
            }
            break;
        }
        case Opcode::StoreName:
            if (const auto stored = storeName(module, instruction); !stored.ok) {
                return stored;
            }
            break;
        case Opcode::Add: {
            runtime::Value right;
            runtime::Value left;
            std::string error;
            if (!pop(right, error) || !pop(left, error)) {
                return {.ok = false, .value = runtime::Value::undefined(), .error = error};
            }
            if (isStringLikeAdd(left, right)) {
                stack_.push_back(runtime::Value::string(left.toString() + right.toString()));
            } else {
                stack_.push_back(runtime::Value::number(toNumber(left) + toNumber(right)));
            }
            break;
        }
        case Opcode::Subtract:
        case Opcode::Multiply:
        case Opcode::Divide: {
            runtime::Value right;
            runtime::Value left;
            std::string error;
            if (!pop(right, error) || !pop(left, error)) {
                return {.ok = false, .value = runtime::Value::undefined(), .error = error};
            }
            const auto leftNumber = toNumber(left);
            const auto rightNumber = toNumber(right);
            if (instruction.opcode == Opcode::Subtract) {
                stack_.push_back(runtime::Value::number(leftNumber - rightNumber));
            } else if (instruction.opcode == Opcode::Multiply) {
                stack_.push_back(runtime::Value::number(leftNumber * rightNumber));
            } else {
                stack_.push_back(runtime::Value::number(leftNumber / rightNumber));
            }
            break;
        }
        case Opcode::Negate: {
            runtime::Value value;
            std::string error;
            if (!pop(value, error)) {
                return {.ok = false, .value = runtime::Value::undefined(), .error = error};
            }
            stack_.push_back(runtime::Value::number(-toNumber(value)));
            break;
        }
        case Opcode::MakeObject:
            stack_.push_back(runtime::Value::object(std::make_shared<runtime::Object>()));
            break;
        case Opcode::DefineProperty: {
            if (instruction.operand >= module.names().size()) {
                return {.ok = false,
                        .value = runtime::Value::undefined(),
                        .error = "Bytecode property name index out of range"};
            }
            runtime::Value value;
            runtime::Value object;
            std::string error;
            if (!pop(value, error) || !pop(object, error)) {
                return {.ok = false, .value = runtime::Value::undefined(), .error = error};
            }
            if (object.type() != runtime::ValueType::Object) {
                return {.ok = false,
                        .value = runtime::Value::undefined(),
                        .error = "TypeError: cannot define property on non-object"};
            }
            object.asObject()->defineProperty(module.names()[instruction.operand], {.value = value});
            stack_.push_back(object);
            break;
        }
        case Opcode::GetProperty: {
            if (instruction.operand >= module.names().size()) {
                return {.ok = false,
                        .value = runtime::Value::undefined(),
                        .error = "Bytecode property name index out of range"};
            }
            runtime::Value object;
            std::string error;
            if (!pop(object, error)) {
                return {.ok = false, .value = runtime::Value::undefined(), .error = error};
            }
            if (object.type() != runtime::ValueType::Object) {
                return {.ok = false,
                        .value = runtime::Value::undefined(),
                        .error = "TypeError: cannot read property from non-object"};
            }
            const auto property = object.asObject()->getProperty(module.names()[instruction.operand]);
            propertyCaches_[ip].recordHit(object.asObject()->shapeId(), instruction.operand);
            stack_.push_back(property.value_or(runtime::Value::undefined()));
            break;
        }
        case Opcode::SetProperty: {
            if (instruction.operand >= module.names().size()) {
                return {.ok = false,
                        .value = runtime::Value::undefined(),
                        .error = "Bytecode property name index out of range"};
            }
            runtime::Value value;
            runtime::Value object;
            std::string error;
            if (!pop(value, error) || !pop(object, error)) {
                return {.ok = false, .value = runtime::Value::undefined(), .error = error};
            }
            if (object.type() != runtime::ValueType::Object) {
                return {.ok = false,
                        .value = runtime::Value::undefined(),
                        .error = "TypeError: cannot set property on non-object"};
            }
            if (!object.asObject()->setProperty(module.names()[instruction.operand], value)) {
                return {
                    .ok = false, .value = runtime::Value::undefined(), .error = "TypeError: property is not writable"};
            }
            propertyCaches_[ip].recordHit(object.asObject()->shapeId(), instruction.operand);
            stack_.push_back(value);
            break;
        }
        case Opcode::Pop:
            if (!stack_.empty()) {
                stack_.pop_back();
            }
            break;
        case Opcode::Return:
            return {.ok = true, .value = stack_.empty() ? runtime::Value::undefined() : stack_.back(), .error = ""};
        case Opcode::Nop:
            break;
        }
    }

    return {.ok = true, .value = runtime::Value::undefined(), .error = ""};
}

ExecutionResult VirtualMachine::declareName(const BytecodeModule& module, const Instruction& instruction,
                                            bool mutableBinding) {
    if (instruction.operand >= module.names().size()) {
        return {.ok = false, .value = runtime::Value::undefined(), .error = "Bytecode name index out of range"};
    }

    runtime::Value value;
    std::string error;
    if (!pop(value, error)) {
        return {.ok = false, .value = runtime::Value::undefined(), .error = error};
    }

    const auto& name = module.names()[instruction.operand];
    if (globals_.contains(name)) {
        return {
            .ok = false, .value = runtime::Value::undefined(), .error = "SyntaxError: " + name + " is already defined"};
    }

    globals_[name] = {.value = value, .mutableBinding = mutableBinding};
    stack_.push_back(value);
    return {.ok = true, .value = value, .error = ""};
}

ExecutionResult VirtualMachine::storeName(const BytecodeModule& module, const Instruction& instruction) {
    if (instruction.operand >= module.names().size()) {
        return {.ok = false, .value = runtime::Value::undefined(), .error = "Bytecode name index out of range"};
    }

    runtime::Value value;
    std::string error;
    if (!pop(value, error)) {
        return {.ok = false, .value = runtime::Value::undefined(), .error = error};
    }

    const auto& name = module.names()[instruction.operand];
    const auto found = globals_.find(name);
    if (found != globals_.end() && !found->second.mutableBinding) {
        return {.ok = false,
                .value = runtime::Value::undefined(),
                .error = "TypeError: assignment to constant variable " + name};
    }

    globals_[name] = {.value = value, .mutableBinding = true};
    stack_.push_back(value);
    return {.ok = true, .value = value, .error = ""};
}

bool VirtualMachine::pop(runtime::Value& value, std::string& error) {
    if (stack_.empty()) {
        error = "Bytecode stack underflow";
        return false;
    }
    value = stack_.back();
    stack_.pop_back();
    return true;
}

} // namespace jsengine::vm
