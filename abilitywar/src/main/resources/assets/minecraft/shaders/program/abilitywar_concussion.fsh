#version 150

uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float VoidAmount;
uniform float Time;
uniform vec3 TintDark;
uniform vec3 TintLight;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    float s = clamp(Strength, 0.0, 1.0);
    vec2 uv = texCoord;
    vec2 c = uv - 0.5;
    // 울렁임
    uv += vec2(sin(uv.y * 11.0 + Time * 2.3), cos(uv.x * 9.0 + Time * 1.9)) * 0.0035 * s;
    vec3 base = texture(DiffuseSampler, uv).rgb;
    // 흐림
    vec3 blur = vec3(0.0);
    float rad = 4.0 * s;
    for (int i = 0; i < 12; i++) {
        float a = float(i) / 12.0 * 6.2831853;
        float r = rad * (1.0 + float(i - (i / 3) * 3));
        blur += texture(DiffuseSampler, uv + vec2(cos(a), sin(a)) * oneTexel * r).rgb;
    }
    blur /= 12.0;
    vec3 col = mix(base, blur, clamp(s * 1.3, 0.0, 1.0));
    // 겹쳐 보임
    vec2 off = vec2(0.014 * sin(Time * 0.8), 0.007 * cos(Time * 1.1)) * s;
    col = mix(col, texture(DiffuseSampler, uv + off).rgb, 0.38 * s);
    // 색수차
    float ca = 0.006 * s;
    col.r = mix(col.r, texture(DiffuseSampler, uv + c * ca).r, s);
    col.b = mix(col.b, texture(DiffuseSampler, uv - c * ca).b, s);
    // 채도/가장자리
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(l), 0.35 * s);
    float vig = smoothstep(0.95, 0.25, length(c) * 1.4);
    col *= mix(1.0, vig, 0.65 * s);
    // 공허 색조
    float v = clamp(VoidAmount, 0.0, 1.0);
    float vl = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 voidCol = mix(TintDark, TintLight, pow(vl, 1.35));
    col = mix(col, voidCol, v * 0.62);
    col *= mix(1.0, smoothstep(1.1, 0.15, length(c) * 1.55), v * 0.7);
    fragColor = vec4(col, 1.0);
}
