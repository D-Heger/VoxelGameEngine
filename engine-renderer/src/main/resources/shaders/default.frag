#version 330 core
out vec4 FragColor;

// Inputs from vertex shader
in vec2 vTexCoords;

// Uniforms
uniform sampler2D uTexture; // Texture sampler
// uniform vec3 objectColor; // Keep if you want to tint the texture later
// uniform vec3 lightColor; // Keep if you want to apply lighting later

void main()
{
    // Sample the texture using the interpolated texture coordinates
    FragColor = texture(uTexture, vTexCoords);

    // Optional: Tint the texture
    // FragColor = texture(uTexture, vTexCoords) * vec4(objectColor, 1.0);

    // Optional: Apply simple lighting (needs lightColor uniform)
    // FragColor = texture(uTexture, vTexCoords) * vec4(lightColor, 1.0);
}
