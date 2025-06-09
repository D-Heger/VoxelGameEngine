#version 330 core
layout (location = 0) in vec3 aPos; // Position attribute
layout (location = 1) in vec2 aTexCoords; // Texture coordinate attribute
layout (location = 2) in vec3 aNormal; // Normal attribute

// Outputs
out vec2 vTexCoords;
out vec3 vNormal;
out vec3 vFragPos;

// UBO for Camera Data
layout (std140) uniform CameraData {
    mat4 projection;
    mat4 view;
    vec3 viewPos;
};

// Standard Uniforms
uniform mat4 model;

void main()
{
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    vTexCoords = aTexCoords;
    vNormal = mat3(transpose(inverse(model))) * aNormal; // Transform normal to world space
    vFragPos = vec3(model * vec4(aPos, 1.0)); // Transform position to world space
}
