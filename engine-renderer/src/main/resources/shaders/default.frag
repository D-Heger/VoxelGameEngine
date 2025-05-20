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
uniform vec3 viewPos; // Camera position in world space
uniform float specularStrength; // Intensity of the specular highlight
uniform float shininess; // Shininess factor for specular highlight

// Fog Uniforms
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;

void main()
{
    // Sample the texture
    vec4 texColor = texture(uTexture, vTexCoords, -0.5);
    
    // Normalize vectors
    vec3 normal = normalize(vNormal);
    vec3 lightDirection = normalize(-lightDir); // Negate because we want direction TO light
    
    // Calculate ambient lighting
    vec3 ambient = ambientStrength * ambientColor;
    
    // Calculate diffuse lighting
    float diff = max(dot(normal, lightDirection), 0.0);
    vec3 diffuse = diff * lightColor;
    
    // Calculate specular lighting (Blinn-Phong)
    vec3 viewDir = normalize(viewPos - vFragPos);
    vec3 halfwayDir = normalize(lightDirection + viewDir);
    float spec = pow(max(dot(normal, halfwayDir), 0.0), shininess);
    vec3 specular = specularStrength * spec * lightColor; // Specular highlight is also affected by light color
    
    // Combine lighting with texture
    vec3 result = (ambient + diffuse + specular) * texColor.rgb;

    // --- Fog Calculation ---
    // Calculate distance from camera (viewPos) to the fragment's world position (vFragPos)
    float dist = length(vFragPos - viewPos);
    // Calculate fogFactor: a value between 0.0 (no fog) and 1.0 (full fog).
    // smoothstep provides a smooth transition from no fog to full fog between fogStart and fogEnd distances.
    // - If dist < fogStart, fogFactor is 0.0.
    // - If dist > fogEnd, fogFactor is 1.0.
    // - If fogStart <= dist <= fogEnd, fogFactor transitions smoothly from 0.0 to 1.0.
    float fogFactor = smoothstep(fogStart, fogEnd, dist); // Linear fog: 0 (no fog) to 1 (full fog)

    // Mix the original fragment color with the fogColor based on fogFactor.
    // mix(x, y, a) returns x * (1.0 - a) + y * a
    // So, if fogFactor is 0, finalColor is result. If fogFactor is 1, finalColor is fogColor.
    vec3 finalColorWithFog = mix(result, fogColor, fogFactor);

    FragColor = vec4(finalColorWithFog, texColor.a);
}
