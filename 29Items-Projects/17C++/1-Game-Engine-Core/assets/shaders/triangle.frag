#version 450
// triangle.frag — minimal Vulkan fragment shader (compiled to SPIR-V by glslc).

layout(location = 0) in vec3 vColor;
layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(vColor, 1.0);
}
