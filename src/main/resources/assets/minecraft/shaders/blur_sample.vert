#version 330 core

precision mediump float;

layout (location = 0) in vec2 pos;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec2 v_WorldPos;

void main() {
    gl_Position = u_Proj * u_ModelView * vec4(pos, 0.0, 1.0);
    v_WorldPos = pos;
}
