// Clouds as volumes of mist: where they are, how dense, and how much sunlight reaches a point inside them.
// Shared by the cloud pass (which marches them) and the scene pass (their shadows on the land, the high clouds).

uniform highp sampler3D tNoise3D;
uniform sampler2D tNoise2D;
uniform vec4 uCloudA;   // coverage 0..1, kind (0 sheet .. 0.4 heaped .. 0.7 towers .. 1 thunderstorm), base (km), density
uniform vec4 uCloudB;   // middle layer: amount, form (0 flocks .. 0.5 rows .. 1 veil); clustering; anvils
uniform vec4 uCloudC;   // day's seed, cirrus amount, cirrus veil (cirrostratus), showers under the clouds
uniform vec4 uWind;     // how far the wind has carried the low clouds (km, xz) and the middle clouds (zw)

const float CLOUD_TOP_MAX = 9.5;      // the tallest tower, km above the cloud base
const float MID_BASE = 5.2;           // middle clouds, km
const float MID_TOP = 5.9;
const float HIGH_ALT = 9.5;           // ice clouds, km

// The weather seen from above: how much cloud stands over a place (x) and how tall it grows there (y).
vec2 weatherMap(vec2 xz) {
    vec2 q = xz + uWind.xy;
    vec2 so = uCloudC.x * vec2(0.37, 0.61);
    vec4 a = texture(tNoise2D, q / 61.0 + so);
    vec4 b = texture(tNoise2D, q / 23.0 + so.yx);
    float big = a.r * 0.7 + b.g * 0.3;                      // fields and gaps tens of kilometres across
    float cluster = uCloudB.z;
    float cov = uCloudA.x + (big - 0.5) * 2.2 * cluster * (0.35 + 0.65 * uCloudA.x);
    // A sheet is nearly whole; heaped clouds keep blue between them.
    float kind = clamp(uCloudA.y + (a.g - 0.5) * 0.55 + (big - 0.5) * 0.35, 0.0, 1.0);
    return vec2(clamp(cov, 0.0, 1.0), kind);
}

// How high the cloud tops stand above the base (km) for a kind of cloud.
float cloudTop(float kind) {
    float low = mix(0.5, 1.9, smoothstep(0.05, 0.45, kind));
    float tall = mix(4.4, CLOUD_TOP_MAX, smoothstep(0.72, 1.0, kind));
    return mix(low, tall, smoothstep(0.45, 0.72, kind));
}

float gCloudHf;     // set by lowDensity: how far up its cloud the last point was, 0 base .. 1 top
float gCloudCav;    // ... and how deep in a crevice between billows it lies, 0 crest .. 1 crevice

// The noise is turned off the camera's axes, so its tiling never lines clouds up towards the vanishing point.
vec3 cloudSpace(vec3 p) {
    return vec3(0.8829 * p.x - 0.4695 * p.z + uWind.x, p.y, 0.4695 * p.x + 0.8829 * p.z + uWind.y);
}

// Density of the low clouds at p (km; y is height above the ground), 0..1.
float lowDensity(vec3 p, float lod, bool fine) {
    float a = p.y - uCloudA.z;
    gCloudHf = 0.0;
    gCloudCav = 0.0;
    if (a < 0.0 || a > CLOUD_TOP_MAX) return 0.0;
    vec2 wm = weatherMap(p.xz);
    if (wm.x < 0.01) return 0.0;
    float top = cloudTop(wm.y);
    float hf = a / top;
    if (hf > 1.0) return 0.0;
    gCloudHf = hf;
    // A flat base, rounding towards the top; under the tropopause a thunderstorm spreads into an anvil.
    float crown = 1.0 - pow(hf, mix(2.4, 1.4, smoothstep(0.3, 0.8, wm.y)));
    float anvil = uCloudB.w * smoothstep(0.8, 1.0, wm.y) * smoothstep(0.70, 0.88, hf) * (1.0 - smoothstep(0.95, 1.0, hf));
    float c = saturate(wm.x * crown + anvil * 0.55);
    vec3 q = cloudSpace(p);
    vec4 n = textureLod(tNoise3D, q * (1.0 / 9.0) + vec3(0.0, uTime * 0.0006, 0.0), lod);
    float fbm = n.g * 0.625 + n.b * 0.25 + n.a * 0.125;
    // The volume's values crowd between 0.58 and 0.86; spread them over 0..1 so that coverage means what it says.
    float base = saturate((remap(n.r, fbm - 1.0, 1.0, 0.0, 1.0) - 0.58) / 0.28);
    // How far inside the cloud this point is (in units of the noise): positive within.
    float inside = base - (1.0 - c);
    // Sheets are soft and wide; heaped clouds have firm edges.
    float firm = mix(0.20, 0.055, smoothstep(0.1, 0.4, wm.y));
    if (inside <= -0.02) return 0.0;
    // Billows eat into the edges: wispy at the base, cauliflower on top. (Far from the eye, and for light that has
    // already crossed some cloud, their average is enough.)
    float eat = mix(0.30, 0.26, smoothstep(0.1, 0.4, wm.y)) * (1.0 - smoothstep(0.05, 0.6, inside));
    if (fine) {
        vec4 h = textureLod(tNoise3D, q * (1.0 / 2.3) + vec3(0.0, uTime * 0.004, 0.0), lod);
        // Cells of three sizes, each spread over 0..1: big billows, the lumps on them, the crumbs on those.
        float m1 = saturate((h.g - 0.28) / 0.50), m2 = saturate((h.b - 0.30) / 0.45), m3 = saturate((h.a - 0.32) / 0.40);
        float m = m1 * 0.55 + m2 * 0.30 + m3 * 0.15;
        // Turrets and hollows all over a heaped cloud, not only at its rim.
        inside += (0.5 - m1) * 0.20 * smoothstep(0.15, 0.4, wm.y) * saturate(hf * 3.0);
        m = mix(m, 1.0 - m, saturate(hf * 4.0));
        // Near the eye, finer crumbs still.
        if (lod < 0.5) {
            vec4 h3 = textureLod(tNoise3D, q * (1.0 / 0.61), 0.0);
            m = m * 0.82 + (1.0 - saturate((h3.b - 0.30) / 0.45)) * 0.18;
        }
        // The base is never a clean cut: pockets reach up into it.
        float under = 1.0 - saturate(a / 0.28);
        inside -= m * (eat + 0.30 * under);
        gCloudCav = m * max(1.0 - smoothstep(0.0, 0.45, inside), under * 0.8);
    } else {
        inside -= 0.5 * (eat + 0.30 * (1.0 - saturate(a / 0.28)));
    }
    float d = saturate(inside / firm) * saturate(a / 0.08);
    return d * mix(0.55, 1.0, smoothstep(0.0, 0.25, hf));
}

