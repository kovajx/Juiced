#version 330
#extension GL_ARB_separate_shader_objects : require

// Juiced "Bildeffekte": makes the picture more or less colorful.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SaturationConfig {
    float Saturation;
};

layout(location = 0) out vec4 fragColor;

const vec3 Luma = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float gray = dot(color, Luma);
    fragColor = vec4(clamp(mix(vec3(gray), color, Saturation), 0.0, 1.0), 1.0);
}
