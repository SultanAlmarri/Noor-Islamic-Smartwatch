// Red rock country: a desert floor of red sand and sagebrush, buttes and mesas of banded sandstone standing out of
// it, their cliffs glowing at sunrise and sunset. uLand.z dusts the ledges with snow.

const float LAND_STEP = 0.32;
const float LAND_TOP = 420.0;

float landWater() { return -1.0e6; }
float landObj(vec3 p) { return 1.0e6; }

// A flat-topped rock: sheer cliffs in ledges above, an apron of fallen rock below. Returns its height at p.
float mesa(vec2 p, vec2 c, vec2 r, float h, float seed) {
    vec2 q = (p - c) / r;
    if (dot(q, q) > 9.0) return 0.0;
    float w = (nA(p / (r.x * 3.1) + seed) - 0.5) * 0.9 + (nB(p / (r.x * 0.8) + seed) - 0.5) * 0.28;
    float d = length(q) + w * 0.42;
    float cliff = 1.0 - smoothstep(0.80, 1.0, d);
    // Ledges: the cliff climbs in steps.
    float f = cliff * 4.0;
    float st = (floor(f) + smoothstep(0.55, 1.0, fract(f))) / 4.0;
    float top = mix(cliff, st, 0.85);
    float apron = pow(1.0 - smoothstep(0.9, 2.3, d), 1.6);
    return h * (0.42 * apron + 0.58 * top) * (0.95 + 0.1 * nB(p / (r.x * 0.5)));
}

float floorH(vec2 p) { return (nA(p / 1300.0 + 0.4) - 0.5) * 22.0 + (nB(p / 160.0) - 0.5) * 2.2; }

float landH(vec2 p, float t) {
    float d = length(p);
    float h = floorH(p);
    // Dry washes cut the floor.
    h -= 3.5 * smoothstep(0.06, 0.0, abs(nA(p / 900.0 + 0.7) - 0.5)) * smoothstep(40.0, 200.0, d);
    // A butte and a spire in the middle distance, low enough for the Sun to pass over them.
    h = max(h, mesa(p, vec2(-25.0, 760.0), vec2(74.0, 60.0), 108.0, 0.11));
    h = max(h, mesa(p, vec2(118.0, 900.0), vec2(22.0, 20.0), 92.0, 0.53));
    h = max(h, mesa(p, vec2(-190.0, 1500.0), vec2(48.0, 44.0), 120.0, 0.83));
    // Mesa walls at the edges of the view.
    h = max(h, mesa(p, vec2(-610.0, 1250.0), vec2(260.0, 420.0), 230.0, 0.29));
    h = max(h, mesa(p, vec2(540.0, 1100.0), vec2(210.0, 300.0), 190.0, 0.67));
    // Far tablelands along the horizon (open where the Sun rises and sets).
    float far = smoothstep(5000.0, 9000.0, d);
    // (Most of the horizon is theirs: step behind step of them, of different heights, blue with distance.)
    float table = smoothstep(0.40, 0.46, nA(p / 14000.0 + 0.61)) * (0.45 + 0.30 * smoothstep(0.45, 0.5, nB(p / 5200.0)) + 0.25 * smoothstep(0.52, 0.56, nA(p / 6100.0 + 0.2)));
    h += far * sunGap(p) * table * 360.0;
    return h * smoothstep(0.0, 40.0, d);
}

void landMat(vec3 p, inout vec3 n, float t, bool isObj, out vec3 alb, out float kind, out float open) {
    float steep = 1.0 - smoothstep(0.55, 0.85, n.y);
    // Sandstone in beds: red, rust, a pale band, a dark one; streaked down the cliffs by desert varnish.
    float bed = p.y / 9.0 + (nA(p.xz / 240.0) - 0.5) * 1.5;
    float band = nB(vec2(bed * 0.07, 0.37));
    vec3 rock = mix(vec3(0.46, 0.17, 0.08), vec3(0.62, 0.29, 0.15), band);
    rock = mix(rock, vec3(0.70, 0.45, 0.30), smoothstep(0.72, 0.8, band));
    rock *= 1.0 - 0.35 * smoothstep(0.55, 0.75, nA(vec2(p.x + p.z, p.y * 0.08) / 23.0)) * steep;
    // The floor: red sand dotted with sagebrush.
    vec3 sand = mix(vec3(0.50, 0.22, 0.11), vec3(0.60, 0.31, 0.16), nB(p.xz / 70.0));
    float brush = smoothstep(0.60, 0.70, nC(p.xz / 9.0) * 0.7 + nG(p.xz / 2.3) * 0.3) * (1.0 - smoothstep(600.0, 3000.0, t));
    sand = mix(sand, vec3(0.10, 0.12, 0.07), brush * 0.85);
    float isRock = max(steep, smoothstep(14.0, 30.0, p.y - floorH(p.xz)));
    alb = mix(sand, rock, isRock);
    kind = isRock > 0.5 ? K_ROCK : K_SAND;
    // A dusting of snow on whatever is level.
    // (It thins out raggedly where the ground shows through, never along a clean edge.)
    float lie = nB(p.xz / 45.0) * 0.55 + nA(p.xz / 9.0) * 0.30 + nG(p.xz / 2.1) * 0.15;
    float snow = uLand.z * smoothstep(0.80, 0.93, n.y) * smoothstep(0.22, 0.62, lie + uLand.z * 0.5);
    alb = mix(alb, vec3(0.86, 0.88, 0.92), snow);
    if (snow > 0.5) kind = K_SNOW;
    open = mix(0.9, 0.6, steep);
}
