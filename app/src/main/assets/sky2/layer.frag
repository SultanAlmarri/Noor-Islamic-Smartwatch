// One layer of clouds in the light of the moment. A set of clouds was path traced under unit lights (tools/scenery/
// skybake.py); here its pictures are given the colours those lights have now: the Sun as reddened by the air below
// the layer, the glow of the twilight sky, the blue of the sky above, the light thrown up by the ground. The result
// (colour already multiplied by opacity, and opacity) is laid over the sky by scene.frag, slid by the wind.
// Drawn twice into one target while one set gives way to another (uLay.z: how much of this one).
uniform sampler2D tA;        // opacity
uniform sampler2D tF;        // how far (see skybake)
uniform sampler2D tK;        // lit by a white sky above
uniform sampler2D tG;        // lit by white ground below
uniform sampler2D tLit;      // lit by the Sun and by the glow of the moment (lit.frag)
uniform sampler2D tSky;      // the clear sky (for the colour far clouds fade into)
uniform vec4 uLay;           // brightest light stored, the set's level (0 low, 1 middle, 2 high), share of this set, 1 = mirrored
uniform vec4 uLayFrame;      // the set's own window of the sky: degrees to either side of its middle, its lowest and
                             // highest degrees over the horizon; how far right of its middle the view's middle stands now
out vec4 o;

// How much of the air's haze a sight line meets that climbs x scale heights on its way: all of it along the ground,
// little of it up to a high cloud.
float thinning(float x) { return x < 1e-3 ? 1.0 : (1.0 - exp(-x)) / x; }

void main() {
    vec2 uv = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y) / uRes;
    // This pixel of the window, as a direction: degrees right of the view's middle and over the horizon. The set is a
    // wider window of its own, turned by its drift.
    float az = (uv.x - 0.5) * 2.0 * SKYWIN_AZ, el = SKYWIN_EL1 - uv.y * (SKYWIN_EL1 - SKYWIN_EL0);
    float saz = (uLay.w > 0.5 ? -az : az) + uLayFrame.w;
    vec2 tuv = vec2(0.5 + saz / (2.0 * uLayFrame.x), (uLayFrame.z - el) / (uLayFrame.z - uLayFrame.y));
    if (tuv.x <= 0.0 || tuv.x >= 1.0 || tuv.y <= 0.0 || tuv.y >= 1.0) { o = vec4(0.0); return; }
    // (A set ends softly: the next one is coming in over it there.)
    float a = texture(tA, tuv).r * smoothstep(0.0, 3.0, (0.5 - abs(tuv.x - 0.5)) * 2.0 * uLayFrame.x);
    if (a < 0.002) { o = vec4(0.0); return; }
    vec2 lit = texture(tLit, vec2(uLay.w > 0.5 ? 1.0 - tuv.x : tuv.x, 1.0 - tuv.y)).rg;
    float sun = sq(lit.r) * uLay.x, glow = sq(lit.g) * uLay.x;
    float fromSky = sq(texture(tK, tuv).r) * uLay.x, fromGround = sq(texture(tG, tuv).r) * uLay.x;

    float moonW = smoothstep(-6.0, -10.0, uSunEl);
    vec3 direct = (uLay.y < 0.5 ? mSunCloud() : uLay.y < 1.5 ? mSunMid() : mSunHigh()) * (1.0 - moonW) + mMoonLight() * moonW;
    vec3 ambTop = mAmbTop(), ambLow = mAmbLow();
    vec3 dome = mix(ambLow, ambTop, 0.6);
    // The ground under the clouds: a third of the light that falls on it comes back up.
    vec3 ground = 0.30 * (mSunGround() * max(uSunTrue.y, 0.0) + dome);
    // (Clouds are exposed a little up, as the land is held a little down: in a photograph they are the brightest things.)
    vec3 c = (sun * direct + glow * mGlow()) * 1.15 + fromSky * dome * 0.9 + fromGround * ground * 0.8;

    // Far clouds fade into the air in front of them, which has the colour of the sky behind them. The haze lies low:
    // the foot of a far tower dissolves in it while its head, seen through thin air high up, stays crisp and white.
    float te = tan(el * DEG), ta = tan(az * DEG);
    float vx = 1.15 * ta;                                                     // across the view, 0 = its middle
    float row = min(uCam.y - te * sqrt(1.0 + ta * ta) * 1.15 * uView.x, uCam.y - 0.012 * uView.y);
    vec3 haze = texture(tSky, vec2(clamp(0.5 + vx, 0.02, 0.98), 1.0 - row / uView.y)).rgb;
    float far = texture(tF, tuv).r;
    float km = -30.0 * log(max(1.0 - far, 1e-3));
    float up = km * max(te, 0.0) / sqrt(1.0 + te * te);
    float fog = 1.0 - exp(-(0.55 * uHaze + 0.25) * km / 30.0 * (0.25 * thinning(up / 8.0) + 0.75 * thinning(up / 1.6)));
    c = mix(c * a / max(texture(tA, tuv).r, 1e-3), haze * a, fog * 0.92);
    o = vec4(c, a) * uLay.z;
}
