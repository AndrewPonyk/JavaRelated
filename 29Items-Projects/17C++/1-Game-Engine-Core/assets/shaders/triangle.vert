#version 450
// triangle.vert — minimal Vulkan vertex shader (compiled to SPIR-V by glslc).
// Emits a single triangle with hard-coded positions/colors (no vertex buffer, no
// descriptors), so the offscreen pipeline needs no vertex input or descriptor sets.

layout(location = 0) out vec3 vColor;

vec2 positions[3] = vec2[](
    vec2( 0.0, -0.5),
    vec2( 0.5,  0.5),
    vec2(-0.5,  0.5)
);

vec3 colors[3] = vec3[](
    vec3(1.0, 0.0, 0.0),
    vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, 1.0)
);

void main() {
    gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
    vColor = colors[gl_VertexIndex];
}
