// Noor real sky (1.0): what every pass shares. GLSL ES 3.00; the design bench (tools/sky3d) compiles these very files.
//
// Space: x right, y up, z forward (into the picture), kilometres for the sky and the clouds, metres for the land.
// The camera never moves: it stands uCam.z metres above the ground looking along +z, pitched so that the horizon
// lies uCam.y pixels from the top of the view.

#define PI 3.14159265359
#define DEG 0.01745329252

uniform vec2 uRes;        // size of the target this pass draws into, px
uniform vec2 uView;       // size of the whole scene on screen, px
uniform float uTime;      // seconds, for drift and shimmer
uniform int uFrame;       // counts cloud updates
uniform vec4 uCam;        // focal length (px), horizon (px from the top), eye height (m), 1 = mirrored (right to left)
uniform vec3 uSunDir;     // where the Sun is drawn
uniform vec3 uLightDir;   // where sunlight (or, at night, moonlight) falls from
uniform vec3 uSunTrue;    // the Sun at its true elevation, at the drawn azimuth: what the air does to its light
uniform float uSunEl;     // true elevation, degrees
uniform vec4 uMoon;       // direction it is drawn in (xyz), how much of it is lit (w)
uniform vec4 uMoonLit;    // sunlight's direction in the moon's own frame (xyz), how visible it is (w)
uniform float uNight;     // 0 by day, 1 when the last twilight has gone
uniform float uHaze;      // 1 = clean air; more for dust and humid haze

float saturate(float x) { return clamp(x, 0.0, 1.0); }
float remap(float v, float a, float b, float c, float d) { return c + (v - a) / (b - a) * (d - c); }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float sq(float x) { return x * x; }

// Position in the view, px from the top-left corner, for a fragment of a pass of any size.
vec2 viewPx() {
    vec2 p = gl_FragCoord.xy * (uView / uRes);
    return vec2(p.x, uView.y - p.y);
}

float camPitch() { return atan((uCam.y - 0.5 * uView.y) / uCam.x); }

// The direction seen at a point of the view.
vec3 viewRay(vec2 vp) {
    float x = vp.x - 0.5 * uView.x;
    if (uCam.w > 0.5) x = -x;
    vec3 d = normalize(vec3(x, 0.5 * uView.y - vp.y, uCam.x));
    float pitch = camPitch();
    float c = cos(pitch), s = sin(pitch);
    return vec3(d.x, d.y * c + d.z * s, d.z * c - d.y * s);
}

// Where a direction falls in the view (px from the top-left); z < 0 when it is behind the camera.
vec3 viewPoint(vec3 dir) {
    float pitch = camPitch();
    float c = cos(pitch), s = sin(pitch);
    vec3 d = vec3(dir.x, dir.y * c - dir.z * s, dir.z * c + dir.y * s);
    float k = uCam.x / max(d.z, 1e-4);
    float x = d.x * k;
    if (uCam.w > 0.5) x = -x;
    return vec3(0.5 * uView.x + x, 0.5 * uView.y - d.y * k, d.z);
}

// Interleaved gradient noise: a different, well-spread value at every pixel.
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

// The filmic curve of the 0.9.6 sky (Atmosphere.tone): linear light in, display values out.
vec3 acesc(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
float aces1(float x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
vec3 toneLinear(vec3 c) {
    c = max(c, 0.0);
    float l = luma(c);
    float s = l > 1e-6 ? aces1(l) / l : 0.0;
    vec3 o = min(c * s, 1.0) * 0.7 + acesc(c) * 0.3;
    float y = luma(o);
    return clamp(vec3(y) + (o - y) * 1.22, 0.0, 1.0);
}
vec3 srgb(vec3 x) { return mix(12.92 * x, 1.055 * pow(max(x, 1e-5), vec3(1.0 / 2.4)) - 0.055, step(0.0031308, x)); }
