#version 330 core
out vec4 FragColor;

// Inputs from vertex shader
in vec2 vTexCoords;
in vec3 vNormal;
in vec3 vFragPos;

// UBO for Camera Data (includes view position)
layout (std140) uniform CameraData {
    mat4 projection; // Not used in frag, but part of the shared block
    mat4 view;       // Not used in frag, but part of the shared block
    vec3 viewPos;
};

// UBO for Lighting Data
layout (std140) uniform Lighting {
    vec3 lightDir;
    float pad1; // Padding for std140 alignment
    vec3 lightColor;
    float pad2; // Padding for std140 alignment
    vec3 ambientColor;
    float ambientStrength;
    vec3 fogColor;
    float fogStart;
    float fogEnd;
    float pad3; // Padding for std140 alignment
};

// Standard Uniforms
uniform sampler2D uTexture; // Texture sampler

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
    // vec3 viewDir = normalize(viewPos - vFragPos);
    // vec3 halfwayDir = normalize(lightDirection + viewDir);
    // float spec = pow(max(dot(normal, halfwayDir), 0.0), shininess);
    // vec3 specular = specularStrength * spec * lightColor; // Specular highlight is also affected by light color
    
    // Combine lighting with texture
    vec3 result = (ambient + diffuse) * texColor.rgb;

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
