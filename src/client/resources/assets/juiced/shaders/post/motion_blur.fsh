#version 330
#extension GL_ARB_separate_shader_objects : require

// Juiced "Bildeffekte": blends the new frame with the previous result (motion blur),
// after adjusting its saturation.

uniform sampler2D InSampler;
uniform sampler2D PreviousSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform MotionBlurConfig {
    float Saturation;
    float Blend;
};

layout(location = 0) out vec4 fragColor;

const vec3 Luma = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float gray = dot(color, Luma);
    color = clamp(mix(vec3(gray), color, Saturation), 0.0, 1.0);

    // The previous frame is written with alpha 1. A fresh (cleared) target has alpha 0:
    // then there is no history yet and the new frame is shown as it is.
    vec4 previous = texture(PreviousSampler, texCoord);
    float weight = previous.a > 0.5 ? Blend : 0.0;
    fragColor = vec4(mix(color, previous.rgb, weight), 1.0);
}
