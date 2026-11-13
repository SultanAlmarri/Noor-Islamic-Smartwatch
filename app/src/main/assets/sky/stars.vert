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
uniform sampler2D tCloudA;
uniform sampler2D tG0;
out vec4 vCol;

void main() {
    vec3 dir = uCelX * aStar.x + uCelY * aStar.y + uCelZ * aStar.z;
    float pitch = atan((uCam.y - 0.5 * uView.y) / uCam.x);
    float c = cos(pitch), s = sin(pitch);
    vec3 d = vec3(dir.x, dir.y * c - dir.z * s, dir.z * c + dir.y * s);
    float k = uCam.x / max(d.z, 1e-4);
    float x = d.x * k;
    if (uCam.w > 0.5) x = -x;
    vec2 p = vec2(0.5 * uView.x + x, 0.5 * uView.y - d.y * k);
    float alt = asin(clamp(dir.y, -1.0, 1.0)) * 57.29578;
    bool vis = d.z > 0.0 && alt > 0.5 && p.x > 0.0 && p.x < uView.x && p.y > 0.0 && p.y < uView.y;
    vec2 uv = vec2(p.x / uView.x, 1.0 - p.y / uView.y);
    float through = vis ? textureLod(tCloudA, uv, 0.0).a : 0.0;
    float land = vis ? textureLod(tG0, uv, 0.0).a : 1.0;
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
