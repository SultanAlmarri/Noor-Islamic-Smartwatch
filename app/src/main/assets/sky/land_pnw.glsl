// The Pacific Northwest: a temperate rainforest. From a bluff among tall firs, a sea inlet runs out between ridge
// after ridge of conifers, and beyond them a snow-capped volcano stands alone on the horizon.
// uLand.y is the time of year (0 midwinter, 0.25 spring, 0.5 midsummer, 0.75 autumn); uLand.z is snow lying low.

const float LAND_STEP = 0.30;
const float LAND_TOP = 5200.0;

float landWater() { return 0.0; }

// The volcano: a broad cone, steeper towards the top, with shoulders and gullies.
float volcano(vec2 p) {
    vec2 c = vec2(1800.0, 46000.0);
    float r = length(p - c) / 11500.0;
    if (r > 1.3) return 0.0;
    float cone = pow(max(1.0 - r, 0.0), 1.55);
    float a = atan(p.x - c.x, p.y - c.y);
    float gully = (nA(vec2(a * 0.9, r * 1.6) + 0.3) - 0.5) * 0.16 * smoothstep(1.0, 0.25, r) * smoothstep(0.0, 0.2, r);
    float shoulder = 0.07 * exp(-sq((r - 0.42) / 0.12)) * (0.5 + nB(vec2(a * 0.5, 0.2)));
    return 4300.0 * max(cone + gully * cone + shoulder, 0.0);
}

// The ground without its trees.
float groundH(vec2 p) {
    float d = length(p);
    // The inlet: water down the middle, widening with distance, closed far off by low shores.
    float halfW = 330.0 + 0.085 * p.y + (nA(p / 2600.0 + 0.2) - 0.5) * 520.0;
    float side = abs(p.x - 60.0 * sin(p.y / 2300.0)) - halfW;
    float reach = smoothstep(7500.0, 10500.0, p.y);
    float shore = max(side, mix(-1.0e5, 600.0, reach));
    // Ridges: steep forested hills rising from the shore, higher further back.
    float ridges = (nA(p / 5200.0 + 0.6) * 0.65 + nB(p / 1700.0 + 0.1) * 0.35);
    float rise = smoothstep(-40.0, 1500.0, shore);
    float h = mix(-25.0, 40.0 + 720.0 * ridges * (0.5 + 0.9 * smoothstep(1000.0, 14000.0, d)), rise) * smoothstep(-60.0, 90.0, shore);
    h = shore < -60.0 ? -25.0 : max(h, -25.0 + (shore + 60.0) * 0.4);
    // Open towards sunrise and sunset beyond the near ridges.
    h = mix(h, min(h, 14.0), (1.0 - sunGap(p)) * smoothstep(900.0, 2200.0, d));
    h = max(h, volcano(p));
    // The bluff we stand on, falling to a shingle beach.
    float bluff = 26.0 - smoothstep(60.0, 200.0, p.y) * 30.0 - 0.0006 * p.x * p.x + (nB(p / 90.0) - 0.5) * 3.0;
    return max(h, bluff * (1.0 - smoothstep(180.0, 260.0, p.y)) - smoothstep(180.0, 260.0, p.y) * 30.0);
}

// How tall the forest stands at a place: firs and cedars 25 to 50 m, none on the beach, the snow or the rock.
float forest(vec2 p, float g) {
    float f = smoothstep(2.5, 9.0, g) * (1.0 - smoothstep(1250.0, 1650.0, g + (nB(p / 800.0) - 0.5) * 500.0));
    // The bluff's brow is open: a clearing in front of us, widening down to the beach.
    float open = smoothstep(0.0, 26.0, abs(p.x) - 30.0 - max(p.y, 0.0) * 0.36) + smoothstep(250.0, 300.0, p.y);
    return f * saturate(open) * smoothstep(-40.0, -10.0, p.y);
}

float landH(vec2 p, float t) {
    float g = groundH(p);
    float f = forest(p, g);
    if (f <= 0.0) return g;
    float tall = 26.0 + 22.0 * nB(p / 260.0 + 0.3);
    if (t > 2600.0) return g + f * tall * 0.62;
    // Each tree a spire; beyond a kilometre or two they blur into a rough canopy.
    vec2 id = floor(p / 8.5);
    vec2 c = (id + 0.25 + 0.5 * hash22(id)) * 8.5;
    float hgt = tall * (0.65 + 0.5 * hash12(id + 0.7));
    float r = length(p - c) / (hgt * 0.17);
    float spire = hgt * max(1.0 - r, 0.0);
    float canopy = tall * 0.45;
    return g + f * mix(max(spire, canopy * 0.6), tall * 0.62, smoothstep(1300.0, 2600.0, t));
}

