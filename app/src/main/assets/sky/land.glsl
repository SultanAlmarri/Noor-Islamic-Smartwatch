// What every scenery's land is built from.

uniform sampler2D tNoise2D;
uniform vec4 uLand;      // scenery, season (0 midwinter .. 1 midsummer), snow lying, wetness
uniform float uGapAz;    // the Sun rises and sets this far (radians) to either side of the view's middle

// Smooth fields 0..1 (many octaves each), cells, and fine grain; one tile per unit.
float nA(vec2 p) { return textureLod(tNoise2D, p, 0.0).r; }
float nB(vec2 p) { return textureLod(tNoise2D, p, 0.0).g; }
float nC(vec2 p) { return textureLod(tNoise2D, p, 0.0).b; }
float nG(vec2 p) { return textureLod(tNoise2D, p, 0.0).a; }
vec2 rot2(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
float smax(float a, float b, float k) { return -smin(-a, -b, k); }

// The far skyline opens where the Sun rises and sets, so that it meets the true horizon at Shuruq and at Maghrib:
// 0 in those two gaps, 1 elsewhere.
float sunGap(vec2 xz) {
    float az = atan(xz.x, xz.y);
    return smoothstep(0.045, 0.13, abs(abs(az) - uGapAz));
}

// Kinds of surface (scene.frag lights each in its own way).
const float K_ROCK = 1.0, K_SAND = 2.0, K_SNOW = 3.0, K_ICE = 4.0, K_GRASS = 5.0, K_TREE = 6.0, K_WATER = 7.0;
