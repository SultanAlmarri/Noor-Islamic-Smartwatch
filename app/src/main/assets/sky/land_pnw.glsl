// The Pacific Northwest: a temperate rainforest. From a bluff among tall firs, a sea inlet runs out between spur
// after spur of forested ridges, each paler than the last in the damp air, and beyond them a snow-capped volcano
// stands alone on the horizon.
// uLand.y is the time of year (0 midwinter, 0.25 spring, 0.5 midsummer, 0.75 autumn); uLand.z is snow lying low.

const float LAND_STEP = 0.30;
const float LAND_TOP = 5200.0;

float landWater() { return 0.0; }

// The volcano: a broad cone, steeper towards the top, with shoulders and gullies.
float volcano(vec2 p) {
    vec2 c = vec2(900.0, 38000.0);
    float r = length(p - c) / 9800.0;
    if (r > 1.3) return 0.0;
    float cone = pow(max(1.0 - r, 0.0), 1.5);
    float a = atan(p.x - c.x, p.y - c.y);
    float gully = (nA(vec2(a * 0.9, r * 1.6) + 0.3) - 0.5) * 0.20 * smoothstep(1.0, 0.25, r) * smoothstep(0.0, 0.2, r);
    float shoulder = 0.08 * exp(-sq((r - 0.42) / 0.12)) * (0.5 + nB(vec2(a * 0.5, 0.2)));
    return 4400.0 * max(cone + gully * cone + shoulder, 0.0);
}

// How far inland a place is from the shore of the inlet (m): negative on the water.
float inland(vec2 p) {
    // Spurs run out from either side in turn, so headland overlaps headland into the distance.
    float left = 240.0 + 0.055 * p.y + 260.0 * sin(p.y / 1450.0 + 0.6) + (nA(p / 2100.0 + 0.2) - 0.5) * 380.0;
    float right = 260.0 + 0.055 * p.y + 260.0 * sin(p.y / 1700.0 + 3.4) + (nA(p / 2300.0 + 0.7) - 0.5) * 380.0;
    float side = p.x < 0.0 ? -p.x - left : p.x - right;
    // The inlet ends far off against low shores.
    return max(side, (p.y - 9000.0) * 0.6);
}

// The ground without its trees.
float groundH(vec2 p) {
    float d = length(p);
    float shore = inland(p);
    // Hills climbing from the shore: low spurs near, real ridges further back and further off.
    float ridges = nA(p / 4600.0 + 0.6) * 0.6 + nB(p / 1500.0 + 0.1) * 0.4;
    float reach = 0.45 + 1.0 * smoothstep(800.0, 12000.0, d);
    float h = (26.0 + 900.0 * ridges * reach) * smoothstep(0.0, 700.0 + 0.1 * d, shore);
    h = mix(-30.0, h, smoothstep(-70.0, 30.0, shore)) + (shore > 0.0 ? shore * 0.035 : 0.0);
    // Open towards sunrise and sunset beyond the near spurs.
    h = mix(h, min(h, 10.0), (1.0 - sunGap(p)) * smoothstep(700.0, 1900.0, d));
    h = max(h, volcano(p));
    // The bluff we stand on, falling to a shingle beach.
    float fall = smoothstep(40.0, 190.0, p.y);
    float edge = smoothstep(170.0, 250.0, p.y);
    float bluff = mix(26.0 + (nB(p / 90.0) - 0.5) * 3.0 - 0.0005 * p.x * p.x, -30.0, edge) - fall * 24.0 * (1.0 - edge);
    return p.y < 250.0 ? max(h * smoothstep(120.0, 250.0, p.y), bluff) : h;
}

// How much forest stands at a place, 0..1: none on the beach, on the snow or the bare rock.
float forest(vec2 p, float g) {
    return smoothstep(2.5, 9.0, g) * (1.0 - smoothstep(1250.0, 1650.0, g + (nB(p / 800.0) - 0.5) * 500.0));
}

float landH(vec2 p, float t) {
    float g = groundH(p);
    float f = forest(p, g);
    if (f <= 0.0) return g;
    float tall = 28.0 + 22.0 * nB(p / 260.0 + 0.3);
    // In front of us the trees stand below the bluff: we look out over their tops.
    if (p.y < 260.0 && abs(p.x) < 40.0 + max(p.y, 0.0) * 0.5) tall = min(tall, max(21.0 - g, 0.0));
    if (t > 2600.0) return g + f * tall * 0.62;
    // Each tree a spire, a little irregular; beyond a kilometre or two they blur into a rough canopy.
    vec2 id = floor(p / 7.5);
    vec2 c = (id + 0.25 + 0.5 * hash22(id)) * 7.5;
    float hgt = tall * (0.6 + 0.55 * hash12(id + 0.7));
    float r = length(p - c) / (hgt * 0.15);
    float spire = hgt * max(1.0 - pow(r, 0.8), 0.0);
    return g + f * mix(max(spire, tall * 0.22), tall * 0.62, smoothstep(1300.0, 2600.0, t));
}

