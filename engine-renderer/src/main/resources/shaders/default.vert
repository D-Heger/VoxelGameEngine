#version 330 core
layout (location = 0) in vec3 aPos; // Position attribute
layout (location = 1) in vec2 aTexCoords; // Texture coordinate attribute

// Outputs
out vec2 vTexCoords;

// Uniforms
uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main()
{
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    vTexCoords = aTexCoords; // Pass texture coordinates to fragment shader
}
