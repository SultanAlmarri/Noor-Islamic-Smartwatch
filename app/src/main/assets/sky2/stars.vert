// The catalogue's stars (the 1,600 brighter than magnitude 5), each a point fixed to the celestial sphere and turned
// to where it stands for this place and moment. A star behind a cloud or behind the land is not drawn.
layout(location = 0) in vec3 aStar;     // unit vector: x to right ascension 0, y to 6 h, z to the celestial north pole
layout(location = 1) in vec4 aLook;     // magnitude, colour

uniform vec2 uRes;
uniform vec2 uView;
uniform vec4 uCam;
uniform float uTime;
uniform float uNight;
uniform vec3 uCelX, uCelY, uCelZ;
uniform sampler2D tCloud0, tCloud1, tCloud2;
uniform vec4 uCloudF0, uCloudF1, uCloudF2, uCloudD;
uniform sampler2D tLandA;
uniform vec4 uLandFrame;
out vec4 vCol;

float gap(sampler2D t, vec4 f, float slide, vec2 vp) {
    float k = f.x / uView.x;
    vec2 uv = vec2(0.5 + ((vp.x - 0.5 * uView.x) * k + slide) / f.z, (f.y + (vp.y - uCam.y) * k) / f.w);
    if (uv.y >= 1.0 || uv.y <= 0.0) return 1.0;
    return 1.0 - textureLod(t, vec2(uv.x, 1.0 - uv.y), 0.0).a;
}

void main() {
    vec3 dir = uCelX * aStar.x + uCelY * aStar.y + uCelZ * aStar.z;
    // The same level, shifted-lens camera as common.glsl's.
    float k = uCam.x / max(dir.z, 1e-4);
    float x = dir.x * k;
    if (uCam.w > 0.5) x = -x;
    vec2 p = vec2(0.5 * uView.x + x, uCam.y - dir.y * k);
    vec3 d = dir;
    float alt = asin(clamp(dir.y, -1.0, 1.0)) * 57.29578;
    bool vis = d.z > 0.0 && alt > 0.5 && p.x > 0.0 && p.x < uView.x && p.y > 0.0 && p.y < uView.y;
    vec2 uv = vec2(p.x / uView.x, 1.0 - p.y / uView.y);
    float through = !vis ? 0.0 : uCloudD.w < 0.5 ? 1.0 : gap(tCloud0, uCloudF0, uCloudD.x, p) * gap(tCloud1, uCloudF1, uCloudD.y, p) * gap(tCloud2, uCloudF2, uCloudD.z, p);
    float lk = uLandFrame.x / uView.x;
    float lx = p.x * lk;
    if (uCam.w > 0.5) lx = uLandFrame.x - lx;
    vec2 luv = vec2(lx / uLandFrame.x, (uLandFrame.y + (p.y - uCam.y) * lk - uLandFrame.z) / uLandFrame.w);
    float land = !vis ? 1.0 : (luv.y > 0.0 ? textureLod(tLandA, luv, 0.0).a : 0.0);
    // Each magnitude is 2.5 times dimmer; the air near the horizon dims them further.
    float air = pow(clamp((alt - 0.5) / 12.0, 0.0, 1.0), 0.7);
    float flux = min(pow(10.0, -0.4 * (aLook.x - 1.0)), 4.0) * air;
    float a = min(0.12 + 0.60 * sqrt(flux), 1.0);
    float id = float(gl_VertexID);
    float tw = 0.82 + 0.18 * sin(uTime * (2.0 + 5.0 * fract(id * 0.618)) + id) * smoothstep(45.0, 8.0, alt);
    vCol = vec4(aLook.yzw * a * tw * uNight * uNight * through * step(land, 0.03), 1.0);
    gl_Position = vis ? vec4(uv * 2.0 - 1.0, 0.0, 1.0) : vec4(2.0, 2.0, 2.0, 1.0);
    gl_PointSize = (2.4 + 3.4 * pow(flux, 0.4)) * uRes.x / 1080.0;
}
