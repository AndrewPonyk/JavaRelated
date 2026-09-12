#pragma once

#include "jsengine/runtime/value.h"

#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace jsengine::runtime {

struct PropertyDescriptor {
    Value value;
    bool writable{true};
    bool enumerable{true};
    bool configurable{true};
};

class Object {
  public:
    bool defineProperty(std::string name, PropertyDescriptor descriptor);
    bool setProperty(std::string name, Value value);
    std::optional<Value> getProperty(const std::string& name) const;
    std::vector<std::string> ownPropertyNames() const;
    const std::string& shapeId() const;
    std::string inspect() const;

  private:
    void transitionShape(const std::string& propertyName);

    std::string shapeId_{"root"};
    std::unordered_map<std::string, PropertyDescriptor> properties_;
    std::vector<std::string> insertionOrder_;
};

} // namespace jsengine::runtime
