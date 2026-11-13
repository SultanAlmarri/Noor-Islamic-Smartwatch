// The Arctic: a frozen sea. Snow-covered floes with dark leads of open water between them, icebergs locked in the
// ice, an ice cap along the horizon. uLand.y is the season: 0 = midwinter (the sea frozen over), 1 = midsummer (open
// water, melt pools). The aurora belongs to the sky (scene.frag).

const float LAND_STEP = 0.45;
const float LAND_TOP = 900.0;

float landWater() { return 0.0; }
float landObj(vec3 p) { return 1.0e6; }

// An iceberg: a block with a sheer, fluted face and a broken top. Height at p.
float berg(vec2 p, vec2 c, vec2 r, float h, float seed) {
    vec2 q = (p - c) / r;
    if (dot(q, q) > 4.0) return -10.0;
    float w = (nA(p / (r.x * 2.3) + seed) - 0.5) * 0.9 + (nB(p / (r.x * 0.6) + seed) - 0.5) * 0.3;
    float d = length(q) + w * 0.5;
    float body = 1.0 - smoothstep(0.78, 1.0, d);
    // Pinnacles and a sloping top.
    float top = 0.62 + 0.38 * nA(p / (r.x * 2.6) + seed * 3.0) + 0.35 * pow(nA(p / (r.x * 1.9) + seed), 3.0);
    return mix(-10.0, h * top, body);
}

float landH(vec2 p, float t) {
    float d = length(p);
    float summer = uLand.y;
    // The pack: plates of ice, split by leads; fewer and smaller in summer.
    float plates = nA(p / 900.0 + 0.3) * 0.62 + nC(p / 260.0 + 0.1) * 0.38;
    float lead = smoothstep(0.05, 0.0, abs(nB(p / 1400.0 + 0.6) - 0.5));
    float thr = mix(0.43, 0.60, summer);
    float ice = smoothstep(thr, thr + 0.012, plates) * (1.0 - lead * (0.7 + 0.3 * summer));
    // Near the shore the ice is fast and whole.
    ice = max(ice, 1.0 - smoothstep(30.0, 90.0, d));
    float drift = (nB(p / 37.0) - 0.4) * 0.9 + (nA(p / 9.0) - 0.5) * 0.25;
    float h = mix(-6.0, 0.45 + max(drift, 0.0), ice);
    // Pressure ridges where plates have met.
    h += ice * 1.6 * smoothstep(0.035, 0.0, abs(nA(p / 210.0 + 0.9) - 0.5)) * nB(p / 11.0);
    // Icebergs: one in the middle distance, one large and near at the left edge, smaller ones beyond.
    h = max(h, berg(p, vec2(40.0, 560.0), vec2(62.0, 44.0), 36.0, 0.17));
    h = max(h, berg(p, vec2(-150.0, 330.0), vec2(48.0, 60.0), 44.0, 0.61));
    h = max(h, berg(p, vec2(250.0, 1000.0), vec2(60.0, 50.0), 24.0, 0.39));
    h = max(h, berg(p, vec2(-70.0, 1500.0), vec2(90.0, 60.0), 22.0, 0.83));
    // The ice cap and its mountains, far off (open where the Sun rises and sets).
    float far = smoothstep(12000.0, 22000.0, d);
    float m = nA(p / 21000.0 + 0.27);
    float ridge = 1.0 - abs(nB(p / 6000.0 + 0.4) * 2.0 - 1.0);
    h += far * sunGap(p) * (pow(m, 2.0) * 900.0 + ridge * ridge * 260.0 * m);
    // The hummock of old ice we stand on, above the pack.
    h = max(h, 11.0 * exp(-d * d / 3600.0) + 1.2 * (1.0 - smoothstep(60.0, 140.0, d)) + drift * 0.5 * (1.0 - smoothstep(60.0, 140.0, d)));
    return h;
}

void landMat(vec3 p, inout vec3 n, float t, bool isObj, out vec3 alb, out float kind, out float open) {
    float steep = 1.0 - smoothstep(0.45, 0.8, n.y);
    float high = smoothstep(4.0, 9.0, p.y) * (1.0 - smoothstep(3000.0, 9000.0, t));
    // Snow on everything level; bare blue ice on the bergs' faces, bluer in their clefts.
    vec3 snow = vec3(0.86, 0.89, 0.93) * (0.94 + 0.08 * nB(p.xz / 5.0));
    vec3 ice = mix(vec3(0.70, 0.84, 0.92), vec3(0.42, 0.66, 0.80), smoothstep(0.3, 0.8, nA(vec2(p.x + p.z, p.y * 2.0) / 260.0)));
    float bare = steep * high;
    alb = mix(snow, ice, bare);
    kind = bare > 0.5 ? K_ICE : K_SNOW;
    // Melt pools on the floes in summer: shallow, turquoise.
    float pool = uLand.y * smoothstep(0.66, 0.70, nC(p.xz / 23.0) * 0.6 + nA(p.xz / 61.0) * 0.4) * (1.0 - high) * step(p.y, 1.0) * smoothstep(60.0, 120.0, t);
    if (pool > 0.5) { alb = vec3(0.16, 0.50, 0.56); kind = K_ICE; n = vec3(0.0, 1.0, 0.0); }
    // Wind-packed snow has a grain (sastrugi) that catches low light.
    float near = 1.0 - smoothstep(15.0, 200.0, t);
    vec2 r = rot2(p.xz, 0.5);
    n = normalize(n + vec3(0.6, 0.0, 0.8) * (nA(vec2(r.x / 7.0, r.y / 1.3)) - 0.5) * 0.5 * near * (1.0 - bare));
    open = mix(0.95, 0.6, steep);
}
