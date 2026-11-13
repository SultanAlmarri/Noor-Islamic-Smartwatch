// Arabian dunes: a sand sea seen from the crest of a high dune. Long ridges with a gentle windward slope and a sharp
// brink, smaller dunes riding on them, ripples underfoot, and great star dunes standing on the horizon.

const float LAND_STEP = 0.6;
const float LAND_TOP = 320.0;

float landWater() { return -1.0e6; }
float landObj(vec3 p) { return 1.0e6; }

// One dune across its width, 0..1: a long rise to a rounded crest, then the short, steep slip face.
float dune(float x) {
    float f = fract(x);
    const float w = 0.84;
    return f < w ? 0.5 - 0.5 * cos(PI * f / w) : pow((1.0 - f) / (1.0 - w), 1.25);
}

float landH(vec2 p, float t) {
    float d = length(p);
    // Near the camera the field is tamed, so that we always stand on a brink: s is 0 underfoot, 1 out in the sand sea.
    float s = smoothstep(50.0, 420.0, d);
    // The great ridges, sinuous, running away to the left: their slip faces look towards the evening Sun.
    vec2 q = rot2(p, 0.92);
    float warp = mix(200.0, 200.0 + (nA(p / 2300.0 + 0.13) - 0.5) * 620.0 + (nB(p / 700.0) - 0.5) * 70.0, s);
    // The dune underfoot is the highest for a long way: the sand sea lies below us.
    float size = mix(1.9, 0.6 + 0.85 * nA(p / 3900.0 + 0.5), smoothstep(80.0, 700.0, d));
    float ph = (q.y + warp) / 440.0 + 0.38;
    float h = 32.0 * size * dune(ph);
    // Smaller dunes ride up their windward slopes at an angle.
    vec2 q2 = rot2(p, 0.25);
    float warp2 = (nB(p / 340.0 + 0.7) - 0.5) * 70.0;
    float onSlope = smoothstep(0.08, 0.35, fract(ph)) * (1.0 - smoothstep(0.60, 0.78, fract(ph)));
    h += 3.4 * dune((q2.y + warp2) / 96.0) * onSlope * (0.3 + 0.7 * nA(p / 800.0));
    h += (nA(p / 1700.0 + 0.3) - 0.5) * 10.0 * s;
    // Far away, star dunes stand on the horizon (not where the Sun rises and sets).
    float far = smoothstep(2200.0, 5200.0, d);
    float star = pow(nA(p / 9000.0 + 0.21), 2.2) * 2.4;
    h += far * sunGap(p) * 170.0 * star * (0.6 + 0.4 * dune((q.y + warp) / 1900.0));
    return h;
}

void landMat(vec3 p, inout vec3 n, float t, bool isObj, out vec3 alb, out float kind, out float open) {
    // Sand: warm gold, a little redder and darker in the troughs where the coarse grains gather.
    float v = nB(p.xz / 190.0) * 0.6 + nA(p.xz / 37.0) * 0.4;
    alb = mix(vec3(0.50, 0.27, 0.12), vec3(0.60, 0.35, 0.165), v);
    alb *= 0.94 + 0.12 * nG(p.xz / 3.1);
    // Wind ripples underfoot, across the wind; they fade with distance and on the steep slip faces.
    float near = 1.0 - smoothstep(8.0, 55.0, t);
    if (near > 0.0) {
        vec2 r = rot2(p.xz, 0.35);
        float w = (nA(p.xz / 6.0) - 0.5) * 2.0;
        float ph = r.y * 24.0 + w * 9.0;
        float slope = cos(ph) * 0.11 * near * smoothstep(0.80, 0.93, n.y);
        n = normalize(n + vec3(sin(0.35), 0.0, cos(0.35)) * slope);
    }
    kind = K_SAND;
    open = 0.75 + 0.25 * smoothstep(-0.3, 0.6, n.y);
}
