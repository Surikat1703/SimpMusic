#include <flutter/runtime_effect.glsl>

uniform vec2 uResolution;
uniform float uTime;
uniform float uBpmSpeed;
uniform float uAmplitude;
uniform vec3 uColor1;
uniform vec3 uColor2;

out vec4 fragColor;

// Simplex noise function
vec3 permute(vec3 x) { return mod(((x*34.0)+1.0)*x, 289.0); }
float snoise(vec2 v){
  const vec4 C = vec4(0.211324865405187, 0.366025403784439,
                   -0.577350269189626, 0.024390243902439);
  vec2 i  = floor(v + dot(v, C.yy) );
  vec2 x0 = v -   i + dot(i, C.xx);
  vec2 i1;
  i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
  + i.x + vec3(0.0, i1.x, 1.0 ) );
  vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
  m = m*m ;
  m = m*m ;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );
  vec3 g;
  g.x  = a0.x  * x0.x  + h.x  * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

void main() {
    vec2 uv = FlutterFragCoord().xy / uResolution.xy;

    // Анимация времени с учетом BPM
    float t = uTime * 0.3 * uBpmSpeed;

    // Деформация от шума и баса
    float noise1 = snoise(uv * 2.0 + vec2(t * 0.5, t * 0.3));
    float noise2 = snoise(uv * 3.0 - vec2(t * 0.2, noise1));

    // Пульсация от амплитуды (громкости)
    float wave = noise2 + (uAmplitude * 0.3 * sin(uv.x * 10.0 + t * 5.0));

    // Смешивание цветов
    vec3 color = mix(uColor1, uColor2, clamp(wave + 0.5, 0.0, 1.0));

    // Затемнение по краям (виньетка)
    float vignette = uv.x * (1.0 - uv.x) * uv.y * (1.0 - uv.y) * 15.0;
    vignette = clamp(pow(vignette, 0.5), 0.0, 1.0);

    fragColor = vec4(color * vignette, 1.0);
}