// A tall fir: a straight trunk and whorls of drooping boughs, narrowing to a spire.
float fir(vec3 p, float h, float seed) {
    float y = p.y / h;
    if (y < -0.05 || y > 1.05) return max(abs(p.y - 0.5 * h) - 0.5 * h, 0.0) + 0.5;
    float rr = length(p.xz);
    float trunk = rr - mix(0.55, 0.05, saturate(y));
    float a = atan(p.x, p.z);
    float tiers = 15.0;
    float saw = fract(y * tiers + seed);
    float reach = h * 0.165 * pow(max(1.0 - y, 0.0), 0.85) * smoothstep(0.10, 0.22, y);
    reach *= 0.55 + 0.45 * saw + 0.22 * sin(a * 5.0 + floor(y * tiers) * 2.4 + seed * 9.0);
    return min(trunk, (rr - reach) * 0.55);
}

float landObj(vec3 p) {
    if (p.z > 120.0 || p.z < 5.0) return 60.0;
    // Firs framing the view at its left and right edges.
    float d = fir(p - vec3(-21.0, groundH(vec2(-21.0, 44.0)) - 1.0, 44.0), 47.0, 0.1);
    d = min(d, fir(p - vec3(-30.0, groundH(vec2(-30.0, 62.0)) - 1.0, 62.0), 41.0, 0.5));
    d = min(d, fir(p - vec3(22.5, groundH(vec2(22.5, 47.0)) - 1.0, 47.0), 44.0, 0.8));
    d = min(d, fir(p - vec3(33.0, groundH(vec2(33.0, 70.0)) - 1.0, 70.0), 50.0, 0.3));
    return d;
}

void landMat(vec3 p, inout vec3 n, float t, bool isObj, out vec3 alb, out float kind, out float open) {
    float year = uLand.y;
    float autumn = smoothstep(0.62, 0.74, year) * (1.0 - smoothstep(0.86, 0.95, year));
    float spring = smoothstep(0.15, 0.27, year) * (1.0 - smoothstep(0.40, 0.5, year));
    float g = groundH(p.xz);
    float f = forest(p.xz, g);
    // Evergreens: near-black green, a little bluer far off; fresh tips in spring; here and there a maple turned gold.
    vec3 green = mix(vec3(0.022, 0.050, 0.026), vec3(0.045, 0.085, 0.035), nB(p.xz / 41.0));
    green = mix(green, vec3(0.06, 0.13, 0.04), spring * 0.45 * nA(p.xz / 13.0));
    float maple = autumn * smoothstep(0.66, 0.72, nC(p.xz / 55.0) * 0.6 + nA(p.xz / 19.0) * 0.4) * (1.0 - smoothstep(60.0, 420.0, g));
    green = mix(green, mix(vec3(0.50, 0.30, 0.04), vec3(0.46, 0.12, 0.03), nB(p.xz / 9.0)), maple);
    if (isObj) {
        alb = green * 0.9; kind = K_TREE; open = 0.55;
        // Snow on the boughs in winter.
        float sn = uLand.z * smoothstep(0.1, 0.5, n.y);
        alb = mix(alb, vec3(0.80, 0.84, 0.90), sn * 0.7);
        return;
    }
    float steep = 1.0 - smoothstep(0.5, 0.8, n.y);
    // Above the trees: dark volcanic rock, then snow and ice down to a line that drops in winter.
    vec3 rock = mix(vec3(0.10, 0.09, 0.085), vec3(0.17, 0.15, 0.13), nB(p.xz / 300.0));
    float line = mix(2050.0, 1250.0, uLand.z) + (nA(p.xz / 1900.0) - 0.5) * 700.0;
    float snow = smoothstep(line - 150.0, line + 250.0, p.y) * (1.0 - 0.6 * smoothstep(0.55, 0.8, steep) * (1.0 - smoothstep(3000.0, 4000.0, p.y)));
    vec3 bare = mix(rock, vec3(0.88, 0.90, 0.94), snow);
    alb = mix(bare, green, f);
    kind = f > 0.5 ? K_TREE : (snow > 0.5 ? K_SNOW : K_ROCK);
    // Low snow on the forest in winter: the tops whitened.
    float dust = uLand.z * f * smoothstep(0.3, 0.8, n.y) * smoothstep(0.4, 0.6, nA(p.xz / 6.0) + uLand.z * 0.3);
    alb = mix(alb, vec3(0.78, 0.82, 0.88), dust * 0.75);
    // The beach: grey shingle and drift logs.
    float beach = (1.0 - f) * (1.0 - smoothstep(4.0, 12.0, g)) * (1.0 - snow);
    alb = mix(alb, vec3(0.20, 0.19, 0.18) * (0.7 + 0.6 * nG(p.xz / 2.0)), beach);
    open = mix(0.9, 0.5, f);
}
