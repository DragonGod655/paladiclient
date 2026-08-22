#version 330 core

precision mediump float;

in vec2 v_WorldPos;
out vec4 color;

uniform sampler2D u_BlurTex;

layout (std140) uniform BlurSampleData {
    vec4 u_Rect;     // x1, y1, x2, y2 (framebuffer px)
    vec4 u_Corners;  // TL, TR, BL, BR
    vec4 u_Misc;     // screenW, screenH, opacity, _
};

float sdRoundedBox(vec2 p, vec2 halfSize, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.wz;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - halfSize + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 center   = (u_Rect.xy + u_Rect.zw) * 0.5;
    vec2 halfSize = (u_Rect.zw - u_Rect.xy) * 0.5;
    vec2 p = v_WorldPos - center;

    // Corners uniform is (TL, TR, BL, BR); sdRoundedBox wants (TR, BR, BL, TL).
    vec4 corners = vec4(u_Corners.y, u_Corners.w, u_Corners.z, u_Corners.x);

    float d = sdRoundedBox(p, halfSize, corners);
    float insideAA = 1.0 - smoothstep(-0.7, 0.7, d);
    if (insideAA <= 0.001) discard;

    vec2 screenUV = vec2(v_WorldPos.x / u_Misc.x, v_WorldPos.y / u_Misc.y);
    vec4 backdrop = texture(u_BlurTex, screenUV);

    color = vec4(backdrop.rgb, insideAA * u_Misc.z);
}
