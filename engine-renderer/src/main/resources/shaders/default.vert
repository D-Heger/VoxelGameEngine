#version 330 core
layout (location = 0) in vec3 aPos; // Position attribute
layout (location = 1) in vec2 aTexCoords; // Texture coordinate attribute
layout (location = 2) in vec3 aNormal; // Normal attribute

// Outputs
out vec2 vTexCoords;
out vec3 vNormal;
out vec3 vFragPos;
out vec4 vFragPosLightSpace[4];

#define NUM_CASCADES 4

// UBO for Camera Data
layout (std140) uniform CameraData {
    mat4 projection;
    mat4 view;
    vec3 viewPos;
};

// Standard Uniforms
uniform mat4 model;
uniform mat4 lightSpaceMatrices[NUM_CASCADES];
uniform vec4 uAtlasOffsetScale; // xy = offset, zw = scale

void main()
{
    vec4 worldPos = model * vec4(aPos, 1.0);
    gl_Position = projection * view * worldPos;
    vTexCoords = aTexCoords * uAtlasOffsetScale.zw + uAtlasOffsetScale.xy;
    vNormal = mat3(transpose(inverse(model))) * aNormal; // Transform normal to world space
    vFragPos = vec3(worldPos); // World space position

    for (int i = 0; i < NUM_CASCADES; ++i) {
        vFragPosLightSpace[i] = lightSpaceMatrices[i] * worldPos;
    }
}