// Optical depth from p towards the light, through the low clouds (density x km).
float lightDepth(vec3 p, vec3 l) {
    float od = 0.0, t = 0.0, st = 0.05;
    for (int i = 0; i < 6; i++) {
        t += st;
        od += lowDensity(p + l * (t - 0.5 * st), 0.4 + float(i) * 0.5, i < 3) * st;
        st *= 1.8;
    }
    return od;
}

// Henyey-Greenstein times 4 pi: 1 for light scattered equally all round.
float hg(float mu, float g) { float g2 = g * g; return (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * mu, 1.5); }

// Sunlight leaving a point in a cloud towards the eye: straight through thin edges (the silver lining), scattered many
// times in the body. od is the optical depth towards the light, mu the cosine between the view and the Sun.
float cloudLight(float od, float mu) {
    // Only the thinnest veils pass light straight on, and only close to the Sun: the silver lining.
    float direct = exp(-od * 2.5) * min(hg(mu, 0.90), 12.0) * 0.5;
    float once = exp(-od * 0.7) * hg(mu, 0.5) * 0.35;
    // The body of a cloud is lit by light that has lost its direction: bright where it faces the Sun, grey behind.
    float many = exp(-od * 0.11) * (0.58 + 0.05 * hg(mu, -0.3));
    return direct + once + many;
}

// The middle clouds (altocumulus in flocks or rows, or an altostratus veil): thickness 0..1 at a place, seen from below.
float midCover(vec2 xz) {
    float amt = uCloudB.x;
    if (amt < 0.01) return 0.0;
    vec2 q = xz + uWind.zw;
    float form = uCloudB.y;
    vec2 so = uCloudC.x * vec2(0.71, 0.13);
    float big = texture(tNoise2D, q / 83.0 + so).g * 0.6 + texture(tNoise2D, q / 31.0 + so.yx).r * 0.4;
    // Rows: the cells drawn out across the wind.
    vec2 st = vec2(q.x * mix(1.0, 0.36, smoothstep(0.25, 0.6, form)), q.y);
    float cells = texture(tNoise2D, st / 6.3 + 0.37).b * 0.65 + texture(tNoise2D, st.yx / 3.7 + 0.11).b * 0.35;
    float soft = texture(tNoise2D, q / 19.0 + 0.53).r;
    float pattern = mix(cells, mix(cells, soft, 0.75), smoothstep(0.6, 1.0, form));
    float field = smoothstep(0.50 - 0.45 * amt, 0.78 - 0.45 * amt, big);
    float thresh = mix(0.62, 0.30, amt) - 0.18 * smoothstep(0.6, 1.0, form);
    return field * smoothstep(thresh, thresh + mix(0.22, 0.45, form), pattern);
}

// High ice clouds: fibres drawn out by the wind (cirrus), or a thin veil (cirrostratus). Thickness 0..1.
float highCover(vec2 xz) {
    float amt = uCloudC.y, veil = uCloudC.z;
    if (amt + veil < 0.01) return 0.0;
    vec2 q = xz + uWind.zw * 1.6;
    vec2 so = uCloudC.x * vec2(0.29, 0.47);
    float big = texture(tNoise2D, q / 190.0 + so).r;
    // Warp, then stretch along the wind: long combed streaks with hooked ends.
    vec2 w = vec2(texture(tNoise2D, q / 71.0 + 0.31).g, texture(tNoise2D, q / 71.0 + 0.77).r) - 0.5;
    vec2 s = vec2(q.x * 0.22 + q.y * 0.10, q.y - q.x * 0.05) + w * 14.0;
    float fib = texture(tNoise2D, s / 15.0).r * 0.55 + texture(tNoise2D, s / 5.1 + 0.4).g * 0.30 + texture(tNoise2D, s / 1.9 + 0.7).r * 0.15;
    float streaks = smoothstep(0.52 - 0.22 * amt, 0.86 - 0.20 * amt, fib) * smoothstep(0.62 - 0.5 * amt, 0.90 - 0.5 * amt, big * 0.6 + fib * 0.4);
    float sheet = veil * (0.55 + 0.45 * texture(tNoise2D, q / 47.0 + 0.2).g);
    return clamp(streaks * amt * 1.4 + sheet, 0.0, 1.0);
}

// How much sunlight the low clouds let down to a place on the ground (km), 1 = full Sun.
float cloudShadow(vec2 xz) {
    vec3 l = uLightDir;
    float ly = max(l.y, 0.14);
    float h = uCloudA.z + 0.45;
    vec2 at = xz + l.xz / ly * h;
    float d = lowDensity(vec3(at.x, h, at.y), 2.0, false);
    return exp(-d * uCloudA.w * 0.9);
}
