#version 330 core
out vec4 FragColor;

// Inputs from vertex shader
in vec2 vTexCoords;
in vec3 vNormal;
in vec3 vFragPos;

// Uniforms
uniform sampler2D uTexture; // Texture sampler
uniform vec3 lightDir; // Directional light direction (normalized)
uniform vec3 lightColor; // Light color
uniform vec3 ambientColor; // Ambient light color
uniform float ambientStrength; // Ambient light strength

void main()
{
    // Sample the texture
    vec4 texColor = texture(uTexture, vTexCoords);
    
    // Normalize vectors
    vec3 normal = normalize(vNormal);
    vec3 lightDirection = normalize(-lightDir); // Negate because we want direction TO light
    
    // Calculate ambient lighting
    vec3 ambient = ambientStrength * ambientColor;
    
    // Calculate diffuse lighting
    float diff = max(dot(normal, lightDirection), 0.0);
    vec3 diffuse = diff * lightColor;
    
    // Combine lighting with texture
    vec3 result = (ambient + diffuse) * texColor.rgb;
    FragColor = vec4(result, texColor.a);
}
