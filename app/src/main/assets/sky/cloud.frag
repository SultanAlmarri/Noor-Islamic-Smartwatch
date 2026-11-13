// Marches the view through the clouds. The camera never moves and clouds drift slowly, so only one pixel in sixteen
// is redone each time (uAmort); the rest keep their last value. What is stored is light-independent of colour: how
// much sunlight and how much skylight each pixel's clouds send to the eye, so the colours of the moment (a reddening
// Sun, a violet sky) are applied when the picture is put together and follow the Sun at once.
//
// oA: sunlight from the low clouds, sunlight from the middle clouds and tall tops, skylight, what shows through.
// oB: how far the clouds are, as haze sees it (0 near .. 1 lost in the air), mean distance (per 200 km), -, -.

layout(location = 0) out vec4 oA;
layout(location = 1) out vec4 oB;

uniform int uAmort;
uniform float uSteps;     // 1 = the finest march; larger = coarser steps (lighter tiers)

const int BAYER[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);

float sphereExit(vec3 ro, vec3 rd, float r) {
    float b = dot(ro, rd);
    return -b + sqrt(max(b * b - (dot(ro, ro) - r * r), 0.0));
}

void main() {
    ivec2 ip = ivec2(gl_FragCoord.xy);
    if (uAmort > 1 && BAYER[(ip.x & 3) + ((ip.y & 3) << 2)] != (uFrame & 15)) discard;
    vec3 rd = viewRay(viewPx());
    oA = vec4(0.0, 0.0, 0.0, 1.0);
    oB = vec4(0.0);
    if (rd.y < 0.004) return;

    float eye = uCam.z * 0.001;
    vec3 ro = vec3(0.0, RE + eye, 0.0);
    float mu = dot(rd, uSunDir);
    vec3 L = uLightDir;
    float sigma = uCloudA.w;

    float T = 1.0, sunLow = 0.0, sunHigh = 0.0, amb = 0.0, aer = 0.0, dist = 0.0, wsum = 0.0;

    // Low clouds: heaped, towering, sheets.
    if (uCloudA.x > 0.005) {
        float base = uCloudA.z;
        float topMax = cloudTop(min(uCloudA.y + 0.45, 1.0));
        float t = sphereExit(ro, rd, RE + base);
        float tEnd = min(sphereExit(ro, rd, RE + base + topMax), 210.0);
        float jitter = ign(gl_FragCoord.xy);
        float ds = (0.011 * t + 0.05) * uSteps;
        t += ds * jitter;
        // Long strides through clear air; on meeting a cloud, a step back and short steps through it.
        bool stride = true;
        int clear = 0;
        for (int i = 0; i < 160; i++) {
            if (t > tEnd || T < 0.012) break;
            float unit = clamp(0.011 * t + 0.05, 0.07, 2.4) * uSteps;
            ds = unit * (stride ? 1.5 : 0.45);
            vec3 pw = ro + rd * t;
            vec3 p = vec3(pw.x, length(pw) - RE, pw.z);
            float lod = clamp(log2(ds / 0.10) * 0.6, 0.0, 3.0);
            float d = lowDensity(p, lod, t < 80.0);
            if (d > 0.0) {
                if (stride) { stride = false; clear = 0; t -= ds * 0.85; continue; }
                clear = 0;
                float hf = gCloudHf;
                float cav = gCloudCav;
                float od = lightDepth(p, L) * sigma;
                // The crevices between billows hold less light than their crests.
                float e = cloudLight(od, mu) * (1.0 - 0.55 * cav);
                // Skylight: more of it near the top, less in the cloud's own depth.
                float a = mix(0.22, 1.0, smoothstep(0.0, 0.8, hf)) * mix(1.0, 0.5, d) * (1.0 - 0.35 * cav);
                float st = d * sigma * ds;
                float w = T * (1.0 - exp(-st));
                // Tall tops stand in the light of higher air, which the Sun still reaches after it has left the ground.
                float high = smoothstep(2.6, 5.0, p.y);
                sunLow += w * e * (1.0 - high);
                sunHigh += w * e * high;
                amb += w * a;
                aer += w * (1.0 - exp(-t * 0.016 * uHaze));
                dist += w * t;
                wsum += w;
                T *= exp(-st);
            } else {
                clear++;
                if (clear > 5 || gCloudHf <= 0.0) stride = true;
            }
            t += ds;
        }
    }

    // Middle clouds, beyond the low ones: a thin layer, lit by how its thickness changes towards the Sun.
    if (uCloudB.x > 0.01 && T > 0.02) {
        float tm = sphereExit(ro, rd, RE + 0.5 * (MID_BASE + MID_TOP));
        if (tm < 260.0) {
            vec3 pw = ro + rd * tm;
            vec2 xz = pw.xz;
            float c = midCover(xz);
            if (c > 0.002) {
                float lit = midCover(xz + normalize(L.xz + 1e-5) * 0.35);
                float thick = c * mix(0.55, 1.0, uCloudB.y);
                float path = (MID_TOP - MID_BASE) / max(rd.y, 0.08);
                float alpha = 1.0 - exp(-thick * path * 2.2);
                float od = (thick * 2.4 + max(lit - c, 0.0) * 5.0) * mix(1.0, 0.45, saturate(L.y * 2.0));
                float e = cloudLight(od, mu);
                float w = T * alpha;
                sunHigh += w * e;
                amb += w * mix(0.55, 0.9, 1.0 - thick);
                aer += w * (1.0 - exp(-tm * 0.012 * uHaze));
                dist += w * tm;
                wsum += w;
                T *= 1.0 - alpha;
            }
        }
    }

    float inv = 1.0 / max(wsum, 1e-4);
    oA = vec4(sunLow, sunHigh, amb, T);
    oB = vec4(aer * inv, dist * inv / 200.0, wsum, 1.0);
}