// A tall fir: a straight trunk, and boughs in ragged whorls that droop and shorten towards a pointed top.
float fir(vec3 p, float h, float seed) {
    float y = p.y / h;
    float rr = length(p.xz);
    if (y < -0.05 || y > 1.04 || rr > h * 0.2) return max(max(abs(p.y - 0.5 * h) - 0.52 * h, rr - h * 0.2), 0.0) + 0.6;
    float trunk = rr - mix(0.5, 0.04, saturate(y));
    float a = atan(p.x, p.z);
    // Boughs: each a lobe at its own height and bearing, longer low down, drooping at the tip.
    float droop = rr * 0.35 / h;
    float lev = (y + droop) * 26.0 + seed * 7.0 + 0.9 * sin(a * 3.0 + seed * 11.0) + 0.5 * sin(a * 7.0 + seed * 5.0);
    float bough = 0.5 + 0.5 * cos(lev * 6.2832);
    float lobes = 0.62 + 0.38 * sin(a * 5.0 + floor(lev) * 2.4) * sin(a * 2.0 - floor(lev) * 1.3);
    float taper = pow(max(1.0 - y, 0.0), 0.9) * smoothstep(0.06, 0.20, y);
    float reach = h * 0.118 * taper * (0.35 + 0.65 * pow(bough, 0.6)) * lobes;
    reach += (nC(vec2(a * 0.8 + seed, y * 9.0)) - 0.5) * taper;
    return min(trunk, (rr - reach) * 0.4);
}

float landObj(vec3 p) {
    // Far from these few trees, say how far (so rays to the distant ridges and the volcano are not held back).
    if (p.z > 130.0) return p.z - 105.0;
    if (p.z < 5.0) return 30.0 - p.z;
    // Firs framing the view at its left and right edges.
    float d = fir(p - vec3(-16.5, groundH(vec2(-16.5, 46.0)) - 1.0, 46.0), 50.0, 0.1);
    d = min(d, fir(p - vec3(-27.5, groundH(vec2(-27.5, 70.0)) - 1.0, 70.0), 46.0, 0.5));
    d = min(d, fir(p - vec3(-14.5, groundH(vec2(-14.5, 35.0)) - 1.0, 35.0), 27.0, 0.9));
    d = min(d, fir(p - vec3(17.5, groundH(vec2(17.5, 50.0)) - 1.0, 50.0), 47.0, 0.8));
    d = min(d, fir(p - vec3(29.5, groundH(vec2(29.5, 78.0)) - 1.0, 78.0), 56.0, 0.3));
    d = min(d, fir(p - vec3(15.5, groundH(vec2(15.5, 39.0)) - 1.0, 39.0), 25.0, 0.6));
    return d;
}

void landMat(vec3 p, inout vec3 n, float t, bool isObj, out vec3 alb, out float kind, out float open) {
    float year = uLand.y;
    float autumn = smoothstep(0.62, 0.74, year) * (1.0 - smoothstep(0.86, 0.95, year));
    float spring = smoothstep(0.15, 0.27, year) * (1.0 - smoothstep(0.40, 0.5, year));
    float g = groundH(p.xz);
    float f = forest(p.xz, g);
    // Evergreens: near-black green; fresh tips in spring; here and there a maple turned gold.
    vec3 green = mix(vec3(0.016, 0.036, 0.020), vec3(0.036, 0.070, 0.030), nB(p.xz / 41.0));
    green = mix(green, vec3(0.05, 0.11, 0.035), spring * 0.45 * nA(p.xz / 13.0));
    float maple = autumn * smoothstep(0.66, 0.72, nC(p.xz / 55.0) * 0.6 + nA(p.xz / 19.0) * 0.4) * (1.0 - smoothstep(60.0, 420.0, g));
    green = mix(green, mix(vec3(0.50, 0.30, 0.04), vec3(0.46, 0.12, 0.03), nB(p.xz / 9.0)), maple);
    if (isObj) {
        alb = green * (0.75 + 0.5 * nB(vec2(p.y * 0.21, p.x * 0.5)));
        kind = K_TREE; open = 0.5;
        // Snow on the boughs in winter.
        alb = mix(alb, vec3(0.80, 0.84, 0.90), uLand.z * smoothstep(0.1, 0.5, n.y) * 0.7);
        return;
    }
    float steep = 1.0 - smoothstep(0.5, 0.8, n.y);
    // Above the trees: dark volcanic rock, then snow and ice down to a line that drops in winter.
    vec3 rock = mix(vec3(0.085, 0.078, 0.075), vec3(0.15, 0.13, 0.12), nB(p.xz / 300.0));
    float line = mix(2050.0, 1150.0, uLand.z) + (nA(p.xz / 1900.0) - 0.5) * 700.0;
    float snow = smoothstep(line - 150.0, line + 250.0, p.y) * (1.0 - 0.6 * smoothstep(0.55, 0.8, steep) * (1.0 - smoothstep(3000.0, 4000.0, p.y)));
    vec3 bare = mix(rock, vec3(0.90, 0.92, 0.95), snow);
    alb = mix(bare, green, f);
    kind = f > 0.5 ? K_TREE : (snow > 0.5 ? K_SNOW : K_ROCK);
    // Low snow on the forest in winter: the tops whitened.
    float dust = uLand.z * f * smoothstep(0.3, 0.8, n.y) * smoothstep(0.4, 0.6, nA(p.xz / 6.0) + uLand.z * 0.3);
    alb = mix(alb, vec3(0.78, 0.82, 0.88), dust * 0.75);
    // The beach: grey shingle. The open brow of the bluff: moss and fern.
    float beach = (1.0 - f) * (1.0 - smoothstep(4.0, 12.0, g)) * (1.0 - snow);
    alb = mix(alb, vec3(0.17, 0.165, 0.16) * (0.7 + 0.6 * nG(p.xz / 2.0)), beach);
    float brow = (1.0 - f) * smoothstep(12.0, 18.0, g) * (1.0 - smoothstep(300.0, 500.0, t));
    alb = mix(alb, mix(vec3(0.030, 0.055, 0.018), vec3(0.06, 0.10, 0.03), nA(p.xz / 3.0)), brow);
    if (brow > 0.5) { kind = K_GRASS; n = normalize(n + vec3(nA(p.xz / 0.7) - 0.5, 0.0, nB(p.xz / 0.7) - 0.5) * 0.8); }
    open = mix(0.9, 0.45, f);
}
