// The air: the same physical model as the 0.9.6 sky (Atmosphere.kt), now on the GPU and in linear light, so the sky,
// the clouds, the land and the water are all lit by one Sun through one atmosphere.
//
// Sunlight is scattered by the air (Rayleigh: blue first), by haze (Mie: the glow round the Sun) and partly absorbed
// by ozone (which keeps twilight blue), over a round Earth whose shadow rises opposite the Sun at dusk.

uniform sampler2D tTrans;   // transmittance to the top of the air, by height and direction
uniform sampler2D tMeter;   // the light of the moment (see meter.frag)

const float RE = 6360.0;
const float RA = 6460.0;
const float HR = 8.0;
const float HM = 1.2;
const vec3 BR = vec3(5.802e-3, 13.558e-3, 33.1e-3);
const float BMS = 3.996e-3;
const float BME = 4.40e-3;
const vec3 BO = vec3(0.650e-3, 1.881e-3, 0.085e-3) * 2.2;
const float EYE_KM = 0.25;
const float MIE_G = 0.76;
const float MSCAT = 0.06;
const float E_SUN = 20.0;
const float TRANS_W = 256.0;
const float TRANS_H = 64.0;

vec2 transUV(float h, float mu) {
    float i = sqrt(clamp(h, 0.0, RA - RE) / (RA - RE));
    float x = sign(mu) * sqrt(abs(mu));
    return vec2(((x + 1.0) * 0.5 * (TRANS_W - 1.0) + 0.5) / TRANS_W, (i * (TRANS_H - 1.0) + 0.5) / TRANS_H);
}
// How much of the Sun's light reaches height h (km) from a direction whose sine of elevation there is mu.
vec3 transmittance(float h, float mu) { return texture(tTrans, transUV(h, mu)).rgb; }

float phaseR(float mu) { return 3.0 / (16.0 * PI) * (1.0 + mu * mu); }
float phaseM(float mu) {
    float g2 = MIE_G * MIE_G;
    return 3.0 / (8.0 * PI) * (1.0 - g2) * (1.0 + mu * mu) / ((2.0 + g2) * pow(1.0 + g2 - 2.0 * MIE_G * mu, 1.5));
}

// Sunlight at 50 degrees elevation is white, as the eye (or a camera set to daylight) sees it.
vec3 sunWhite() {
    vec3 t = max(transmittance(EYE_KM, sin(50.0 * DEG)), 1e-4);
    return luma(t) / t;
}

// Light arriving from direction v (unexposed). Transmittance towards the Sun uses its true direction sl; the phase
// (where the glow sits) uses the direction it is drawn in, sp.
vec3 skyRadiance(vec3 v, vec3 sl, vec3 sp) {
    float oy = RE + EYE_KM;
    float b = oy * v.y;
    float len = -b + sqrt(max(b * b - (oy * oy - RA * RA), 0.0));
    float mu = dot(v, sp);
    float pr = phaseR(mu), pm = phaseM(mu);
    float odR = 0.0, odM = 0.0, odO = 0.0;
    vec3 l = vec3(0.0);
    const int N = 14;
    for (int s = 0; s < N; s++) {
        float u0 = float(s) / float(N), u1 = float(s + 1) / float(N);
        float t0 = u0 * u0 * len, t1 = u1 * u1 * len;
        float t = 0.5 * (t0 + t1), dt = t1 - t0;
        vec3 p = vec3(0.0, oy, 0.0) + v * t;
        float r = length(p);
        float h = r - RE;
        float dR = exp(-h / HR), dM = exp(-h / HM) * uHaze, dO = max(0.0, 1.0 - abs(h - 25.0) / 15.0);
        odR += dR * dt; odM += dM * dt; odO += dO * dt;
        float muS = dot(p, sl) / r;
        vec3 tt = transmittance(h, muS);
        vec3 tms = transmittance(h, muS + 0.10);
        vec3 tv = exp(-(BR * odR + BME * odM + BO * odO));
        l += ((BR * dR * pr + BMS * dM * pm) * tt + (BR * dR + BMS * dM) * MSCAT * tms) * tv * dt;
    }
    return l * sunWhite() * E_SUN;
}

// How bright the zenith is shown, by the Sun's elevation: full daylight, a gentle fall through twilight.
float targetLuminance(float el) {
    const int N = 9;
    float xs[9] = float[9](-16.0, -12.0, -9.0, -6.0, -3.0, 0.0, 4.0, 10.0, 90.0);
    float ys[9] = float[9](0.0006, 0.0035, 0.011, 0.026, 0.042, 0.056, 0.068, 0.080, 0.092);
    if (el <= xs[0]) return ys[0];
    for (int i = 1; i < N; i++) if (el <= xs[i]) {
        float t = (el - xs[i - 1]) / (xs[i] - xs[i - 1]);
        float u = t * t * (3.0 - 2.0 * t);
        return exp(mix(log(ys[i - 1]), log(ys[i]), u));
    }
    return ys[8];
}

// The designed night: a deep blue, a little lighter towards the horizon (airglow, distant light).
const vec3 NIGHT_TOP = vec3(0.010, 0.017, 0.045);
const vec3 NIGHT_LOW = vec3(0.028, 0.044, 0.085);

// The light of the moment, metered once per change of the Sun (meter.frag) and read by every pass.
float mExposure() { return texelFetch(tMeter, ivec2(0, 0), 0).a; }
vec3 mAmbTop() { return texelFetch(tMeter, ivec2(0, 0), 0).rgb; }     // skylight from above
vec3 mAmbLow() { return texelFetch(tMeter, ivec2(1, 0), 0).rgb; }     // skylight from the horizon
vec3 mSunGround() { return texelFetch(tMeter, ivec2(2, 0), 0).rgb; }  // sunlight on a white surface on the ground
vec3 mSunCloud() { return texelFetch(tMeter, ivec2(3, 0), 0).rgb; }   // ... at the height of heaped clouds
vec3 mSunMid() { return texelFetch(tMeter, ivec2(4, 0), 0).rgb; }     // ... of the middle clouds
vec3 mSunHigh() { return texelFetch(tMeter, ivec2(5, 0), 0).rgb; }    // ... of the high ice clouds
vec3 mSunDisc() { return texelFetch(tMeter, ivec2(6, 0), 0).rgb; }    // the Sun's own disc through the air
vec3 mMoonLight() { return texelFetch(tMeter, ivec2(7, 0), 0).rgb; }  // moonlight on a white surface
