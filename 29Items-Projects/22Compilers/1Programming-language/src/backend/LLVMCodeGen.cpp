#include "plang/backend/LLVMCodeGen.h"

#include <cmath>
#include <iomanip>
#include <limits>
#include <map>
#include <sstream>
#include <string>
#include <unordered_map>
#include <variant>

namespace plang::backend {

namespace {

struct Value {
    using Array = std::vector<Value>;
    using Record = std::map<std::string, Value>;

    std::variant<std::monostate, bool, long long, double, std::string, Array, Record> data;
};

using Environment = std::unordered_map<std::string, Value>;

bool isNumeric(const Value& value) {
    return std::holds_alternative<long long>(value.data) || std::holds_alternative<double>(value.data);
}

double asDouble(const Value& value) {
    if (const auto* integer = std::get_if<long long>(&value.data)) {
        return static_cast<double>(*integer);
    }
    return std::get<double>(value.data);
}

bool isTruthy(const Value& value) {
    if (std::holds_alternative<std::monostate>(value.data)) {
        return false;
    }
    if (const auto* boolean = std::get_if<bool>(&value.data)) {
        return *boolean;
    }
    if (const auto* integer = std::get_if<long long>(&value.data)) {
        return *integer != 0;
    }
    if (const auto* floating = std::get_if<double>(&value.data)) {
        return *floating != 0.0;
    }
    if (const auto* text = std::get_if<std::string>(&value.data)) {
        return !text->empty();
    }
    if (const auto* array = std::get_if<Value::Array>(&value.data)) {
        return !array->empty();
    }
    return !std::get<Value::Record>(value.data).empty();
}

std::string escape(const std::string& text) {
    std::ostringstream out;
    for (char ch : text) {
        switch (ch) {
        case '\\':
            out << "\\\\";
            break;
        case '\n':
            out << "\\n";
            break;
        case '\r':
            out << "\\r";
            break;
        case '\t':
            out << "\\t";
            break;
        case '"':
            out << "\\\"";
            break;
        default:
            out << ch;
            break;
        }
    }
    return out.str();
}

std::string render(const Value& value) {
    if (std::holds_alternative<std::monostate>(value.data)) {
        return "null";
    }
    if (const auto* boolean = std::get_if<bool>(&value.data)) {
        return *boolean ? "true" : "false";
    }
    if (const auto* integer = std::get_if<long long>(&value.data)) {
        return std::to_string(*integer);
    }
    if (const auto* floating = std::get_if<double>(&value.data)) {
        std::ostringstream out;
        out << std::setprecision(15) << *floating;
        return out.str();
    }
    if (const auto* text = std::get_if<std::string>(&value.data)) {
        return *text;
    }
    if (const auto* array = std::get_if<Value::Array>(&value.data)) {
        std::ostringstream out;
        out << '[';
        for (std::size_t index = 0; index < array->size(); ++index) {
            if (index > 0) {
                out << ", ";
            }
            out << render((*array)[index]);
        }
        out << ']';
        return out.str();
    }
    std::ostringstream out;
    out << '{';
    const auto& record = std::get<Value::Record>(value.data);
    std::size_t index = 0;
    for (const auto& [name, field] : record) {
        if (index++ > 0) {
            out << ", ";
        }
        out << name << ": " << render(field);
    }
    out << '}';
    return out.str();
}

Value numericBinary(const std::string& op, const Value& left, const Value& right) {
    const bool integral = std::holds_alternative<long long>(left.data) &&
                          std::holds_alternative<long long>(right.data) && op != "/";
    if (integral) {
        const long long lhs = std::get<long long>(left.data);
        const long long rhs = std::get<long long>(right.data);
        if (op == "+") {
            return Value{lhs + rhs};
        }
        if (op == "-") {
            return Value{lhs - rhs};
        }
        if (op == "*") {
            return Value{lhs * rhs};
        }
        if (op == "%") {
            return Value{rhs == 0 ? 0LL : lhs % rhs};
        }
    }

    const double lhs = asDouble(left);
    const double rhs = asDouble(right);
    if (op == "+") {
        return Value{lhs + rhs};
    }
    if (op == "-") {
        return Value{lhs - rhs};
    }
    if (op == "*") {
        return Value{lhs * rhs};
    }
    if (op == "/") {
        return Value{rhs == 0.0 ? std::numeric_limits<double>::quiet_NaN() : lhs / rhs};
    }
    if (op == "%") {
        return Value{std::fmod(lhs, rhs)};
    }
    return Value{};
}

bool equals(const Value& left, const Value& right) {
    if (left.data.index() != right.data.index()) {
        if (isNumeric(left) && isNumeric(right)) {
            return asDouble(left) == asDouble(right);
        }
        return false;
    }
    if (std::holds_alternative<std::monostate>(left.data)) {
        return true;
    }
    if (const auto* boolean = std::get_if<bool>(&left.data)) {
        return *boolean == std::get<bool>(right.data);
    }
    if (const auto* integer = std::get_if<long long>(&left.data)) {
        return *integer == std::get<long long>(right.data);
    }
    if (const auto* floating = std::get_if<double>(&left.data)) {
        return *floating == std::get<double>(right.data);
    }
    if (const auto* text = std::get_if<std::string>(&left.data)) {
        return *text == std::get<std::string>(right.data);
    }
    return render(left) == render(right);
}

Value callBuiltin(const std::string& name,
                  std::vector<Value> args,
                  std::vector<CodeGenDiagnostic>& diagnostics) {
    if (name == "len") {
        if (args.size() != 1) {
            diagnostics.push_back(CodeGenDiagnostic{"len expects exactly one argument."});
            return Value{};
        }
        if (const auto* text = std::get_if<std::string>(&args[0].data)) {
            return Value{static_cast<long long>(text->size())};
        }
        if (const auto* array = std::get_if<Value::Array>(&args[0].data)) {
            return Value{static_cast<long long>(array->size())};
        }
        diagnostics.push_back(CodeGenDiagnostic{"len expects a string or array."});
        return Value{};
    }
    if (name == "print") {
        return args.empty() ? Value{} : args.front();
    }
    diagnostics.push_back(CodeGenDiagnostic{"Unknown function '" + name + "'."});
    return Value{};
}

Value evaluate(const frontend::Expr& expr,
               Environment& environment,
               std::vector<CodeGenDiagnostic>& diagnostics);

Value evaluateCall(const frontend::CallExpr& call,
                   Environment& environment,
                   std::vector<CodeGenDiagnostic>& diagnostics,
                   std::vector<Value> prefixArgs = {}) {
    std::vector<Value> args = std::move(prefixArgs);
    for (const auto& arg : call.args) {
        args.push_back(evaluate(*arg, environment, diagnostics));
    }
    if (const auto* identifier = std::get_if<frontend::Identifier>(&call.callee->node)) {
        return callBuiltin(identifier->name, std::move(args), diagnostics);
    }
    diagnostics.push_back(CodeGenDiagnostic{"Only built-in function calls are supported."});
    return Value{};
}

Value evaluate(const frontend::Expr& expr,
               Environment& environment,
               std::vector<CodeGenDiagnostic>& diagnostics) {
    if (const auto* number = std::get_if<frontend::NumberLiteral>(&expr.node)) {
        if (number->isInteger) {
            return Value{static_cast<long long>(number->value)};
        }
        return Value{number->value};
    }
    if (const auto* text = std::get_if<frontend::StringLiteral>(&expr.node)) {
        return Value{text->value};
    }
    if (const auto* boolean = std::get_if<frontend::BoolLiteral>(&expr.node)) {
        return Value{boolean->value};
    }
    if (std::holds_alternative<frontend::NullLiteral>(expr.node)) {
        return Value{};
    }
    if (const auto* identifier = std::get_if<frontend::Identifier>(&expr.node)) {
        const auto found = environment.find(identifier->name);
        if (found == environment.end()) {
            diagnostics.push_back(CodeGenDiagnostic{"Unknown symbol '" + identifier->name + "'."});
            return Value{};
        }
        return found->second;
    }
    if (const auto* array = std::get_if<frontend::ArrayLiteral>(&expr.node)) {
        Value::Array values;
        values.reserve(array->elements.size());
        for (const auto& element : array->elements) {
            values.push_back(evaluate(*element, environment, diagnostics));
        }
        return Value{std::move(values)};
    }
    if (const auto* record = std::get_if<frontend::RecordLiteral>(&expr.node)) {
        Value::Record fields;
        for (const auto& field : record->fields) {
            fields[field.name] = evaluate(*field.value, environment, diagnostics);
        }
        return Value{std::move(fields)};
    }
    if (const auto* unary = std::get_if<frontend::UnaryExpr>(&expr.node)) {
        Value operand = evaluate(*unary->operand, environment, diagnostics);
        if (unary->op == "!") {
            return Value{!isTruthy(operand)};
        }
        if (!isNumeric(operand)) {
            diagnostics.push_back(CodeGenDiagnostic{"Unary '-' expects a numeric operand."});
            return Value{};
        }
        if (const auto* integer = std::get_if<long long>(&operand.data)) {
            return Value{-*integer};
        }
        return Value{-std::get<double>(operand.data)};
    }
    if (const auto* binary = std::get_if<frontend::BinaryExpr>(&expr.node)) {
        if (binary->op == "&&") {
            Value left = evaluate(*binary->left, environment, diagnostics);
            return Value{isTruthy(left) && isTruthy(evaluate(*binary->right, environment, diagnostics))};
        }
        if (binary->op == "||") {
            Value left = evaluate(*binary->left, environment, diagnostics);
            return Value{isTruthy(left) || isTruthy(evaluate(*binary->right, environment, diagnostics))};
        }
        if (binary->op == "|>") {
            Value piped = evaluate(*binary->left, environment, diagnostics);
            if (const auto* call = std::get_if<frontend::CallExpr>(&binary->right->node)) {
                return evaluateCall(*call, environment, diagnostics, {piped});
            }
            if (const auto* identifier = std::get_if<frontend::Identifier>(&binary->right->node)) {
                return callBuiltin(identifier->name, {piped}, diagnostics);
            }
            diagnostics.push_back(CodeGenDiagnostic{"Pipeline target must be a function call."});
            return Value{};
        }

        Value left = evaluate(*binary->left, environment, diagnostics);
        Value right = evaluate(*binary->right, environment, diagnostics);
        if (binary->op == "==" || binary->op == "!=") {
            const bool same = equals(left, right);
            return Value{binary->op == "==" ? same : !same};
        }
        if (binary->op == "<" || binary->op == "<=" || binary->op == ">" || binary->op == ">=") {
            if (!isNumeric(left) || !isNumeric(right)) {
                diagnostics.push_back(CodeGenDiagnostic{"Comparison expects numeric operands."});
                return Value{};
            }
            const double lhs = asDouble(left);
            const double rhs = asDouble(right);
            if (binary->op == "<") {
                return Value{lhs < rhs};
            }
            if (binary->op == "<=") {
                return Value{lhs <= rhs};
            }
            if (binary->op == ">") {
                return Value{lhs > rhs};
            }
            return Value{lhs >= rhs};
        }
        if (binary->op == "+" && std::holds_alternative<std::string>(left.data) &&
            std::holds_alternative<std::string>(right.data)) {
            return Value{std::get<std::string>(left.data) + std::get<std::string>(right.data)};
        }
        if (!isNumeric(left) || !isNumeric(right)) {
            diagnostics.push_back(CodeGenDiagnostic{"Arithmetic expects numeric operands."});
            return Value{};
        }
        return numericBinary(binary->op, left, right);
    }
    if (const auto* call = std::get_if<frontend::CallExpr>(&expr.node)) {
        return evaluateCall(*call, environment, diagnostics);
    }
    if (const auto* index = std::get_if<frontend::IndexExpr>(&expr.node)) {
        Value target = evaluate(*index->target, environment, diagnostics);
        Value indexValue = evaluate(*index->index, environment, diagnostics);
        if (!std::holds_alternative<long long>(indexValue.data)) {
            diagnostics.push_back(CodeGenDiagnostic{"Index must be an integer."});
            return Value{};
        }
        const long long offset = std::get<long long>(indexValue.data);
        if (const auto* array = std::get_if<Value::Array>(&target.data)) {
            if (offset < 0 || static_cast<std::size_t>(offset) >= array->size()) {
                diagnostics.push_back(CodeGenDiagnostic{"Array index out of range."});
                return Value{};
            }
            return (*array)[static_cast<std::size_t>(offset)];
        }
        if (const auto* text = std::get_if<std::string>(&target.data)) {
            if (offset < 0 || static_cast<std::size_t>(offset) >= text->size()) {
                diagnostics.push_back(CodeGenDiagnostic{"String index out of range."});
                return Value{};
            }
            return Value{std::string(1, (*text)[static_cast<std::size_t>(offset)])};
        }
        diagnostics.push_back(CodeGenDiagnostic{"Only arrays and strings can be indexed."});
        return Value{};
    }
    if (const auto* member = std::get_if<frontend::MemberExpr>(&expr.node)) {
        Value target = evaluate(*member->target, environment, diagnostics);
        const auto* record = std::get_if<Value::Record>(&target.data);
        if (record == nullptr) {
            diagnostics.push_back(CodeGenDiagnostic{"Member access expects a record."});
            return Value{};
        }
        const auto found = record->find(member->member);
        if (found == record->end()) {
            diagnostics.push_back(CodeGenDiagnostic{"Unknown record field '" + member->member + "'."});
            return Value{};
        }
        return found->second;
    }
    return Value{};
}

std::string resultKind(const Value& value) {
    if (std::holds_alternative<std::monostate>(value.data)) {
        return "unit";
    }
    if (std::holds_alternative<bool>(value.data)) {
        return "bool";
    }
    if (std::holds_alternative<long long>(value.data)) {
        return "int";
    }
    if (std::holds_alternative<double>(value.data)) {
        return "float";
    }
    return "string";
}

std::string resultPayload(const Value& value) {
    if (std::holds_alternative<std::monostate>(value.data)) {
        return "";
    }
    if (const auto* boolean = std::get_if<bool>(&value.data)) {
        return *boolean ? "true" : "false";
    }
    if (const auto* integer = std::get_if<long long>(&value.data)) {
        return std::to_string(*integer);
    }
    if (const auto* floating = std::get_if<double>(&value.data)) {
        std::ostringstream out;
        out << std::setprecision(17) << *floating;
        return out.str();
    }
    if (const auto* text = std::get_if<std::string>(&value.data)) {
        return escape(*text);
    }
    return escape(render(value));
}

} // namespace

CodeGenResult LLVMCodeGen::emitModule(const frontend::Module& module) const {
    Environment environment;
    std::vector<CodeGenDiagnostic> diagnostics;
    Value last;

    for (const auto& statement : module.statements) {
        if (const auto* let = std::get_if<frontend::LetStatement>(&statement.node)) {
            environment[let->name] = evaluate(*let->initializer, environment, diagnostics);
            last = environment[let->name];
        } else if (const auto* expression =
                       std::get_if<frontend::ExpressionStatement>(&statement.node)) {
            last = evaluate(*expression->expression, environment, diagnostics);
        }
    }

    std::ostringstream ir;
    ir << "; Programming Language executable IR\n";
    ir << "; statement_count = " << module.statements.size() << "\n";
    ir << "; plang.result.kind = " << resultKind(last) << "\n";
    ir << "; plang.result.value = " << resultPayload(last) << "\n";
    ir << "define double @main_expr() {\n";
    ir << "entry:\n";
    if (std::holds_alternative<long long>(last.data)) {
        ir << "  ret double " << static_cast<double>(std::get<long long>(last.data)) << "\n";
    } else if (std::holds_alternative<double>(last.data)) {
        ir << "  ret double " << std::setprecision(17) << std::get<double>(last.data) << "\n";
    } else {
        ir << "  ret double 0.000000e+00\n";
    }
    ir << "}\n";
    return CodeGenResult{ir.str(), std::move(diagnostics)};
}

} // namespace plang::backend
