#include "jsengine/runtime/value.h"

#include "jsengine/runtime/object.h"

#include <cmath>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace jsengine::runtime {

Value::Value() = default;

Value Value::undefined() {
    return {};
}

Value Value::null() {
    Value value;
    value.type_ = ValueType::Null;
    return value;
}

Value Value::boolean(bool raw) {
    Value value;
    value.type_ = ValueType::Boolean;
    value.boolean_ = raw;
    return value;
}

Value Value::number(double raw) {
    Value value;
    value.type_ = ValueType::Number;
    value.number_ = raw;
    return value;
}

Value Value::string(std::string raw) {
    Value value;
    value.type_ = ValueType::String;
    value.string_ = std::move(raw);
    return value;
}

Value Value::object(std::shared_ptr<Object> raw) {
    if (raw == nullptr) {
        throw std::invalid_argument("object value cannot wrap a null pointer");
    }

    Value value;
    value.type_ = ValueType::Object;
    value.object_ = std::move(raw);
    return value;
}

ValueType Value::type() const {
    return type_;
}

bool Value::asBoolean() const {
    if (type_ != ValueType::Boolean) {
        throw std::logic_error("value is not a boolean");
    }
    return boolean_;
}

double Value::asNumber() const {
    if (type_ != ValueType::Number) {
        throw std::logic_error("value is not a number");
    }
    return number_;
}

const std::string& Value::asString() const {
    if (type_ != ValueType::String) {
        throw std::logic_error("value is not a string");
    }
    return string_;
}

const std::shared_ptr<Object>& Value::asObject() const {
    if (type_ != ValueType::Object) {
        throw std::logic_error("value is not an object");
    }
    return object_;
}

bool Value::isTruthy() const {
    switch (type_) {
    case ValueType::Undefined:
    case ValueType::Null:
        return false;
    case ValueType::Boolean:
        return boolean_;
    case ValueType::Number:
        return number_ != 0.0 && !std::isnan(number_);
    case ValueType::String:
        return !string_.empty();
    case ValueType::Object:
        return true;
    }
    return false;
}

std::string Value::toString() const {
    switch (type_) {
    case ValueType::Undefined:
        return "undefined";
    case ValueType::Null:
        return "null";
    case ValueType::Boolean:
        return boolean_ ? "true" : "false";
    case ValueType::Number: {
        std::ostringstream output;
        output << std::setprecision(15) << number_;
        return output.str();
    }
    case ValueType::String:
        return string_;
    case ValueType::Object:
        return object_->inspect();
    }
    return "undefined";
}

} // namespace jsengine::runtime
