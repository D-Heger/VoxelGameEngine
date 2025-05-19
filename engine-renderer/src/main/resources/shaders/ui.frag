#version 330 core
out vec4 FragColor;

in vec2 TexCoords;

uniform sampler2D uTexture; // Font atlas or element texture
uniform vec4 uColor;       // Base color for the element (e.g., text color)
uniform float uAlpha;      // Overall alpha for the element
uniform bool useTexture;   // New uniform

void main() {
    vec4 finalColor;
    if (useTexture) {
        // Sample texture (e.g., font atlas which is single channel GL_RED)
        float alphaFromTexture = texture(uTexture, TexCoords).r;
        // Gamma correction and smoothstep for better edge smoothing
        float gamma = 2.2;
        float correctedAlpha = pow(alphaFromTexture, 1.0 / gamma);
        // Optionally, use smoothstep for a soft edge
        correctedAlpha = smoothstep(0.05, 0.95, correctedAlpha);
        // Final color is uColor, with alpha modulated by texture's alpha and uAlpha
        finalColor = vec4(uColor.rgb, uColor.a * correctedAlpha * uAlpha);
    } else {
        // Use uColor directly, modulated by uAlpha
        finalColor = vec4(uColor.rgb, uColor.a * uAlpha);
    }
    FragColor = finalColor;
} 