#pragma once

#include <memory>
#include <string>

namespace jsengine::runtime {

class Object;

enum class ValueType { Undefined, Null, Boolean, Number, String, Object };

class Value {
  public:
    Value();

    static Value undefined();
    static Value null();
    static Value boolean(bool value);
    static Value number(double value);
    static Value string(std::string value);
    static Value object(std::shared_ptr<Object> value);

    ValueType type() const;
    bool asBoolean() const;
    double asNumber() const;
    const std::string& asString() const;
    const std::shared_ptr<Object>& asObject() const;

    bool isTruthy() const;
    std::string toString() const;

  private:
    ValueType type_{ValueType::Undefined};
    bool boolean_{false};
    double number_{0.0};
    std::string string_;
    std::shared_ptr<Object> object_;
};

} // namespace jsengine::runtime
