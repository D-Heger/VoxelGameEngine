#version 330 core
out vec4 FragColor;

// Using an undefined function should definitely cause a link error
vec4 someUndefinedFunction();

void main() {
    FragColor = someUndefinedFunction();
}
