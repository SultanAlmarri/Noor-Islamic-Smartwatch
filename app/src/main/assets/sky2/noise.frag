// Builds the noise the clouds and the land are shaped from, once, on the GPU (nothing is shipped as a picture).
//
// uMode 0: one slice of the cloud volume (tiles in x, y and z). R: Perlin-Worley (the heaped shape of a cloud),
//          G, B, A: Worley at rising frequency (the billows that erode its edges).
// uMode 1: the flat noise tile. R, G: two independent smooth fields, B: cells, A: fine grain.

uniform int uMode;
uniform float uSlice;   // 0..1 through the volume
uniform float uSize;    // texels along one edge
out vec4 o;

uint hu(uint x) { x ^= x >> 16; x *= 0x7feb352dU; x ^= x >> 15; x *= 0x846ca68bU; x ^= x >> 16; return x; }
vec3 h33(ivec3 p) {
    uint n = hu(uint(p.x) * 73856093U ^ hu(uint(p.y) * 19349663U ^ hu(uint(p.z) * 83492791U)));
    return vec3(float(n & 1023U), float((n >> 10) & 1023U), float((n >> 20) & 1023U)) / 1023.0;
}
ivec3 wrap(ivec3 p, int per) { return ((p % per) + per) % per; }

// Cells: 1 at a cell's point, falling to 0 between them. Tiles with period `per`.
float worley(vec3 p, int per) {
    ivec3 ip = ivec3(floor(p));
    vec3 fp = fract(p);
    float md = 1.0;
    for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        ivec3 c = ivec3(x, y, z);
        vec3 r = vec3(c) + h33(wrap(ip + c, per)) - fp;
        md = min(md, dot(r, r));
    }
    return 1.0 - sqrt(min(md, 1.0));
}
float worleyFbm(vec3 p, int f) {
    return worley(p * float(f), f) * 0.625 + worley(p * float(f * 2), f * 2) * 0.25 + worley(p * float(f * 4), f * 4) * 0.125;
}

// Gradient noise, -1..1, tiling with period `per`.
float gradn(vec3 p, int per) {
    ivec3 ip = ivec3(floor(p));
    vec3 f = fract(p);
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float v[8];
    for (int i = 0; i < 8; i++) {
        ivec3 c = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        vec3 g = normalize(h33(wrap(ip + c, per) + ivec3(17, 59, 83)) * 2.0 - 1.0 + 1e-4);
        v[i] = dot(g, f - vec3(c));
    }
    return mix(mix(mix(v[0], v[1], u.x), mix(v[2], v[3], u.x), u.y), mix(mix(v[4], v[5], u.x), mix(v[6], v[7], u.x), u.y), u.z) * 1.6;
}
float perlinFbm(vec3 p, int f, int octaves) {
    float a = 1.0, s = 0.0, n = 0.0;
    for (int i = 0; i < octaves; i++) {
        s += a * gradn(p * float(f), f);
        n += a;
        a *= 0.5;
        f *= 2;
    }
    return s / n;
}
float remap(float v, float a, float b, float c, float d) { return c + (v - a) / (b - a) * (d - c); }

void main() {
    vec2 uv = gl_FragCoord.xy / uSize;
    if (uMode == 0) {
        vec3 p = vec3(uv, uSlice);
        float pf = mix(1.0, perlinFbm(p, 4, 6), 0.5);
        pf = abs(pf * 2.0 - 1.0);
        float w4 = worleyFbm(p, 4), w8 = worleyFbm(p, 8), w16 = worleyFbm(p, 16);
        o = vec4(clamp(remap(pf, 0.0, 1.0, w4, 1.0), 0.0, 1.0), w4, w8, w16);
    } else {
        vec3 p = vec3(uv, 0.37);
        float a = perlinFbm(p, 4, 7) * 0.5 + 0.5;
        float b = perlinFbm(p + vec3(0.0, 0.0, 0.31), 8, 6) * 0.5 + 0.5;
        float c = worleyFbm(vec3(uv, 0.61), 6);
        vec3 g = h33(ivec3(gl_FragCoord.xy, 7));
        o = vec4(a, b, c, g.x);
    }
}
