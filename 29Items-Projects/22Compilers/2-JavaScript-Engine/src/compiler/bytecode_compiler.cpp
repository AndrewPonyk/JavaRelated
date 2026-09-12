#include "jsengine/compiler/bytecode_compiler.h"

#include <cstdlib>
#include <utility>

namespace jsengine::compiler {

namespace {

class CompilerImpl {
  public:
    CompileResult compile(const AstNode& program) {
        if (program.kind != AstKind::Program) {
            return {.ok = false, .module = {}, .error = "Compiler expected a Program node"};
        }

        for (std::size_t index = 0; index < program.children.size(); ++index) {
            const bool isLast = index + 1 == program.children.size();
            if (!compileStatement(program.children[index], isLast)) {
                return {.ok = false, .module = {}, .error = error_};
            }
        }

        module_.emit(vm::Opcode::LoadConst, module_.addConstant(runtime::Value::undefined()));
        module_.emit(vm::Opcode::Return);
        return {.ok = true, .module = std::move(module_), .error = ""};
    }

  private:
    bool compileStatement(const AstNode& statement, bool isLast) {
        switch (statement.kind) {
        case AstKind::VariableDeclaration:
            if (!compileExpression(statement.children.front())) {
                return false;
            }
            module_.emit(statement.constant ? vm::Opcode::DeclareConst : vm::Opcode::DeclareName,
                         module_.addName(statement.text));
            module_.emit(vm::Opcode::Pop);
            return true;
        case AstKind::ReturnStatement:
            if (!compileExpression(statement.children.front())) {
                return false;
            }
            module_.emit(vm::Opcode::Return);
            return true;
        case AstKind::ExpressionStatement:
            if (!compileExpression(statement.children.front())) {
                return false;
            }
            if (isLast) {
                module_.emit(vm::Opcode::Return);
            } else {
                module_.emit(vm::Opcode::Pop);
            }
            return true;
        default:
            return fail("Unsupported statement in compiler");
        }
    }

    bool compileExpression(const AstNode& expression) {
        switch (expression.kind) {
        case AstKind::Literal:
            module_.emit(vm::Opcode::LoadConst, module_.addConstant(literalValue(expression)));
            return true;
        case AstKind::Identifier:
            module_.emit(vm::Opcode::LoadName, module_.addName(expression.text));
            return true;
        case AstKind::AssignmentExpression:
            if (!compileExpression(expression.children.front())) {
                return false;
            }
            module_.emit(vm::Opcode::StoreName, module_.addName(expression.text));
            return true;
        case AstKind::PropertyAssignmentExpression:
            if (!compileExpression(expression.children[0]) || !compileExpression(expression.children[1])) {
                return false;
            }
            module_.emit(vm::Opcode::SetProperty, module_.addName(expression.text));
            return true;
        case AstKind::BinaryExpression:
            if (!compileExpression(expression.children[0]) || !compileExpression(expression.children[1])) {
                return false;
            }
            return emitBinary(expression.text);
        case AstKind::UnaryExpression:
            if (!compileExpression(expression.children.front())) {
                return false;
            }
            if (expression.text == "-") {
                module_.emit(vm::Opcode::Negate);
                return true;
            }
            return fail("Unsupported unary operator: " + expression.text);
        case AstKind::ObjectLiteral:
            module_.emit(vm::Opcode::MakeObject);
            for (const auto& property : expression.children) {
                if (!compileExpression(property.children.front())) {
                    return false;
                }
                module_.emit(vm::Opcode::DefineProperty, module_.addName(property.text));
            }
            return true;
        case AstKind::PropertyAccess:
            if (!compileExpression(expression.children.front())) {
                return false;
            }
            module_.emit(vm::Opcode::GetProperty, module_.addName(expression.text));
            return true;
        default:
            return fail("Unsupported expression in compiler");
        }
    }

    runtime::Value literalValue(const AstNode& literal) const {
        switch (literal.literalType) {
        case TokenType::Number:
            return runtime::Value::number(std::strtod(literal.text.c_str(), nullptr));
        case TokenType::String:
            return runtime::Value::string(literal.text);
        case TokenType::KeywordTrue:
            return runtime::Value::boolean(true);
        case TokenType::KeywordFalse:
            return runtime::Value::boolean(false);
        case TokenType::KeywordNull:
            return runtime::Value::null();
        case TokenType::KeywordUndefined:
            return runtime::Value::undefined();
        default:
            return runtime::Value::undefined();
        }
    }

    bool emitBinary(const std::string& op) {
        if (op == "+") {
            module_.emit(vm::Opcode::Add);
            return true;
        }
        if (op == "-") {
            module_.emit(vm::Opcode::Subtract);
            return true;
        }
        if (op == "*") {
            module_.emit(vm::Opcode::Multiply);
            return true;
        }
        if (op == "/") {
            module_.emit(vm::Opcode::Divide);
            return true;
        }
        return fail("Unsupported binary operator: " + op);
    }

    bool fail(std::string message) {
        error_ = std::move(message);
        return false;
    }

    vm::BytecodeModule module_;
    std::string error_;
};

} // namespace

CompileResult BytecodeCompiler::compile(const AstNode& program) const {
    CompilerImpl compiler;
    return compiler.compile(program);
}

} // namespace jsengine::compiler
