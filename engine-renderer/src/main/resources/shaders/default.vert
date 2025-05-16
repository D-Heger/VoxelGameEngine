#version 330 core
layout (location = 0) in vec3 aPos; // Position attribute
layout (location = 1) in vec2 aTexCoords; // Texture coordinate attribute
layout (location = 2) in vec3 aNormal; // Normal attribute

// Outputs
out vec2 vTexCoords;
out vec3 vNormal;
out vec3 vFragPos;

// Uniforms
uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main()
{
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    vTexCoords = aTexCoords;
    vNormal = mat3(transpose(inverse(model))) * aNormal; // Transform normal to world space
    vFragPos = vec3(model * vec4(aPos, 1.0)); // Transform position to world space
}
