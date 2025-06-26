#version 330 core
layout (location = 0) out vec4 FragColor;
layout (location = 1) out vec3 gPosition; // location 1
layout (location = 2) out vec3 gNormal;   // location 2

// Inputs from vertex shader
in vec2 vTexCoords;
in vec3 vNormal;
in vec3 vFragPos;
in vec4 vFragPosLightSpace[4];

#define NUM_CASCADES 4

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

// Specular parameters
uniform float specularStrength;
uniform float shininess;

// Cascaded shadow uniforms
uniform float cascadeSplits[NUM_CASCADES];
uniform vec4 cascadeAtlasRects[NUM_CASCADES];

// Standard Uniforms
uniform sampler2D uTexture; // Texture sampler
uniform sampler2D shadowMap; // Shadow map sampler (atlas)

// Percentage-closer filtering with a 5x5 kernel and adaptive bias.
float calculateShadow(int cascadeIdx, vec3 projCoords, float bias) {
    // If fragment is outside the light's orthographic frustum
    if (projCoords.z > 1.0)
        return 0.0;

    // Transform coords from [0,1] cascade space to atlas space
    vec4 rect = cascadeAtlasRects[cascadeIdx];
    vec2 uv = projCoords.xy * rect.zw + rect.xy;

    // Compute texel size for this cascade region
    vec2 texelSize = rect.zw / textureSize(shadowMap, 0);

    float shadow = 0.0;
    // 5x5 weighted kernel (Gaussian-like weights for smoother result)
    float kernel[5] = float[](0.06, 0.11, 0.17, 0.11, 0.06);
    float totalWeight = 0.0;
    for (int x = -2; x <= 2; ++x) {
        for (int y = -2; y <= 2; ++y) {
            float weight = kernel[x + 2] * kernel[y + 2];
            float pcfDepth = texture(shadowMap, uv + vec2(x, y) * texelSize).r;
            shadow += weight * (projCoords.z - bias > pcfDepth ? 1.0 : 0.0);
            totalWeight += weight;
        }
    }

    return shadow / totalWeight;
}

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
    vec3 specular = specularStrength * spec * lightColor;
    
    // Adaptive bias reduces shadow acne and peter-panning on grazing angles
    float bias = max(0.002 * (1.0 - dot(normal, lightDirection)), 0.0005);

    // Determine which cascade this fragment belongs to based on distance from camera
    float fragDist = length(vFragPos - viewPos);
    int cascadeIdx = 0;
    if (fragDist > cascadeSplits[0]) cascadeIdx = 1;
    if (fragDist > cascadeSplits[1]) cascadeIdx = 2;
    if (fragDist > cascadeSplits[2]) cascadeIdx = 3;

    vec3 projCoords = vFragPosLightSpace[cascadeIdx].xyz / vFragPosLightSpace[cascadeIdx].w;
    projCoords = projCoords * 0.5 + 0.5; // Transform to [0,1] range

    // Combine lighting with texture taking shadow into account
    float shadowFactor = 1.0 - calculateShadow(cascadeIdx, projCoords, bias);
    vec3 lighting = ambient + diffuse * shadowFactor + specular * shadowFactor;
    vec3 result = lighting * texColor.rgb;

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

    // Output G-Buffer data
    gPosition = vec3(view * vec4(vFragPos, 1.0)); // view-space position
    gNormal = normalize(mat3(view) * vNormal); // view-space normal
}
