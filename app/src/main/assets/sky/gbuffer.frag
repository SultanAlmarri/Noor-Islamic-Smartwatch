// The land, drawn once. The camera never moves and the ground does not change, so the expensive part (finding what
// every pixel sees, in three dimensions) is done a single time per scenery and stored: its colour and kind, the way
// it faces, how far it is, and how high the land stands between it and the Sun's part of the sky (so shadows can be
// cast every frame at no cost). Each frame then only relights it (scene.frag).
//
// A scenery supplies (land_*.glsl): landH (ground height, m), landObj (distance to solid things standing on it: trees,
// rocks), landWater (the water's level, or far below for none) and landMat (its colour and kind).

layout(location = 0) out vec4 o0;   // colour (square root), kind / 8
layout(location = 1) out vec4 o1;   // normal * 0.5 + 0.5, how open to the sky
layout(location = 2) out vec4 o2;   // distance (log, 16 bits in two channels), land's height towards the Sun's left and right limits

uniform vec4 uTile;                 // only this band of rows is drawn now: first row, one past the last (px of this target)

const float FAR = 90000.0;
const float SUN_AZ = 0.30;          // the Sun never stands further than this (radians) to either side of the view

vec2 packDist(float t) {
    float v = clamp(log2(max(t, 1.0)) / 17.0, 0.0, 1.0) * 65535.0;
    float hi = floor(v / 256.0);
    return vec2(hi, v - hi * 256.0) / 255.0;
}

float sceneD(vec3 p, float t) {
    return min((p.y - landH(p.xz, t)) * LAND_STEP, landObj(p));
}

vec3 landNormal(vec3 p, float t) {
    float e = max(0.02, t * 0.0007);
    float h0 = landH(p.xz, t);
    float hx = landH(p.xz + vec2(e, 0.0), t), hz = landH(p.xz + vec2(0.0, e), t);
    return normalize(vec3(h0 - hx, e, h0 - hz));
}

vec3 objNormal(vec3 p) {
    const float e = 0.03;
    float d = landObj(p);
    return normalize(vec3(landObj(p + vec3(e, 0.0, 0.0)) - d, landObj(p + vec3(0.0, e, 0.0)) - d, landObj(p + vec3(0.0, 0.0, e)) - d) + 1e-6);
}

// The tangent of the height of the land seen from p along a horizontal direction: the Sun is hidden below it.
float horizonTan(vec3 p, vec2 dir) {
    float m = -0.2;
    float s = 1.5;
    for (int i = 0; i < 30; i++) {
        vec2 q = p.xz + dir * s;
        m = max(m, (landH(q, s + 200.0) - p.y) / s);
        // Solid things cast shadows too (a tree's shade on the grass).
        vec3 pp = vec3(q.x, p.y + m * s + 0.4, q.y);
        float od = landObj(pp);
        if (od < 0.3) m = max(m, (pp.y + 1.2 - p.y) / s);
        s *= 1.32;
    }
    return m;
}

void main() {
    if (gl_FragCoord.y < uTile.x || gl_FragCoord.y >= uTile.y) discard;
    vec2 vp = viewPx();
    vec3 rd = viewRay(vp);
    vec3 ro = vec3(0.0, uCam.z, 0.0);
    o0 = vec4(0.0); o1 = vec4(0.5, 1.0, 0.5, 1.0); o2 = vec4(1.0, 1.0, 0.0, 0.0);

    float t = 0.3;
    bool hit = false;
    for (int i = 0; i < 420; i++) {
        vec3 p = ro + rd * t;
        float d = sceneD(p, t);
        if (d < 0.0006 * t) { hit = true; break; }
        t += max(d, 0.0022 * t + 0.01);
        if (t > FAR || (rd.y > 0.0 && p.y > LAND_TOP)) break;
    }
    if (hit) {
        // Close in on the surface.
        float a = t - max(0.0025 * t, 0.05), b = t;
        for (int i = 0; i < 7; i++) {
            float m = 0.5 * (a + b);
            if (sceneD(ro + rd * m, m) < 0.0) b = m; else a = m;
        }
        t = 0.5 * (a + b);
    }
    float tw = rd.y < 0.0 ? (ro.y - landWater()) / -rd.y : 1e9;
    if (tw < 0.0) tw = 1e9;
    if (!hit && rd.y < -1e-4 && tw > FAR) {
        // Beyond the furthest relief the ground runs on, level, to the horizon.
        t = min(ro.y / -rd.y, 4.0e5);
        hit = true;
    }
    if (!hit && tw > FAR) return;       // sky

    vec3 alb; float kind; vec3 n; float open = 1.0;
    vec3 p;
    if (tw < t || !hit) {
        // Water: drawn (and moved) every frame; here only that it is water, and how far.
        t = tw;
        p = ro + rd * t;
        // The bed below it, for the shallows.
        float depth = landWater() - landH(p.xz, t);
        alb = vec3(saturate(depth / 6.0)); kind = 7.0; n = vec3(0.0, 1.0, 0.0);
    } else {
        p = ro + rd * t;
        bool isObj = landObj(p) < (p.y - landH(p.xz, t)) * LAND_STEP + 0.02;
        n = isObj ? objNormal(p) : landNormal(p, t);
        landMat(p, n, t, isObj, alb, kind, open);
    }
    vec3 ps = p + vec3(0.0, 0.05 + t * 0.0005, 0.0);
    float hl = horizonTan(ps, vec2(sin(-SUN_AZ), cos(-SUN_AZ)));
    float hr = horizonTan(ps, vec2(sin(SUN_AZ), cos(SUN_AZ)));
    o0 = vec4(sqrt(clamp(alb, 0.0, 1.0)), (kind + 0.5) / 8.0);
    o1 = vec4(n * 0.5 + 0.5, open);
    // Horizon heights from -0.2 to 1.4 (the tangent), square-rooted for precision near the horizon.
    o2 = vec4(packDist(t), sqrt(clamp((hl + 0.2) / 1.6, 0.0, 1.0)), sqrt(clamp((hr + 0.2) / 1.6, 0.0, 1.0)));
}
