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
uniform vec4 uLay;           // brightest light stored, layer (0 low, 1 middle, 2 high), share of this set, 1 = mirrored
uniform vec4 uLayFrame;      // of the set's picture: px across the phone's view, row of the horizon, width, height
out vec4 o;

// How much of the air's haze a sight line meets that climbs x scale heights on its way: all of it along the ground,
// little of it up to a high cloud.
float thinning(float x) { return x < 1e-3 ? 1.0 : (1.0 - exp(-x)) / x; }

void main() {
    vec2 uv = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y) / uRes;
    vec2 tuv = uLay.w > 0.5 ? vec2(1.0 - uv.x, uv.y) : uv;
    float a = texture(tA, tuv).r;
    if (a < 0.002) { o = vec4(0.0); return; }
    vec2 lit = texture(tLit, vec2(uv.x, 1.0 - uv.y)).rg;
    float sun = sq(lit.r) * uLay.x, glow = sq(lit.g) * uLay.x;
    float fromSky = sq(texture(tK, tuv).r) * uLay.x, fromGround = sq(texture(tG, tuv).r) * uLay.x;

    float moonW = smoothstep(-6.0, -10.0, uSunEl);
    vec3 direct = (uLay.y < 0.5 ? mSunCloud() : uLay.y < 1.5 ? mSunMid() : mSunHigh()) * (1.0 - moonW) + mMoonLight() * moonW;
    vec3 ambTop = mAmbTop(), ambLow = mAmbLow();
    vec3 dome = mix(ambLow, ambTop, 0.6);
    // The ground under the clouds: a third of the light that falls on it comes back up.
    vec3 ground = 0.30 * (mSunGround() * max(uSunTrue.y, 0.0) + dome);
    // (Clouds are exposed a little up, as the land is held a little down: in a photograph they are the brightest things.)
    vec3 c = (sun * direct + glow * mGlow()) * 1.5 + fromSky * dome + fromGround * ground;

    // Far clouds fade into the air in front of them, which has the colour of the sky behind them. The haze lies low:
    // the foot of a far tower dissolves in it while its head, seen through thin air high up, stays crisp and white.
    float vx = (uv.x - 0.5) * uLayFrame.z / uLayFrame.x;                      // across the view, 0 = its middle
    float te = (uLayFrame.y - uv.y * uLayFrame.w) / (1.15 * uLayFrame.x);     // the tangent of its height over the horizon
    float row = min(uCam.y - te * 1.15 * uView.x, uCam.y - 0.012 * uView.y);
    vec3 haze = texture(tSky, vec2(clamp(0.5 + vx, 0.02, 0.98), 1.0 - row / uView.y)).rgb;
    float far = texture(tF, tuv).r;
    float km = -30.0 * log(max(1.0 - far, 1e-3));
    float up = km * max(te, 0.0) / sqrt(1.0 + te * te);
    float fog = 1.0 - exp(-(0.55 * uHaze + 0.25) * km / 30.0 * (0.25 * thinning(up / 8.0) + 0.75 * thinning(up / 1.6)));
    c = mix(c, haze * a, fog * 0.92);
    o = vec4(c, a) * uLay.z;
}
