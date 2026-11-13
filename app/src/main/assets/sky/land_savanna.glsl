// Savanna: grass to the horizon under an enormous sky, a lone acacia, a few more far off, low hills at the edge of
// the world. uLand.y is how green the season is (0 = the gold of the dry season, 1 = after the rains).

const float LAND_STEP = 0.75;
const float LAND_TOP = 420.0;

float landWater() { return -1.0e6; }

float groundH(vec2 p) {
    float d = length(p);
    float h = (nA(p / 2100.0 + 0.2) - 0.5) * 16.0 + (nB(p / 340.0) - 0.5) * 2.4;
    // Low hills and lone rocks at the edge of the plain (not where the Sun rises and sets).
    float far = smoothstep(4500.0, 9000.0, d);
    h += far * sunGap(p) * pow(nA(p / 8200.0 + 0.37), 3.0) * 520.0;
    return h * smoothstep(0.0, 60.0, d);
}
float landH(vec2 p, float t) { return groundH(p); }

float capsule(vec3 p, vec3 a, vec3 b, float ra, float rb) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - mix(ra, rb, h);
}
float ellipsoid(vec3 p, vec3 r) { float k = length(p / r); return (k - 1.0) * min(r.x, min(r.y, r.z)); }

// An umbrella thorn: a leaning trunk that forks, and a wide flat crown in tiers. `s` scales it.
float acacia(vec3 p, float s, float seed) {
    p /= s;
    if (length(p - vec3(0.0, 5.0, 0.0)) > 11.0) return (length(p - vec3(0.0, 5.0, 0.0)) - 9.5) * s;
    float d = capsule(p, vec3(0.0, -0.3, 0.0), vec3(0.5, 3.6, 0.1), 0.34, 0.22);
    d = min(d, capsule(p, vec3(0.5, 3.6, 0.1), vec3(-2.6, 6.3, 0.6), 0.2, 0.09));
    d = min(d, capsule(p, vec3(0.5, 3.6, 0.1), vec3(3.2, 6.5, -0.5), 0.2, 0.09));
    d = min(d, capsule(p, vec3(0.45, 3.2, 0.1), vec3(0.6, 6.9, -1.6), 0.17, 0.08));
    d = min(d, capsule(p, vec3(-1.3, 5.0, 0.4), vec3(-4.6, 6.6, -0.6), 0.11, 0.05));
    d = min(d, capsule(p, vec3(1.8, 5.2, -0.2), vec3(5.2, 6.9, 0.8), 0.11, 0.05));
    // The crown: flat tiers, broken up into leaf masses with sky between them.
    float c = ellipsoid(p - vec3(-2.4, 7.0, 0.3), vec3(4.4, 0.75, 3.6));
    c = min(c, ellipsoid(p - vec3(2.9, 7.3, -0.3), vec3(4.6, 0.8, 3.8)));
    c = min(c, ellipsoid(p - vec3(0.4, 7.9, -0.8), vec3(3.6, 0.7, 3.2)));
    vec2 q = p.xz * 0.21 + seed;
    c += (0.5 - nC(q)) * 1.1 + (0.5 - nB(q * 3.1)) * 0.5 + abs(p.y - 7.3) * 0.12;
    return min(d, c * 0.7) * s;
}

float landObj(vec3 p) {
    // The lone tree, a little left of the middle; two more further off, towards the edges.
    float d = acacia(p - vec3(-3.0, groundH(vec2(-3.0, 38.0)), 38.0), 1.0, 0.13);
    if (p.z > 80.0) {
        d = min(d, acacia(p - vec3(70.0, groundH(vec2(70.0, 190.0)), 190.0), 0.9, 0.71));
        d = min(d, acacia(p - vec3(-150.0, groundH(vec2(-150.0, 330.0)), 330.0), 1.15, 0.43));
        // Scattered trees out on the plain.
        vec2 id = floor(p.xz / 170.0);
        float r = hash12(id + 3.7);
        if (r > 0.62 && length(id) > 2.5) {
            vec2 c = (id + 0.3 + 0.4 * hash22(id)) * 170.0;
            d = min(d, acacia(p - vec3(c.x, groundH(c), c.y), 0.7 + 0.5 * r, r * 9.0));
        } else d = min(d, 60.0);
    }
    return d;
}

void landMat(vec3 p, inout vec3 n, float t, bool isObj, out vec3 alb, out float kind, out float open) {
    float green = uLand.y;
    if (isObj) {
        // Bark below, leaves above.
        float yy = p.y - groundH(p.xz);
        bool leaf = yy > 5.0 * (t > 100.0 ? 0.6 : 1.0) && abs(n.y) + nB(p.xz * 0.9) > 0.35;
        alb = leaf ? mix(vec3(0.060, 0.085, 0.025), vec3(0.085, 0.14, 0.03), green) * (0.7 + 0.6 * nB(p.xz * 1.7)) : vec3(0.075, 0.055, 0.04);
        kind = K_TREE;
        open = leaf ? 0.8 : 0.6;
        return;
    }
    // Grass in tussocks: gold in the dry season, green after the rains; bare red earth shows between.
    float tuft = nA(p.xz / 4.3) * 0.5 + nG(p.xz / 1.1) * 0.5;
    float patchy = nB(p.xz / 150.0) * 0.6 + nA(p.xz / 31.0) * 0.4;
    vec3 dry = mix(vec3(0.36, 0.27, 0.10), vec3(0.50, 0.40, 0.17), tuft);
    vec3 lush = mix(vec3(0.07, 0.13, 0.03), vec3(0.16, 0.25, 0.06), tuft);
    alb = mix(dry, lush, saturate(green * 1.3 - 0.3 * patchy));
    alb = mix(alb, vec3(0.30, 0.15, 0.08), smoothstep(0.62, 0.74, patchy) * 0.6 * (1.0 - 0.5 * green));
    float far = smoothstep(4500.0, 9000.0, t);
    alb = mix(alb, vec3(0.20, 0.17, 0.12), far * 0.6);
    // Tall grass stands rough: it catches low light.
    float near = 1.0 - smoothstep(10.0, 140.0, t);
    n = normalize(n + vec3(nA(p.xz / 0.9) - 0.5, 0.0, nB(p.xz / 0.9) - 0.5) * 0.9 * near);
    kind = K_GRASS;
    open = 0.85;
}
