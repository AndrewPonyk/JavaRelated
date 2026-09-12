#include "jsengine/runtime/object.h"

#include <sstream>
#include <utility>

namespace jsengine::runtime {

bool Object::defineProperty(std::string name, PropertyDescriptor descriptor) {
    if (!properties_.contains(name)) {
        transitionShape(name);
        insertionOrder_.push_back(name);
    }
    properties_[std::move(name)] = std::move(descriptor);
    return true;
}

bool Object::setProperty(std::string name, Value value) {
    const auto found = properties_.find(name);
    if (found != properties_.end() && !found->second.writable) {
        return false;
    }

    return defineProperty(std::move(name), {.value = std::move(value)});
}

std::optional<Value> Object::getProperty(const std::string& name) const {
    const auto found = properties_.find(name);
    if (found == properties_.end()) {
        return std::nullopt;
    }
    return found->second.value;
}

std::vector<std::string> Object::ownPropertyNames() const {
    return insertionOrder_;
}

const std::string& Object::shapeId() const {
    return shapeId_;
}

std::string Object::inspect() const {
    std::ostringstream output;
    output << "{";
    bool first = true;
    for (const auto& name : insertionOrder_) {
        const auto found = properties_.find(name);
        if (found == properties_.end() || !found->second.enumerable) {
            continue;
        }
        if (!first) {
            output << ", ";
        }
        first = false;
        output << name << ": " << found->second.value.toString();
    }
    output << "}";
    return output.str();
}

void Object::transitionShape(const std::string& propertyName) {
    shapeId_ += "." + propertyName;
}

} // namespace jsengine::runtime
