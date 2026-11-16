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
uniform sampler2D tNoise2D;  // the flat noise tile
uniform vec4 uLay;           // brightest light stored, the set's level (0 low, 1 middle, 2 high), share of this set, and
                             // 1 = the scene is mirrored (right to left), 2 = this set's picture is turned about (0.14: a
                             // set passes once as it was rendered and once the other way round), 3 = both
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
    bool rtl = mod(uLay.w, 2.0) > 0.5, turned = uLay.w > 1.5;
    float saz = (rtl ? -az : az) + uLayFrame.w;
    vec2 tuv = vec2(0.5 + (turned ? -saz : saz) / (2.0 * uLayFrame.x), (uLayFrame.z - el) / (uLayFrame.z - uLayFrame.y));
    if (tuv.x <= 0.0 || tuv.x >= 1.0 || tuv.y <= 0.0 || tuv.y >= 1.0) { o = vec4(0.0); return; }
    // (Where this set has given way to its neighbour altogether, there is nothing to work out: see below.)
    float gone = smoothstep(0.5 * SKY_PITCH - 14.0, 0.5 * SKY_PITCH + 14.0, abs(saz) + sign(saz) * 0.2 * (el - 18.0));
    if (gone >= 1.0) { o = vec4(0.0); return; }
    // (0.13) Clouds are never still in themselves. A slow swirl carries every part of the picture a little this way
    // and that (a third of a degree at most), so that outlines swell and sink as a cloud's do; and their thin parts
    // form and melt away while their hearts stay: a slow pattern drifts through the set and feeds or starves what
    // is neither clear sky nor solid cloud.
    vec2 np = vec2(saz, el) * 0.033;
    vec2 swirl = (texture(tNoise2D, np * 1.3 + vec2(uTime * 0.0040, uTime * 0.0027)).rg - 0.5) * 1.5
               + (texture(tNoise2D, np * 3.7 - vec2(uTime * 0.0090, uTime * 0.0060)).rg - 0.5) * 0.20;
    tuv += swirl * vec2(turned ? -1.0 : 1.0, 1.0) / vec2(2.0 * uLayFrame.x, uLayFrame.z - uLayFrame.y);
    float a0 = texture(tA, tuv).r;
    if (a0 < 0.002) { o = vec4(0.0); return; }             // (open sky: most of most sets)
    float feed = texture(tNoise2D, np + vec2(uTime * 0.0021, uTime * 0.0013)).g * 0.62 + texture(tNoise2D, np * 2.9 - vec2(uTime * 0.0034, 0.0)).b * 0.38;
    float a = clamp(a0 + (feed - 0.5) * 4.0 * a0 * (1.0 - a0), 0.0, 1.0);
    // The sets of a level pass side by side (SkyDirector.legs), SKY_PITCH degrees from one middle to the next, each
    // 94 degrees wide: over the 28 degrees they share, the one breaks up and thins out as the other gathers. Not along
    // a line (the edge of an overcast would cross the sky as a wall): in patches, as a sheet of cloud really ends,
    // the gaps growing until nothing is left.
    // (The line the patches thin out along leans, the same way at both ends of a set, so that neighbours fit.)
    if (gone > 0.0) {
        // (Large lobes, patches and a ragged fringe: three sizes of the same grain.)
        vec2 bp = vec2(saz * 0.021, el * 0.050 + 0.37 * uLay.y);
        float lobes = (texture(tNoise2D, bp * 0.31 + 0.17).r - 0.5) * 3.2;
        float cells = texture(tNoise2D, bp * 0.9).b * 0.7 + texture(tNoise2D, bp * 2.7 + 0.4).b * 0.3;
        float patches = clamp(0.5 + lobes * 0.55 + (cells - 0.45) * 1.5, 0.0, 1.0);
        a *= smoothstep(-0.20, 0.16, patches - 1.4 * gone + 0.16);
    }
    if (a < 0.002) { o = vec4(0.0); return; }
    vec2 lit = texture(tLit, vec2(rtl ? 1.0 - tuv.x : tuv.x, 1.0 - tuv.y)).rg;
    float sun = sq(lit.r) * uLay.x, glow = sq(lit.g) * uLay.x;
    float fromSky = sq(texture(tK, tuv).r) * uLay.x, fromGround = sq(texture(tG, tuv).r) * uLay.x;

    float moonW = smoothstep(-6.0, -10.0, uSunEl);
    // (0.14) The Sun's light on this cloud, where this cloud is. The view looks towards where the Sun rises and
    // sets, so a far cloud stands nearer the Sun's side of the Earth than we do: there the Sun is higher by a degree
    // for every 111 km, and the cloud is high besides. That is the whole of an evening sky: when the Sun has set for
    // us, the far clouds are still gold, nearer ones are orange and then red, the nearest have gone grey, and the
    // high clouds burn longest. (Until 0.13 every cloud of a level had one light: all lit, then all grey.)
    float far = texture(tF, tuv).r;
    float km = -30.0 * log(max(1.0 - far, 1e-3));
    float azs = (uCam.w > 0.5 ? -az : az) * DEG;                    // (the scene's own x runs the other way when mirrored)
    float sunAz = atan(uSunDir.x, uSunDir.z);
    float elThere = uSunEl + km * cos(azs - sunAz) / 111.2;
    // (How high this part of the cloud stands, from how far it is and how high it is seen, the Earth curving away
    // under it: the head of a thunderstorm stands ten kilometres and more up and keeps the Sun long after its base
    // and the low clouds round it have lost it, pink and gold over a grey sky. A layer's own height is the least.)
    float high = uLay.y < 0.5 ? 1.2 : uLay.y < 1.5 ? 4.0 : 8.0;
    high = clamp(max(high, km * max(sin(el * DEG), 0.0) + km * km / 12742.0), 0.2, 14.0);
    vec3 direct = warm(transmittance(high, sin(elThere * DEG)) * mSunWhite()) * (1.0 - moonW) + mMoonLight() * moonW;
    vec3 ambTop = mAmbTop(), ambLow = mAmbLow();
    vec3 dome = mix(ambLow, ambTop, 0.6);
    // (0.14) In the evening a cloud's shaded side is lit by the whole blue dome, not by the narrow orange band at
    // the Sun's horizon: it is slate and blue-grey, and the Sun's colours burn against that. (Warm light and warm
    // shade together made every evening cloud khaki.)
    dome = mix(dome, luma(dome) * vec3(0.78, 0.95, 1.38), 0.65 * smoothstep(7.0, -2.0, uSunEl));
    // The ground under the clouds: a third of the light that falls on it comes back up.
    vec3 ground = 0.30 * (mSunGround() * max(uSunTrue.y, 0.0) + dome);
    // (Clouds are exposed a little up, as the land is held a little down: in a photograph they are the brightest things.)
    vec3 c = (sun * direct + glow * mGlow() * mix(1.0, 0.55, smoothstep(2.0, -3.0, uSunEl))) * 1.15 + fromSky * dome * 0.9 + fromGround * ground * 0.8;
    // (0.14) What makes an evening sky, and what the pictures alone do not hold (rendered under a low Sun a cloud
    // seen from the Sun's far side is nearly black: until 0.13 the evening's clouds were grey with pale rims).
    // Light comes through a cloud where it is thin, and most of it goes on nearly the way it came: thin cloud near
    // the Sun blazes, and every cloud on that side of the sky is rimmed and veined with the Sun's colour. (How thin
    // a cloud is, the picture lit by the sky above knows: where light from above comes through to us below.)
    vec3 vdir = vec3(cos(el * DEG) * sin(azs), sin(el * DEG), cos(el * DEG) * cos(azs));
    float mu = dot(vdir, uSunDir);
    float lowSun = smoothstep(26.0, 6.0, uSunEl);
    float through = 0.7 * hg(mu, 0.80) + 0.3 * hg(mu, 0.45);
    // (Heaped clouds are thick: little comes through them, and that at their edges.)
    float thinKind = uLay.y < 0.5 ? 0.45 : 1.0;
    c += direct * fromSky * through * 0.15 * thinKind * mix(0.06, 1.0, lowSun);
    // And once the Sun stands lower than the cloud itself, its light runs along the cloud's underside and catches
    // every fold of it from below: the undersides burn. (The picture lit by the ground knows what is seen from
    // below.)
    // (Which folds catch it, the picture rendered under the low Sun knows, though it holds them dark: a grazing light
    // gives little to a level underside. Its relief is kept and its strength raised, so a sheet of cloud is not
    // painted one flat colour.)
    c += direct * (0.09 * fromGround + 2.6 * sun) * thinKind * smoothstep(3.5, -0.5, elThere);

    // Far clouds fade into the air in front of them, which has the colour of the sky behind them. The haze lies low:
    // the foot of a far tower dissolves in it while its head, seen through thin air high up, stays crisp and white.
    float te = tan(el * DEG), ta = tan(az * DEG);
    float vx = 1.15 * ta;                                                     // across the view, 0 = its middle
    float row = min(uCam.y - te * sqrt(1.0 + ta * ta) * 1.15 * uView.x, uCam.y - 0.012 * uView.y);
    vec3 haze = texture(tSky, vec2(clamp(0.5 + vx, 0.02, 0.98), 1.0 - row / uView.y)).rgb;
    float up = km * max(te, 0.0) / sqrt(1.0 + te * te);
    float fog = 1.0 - exp(-(0.55 * uHaze + 0.25) * km / 30.0 * (0.25 * thinning(up / 8.0) + 0.75 * thinning(up / 1.6)));
    // (0.14: the middle and high clouds keep more of their own light to the horizon. Faded all the way into the sky
    // behind them, a veil of high cloud stopped short of the evening's horizon, and the bare sky under it showed as a
    // band with an edge.)
    c = mix(c * a / max(texture(tA, tuv).r, 1e-3), haze * a, fog * (uLay.y < 0.5 ? 0.92 : uLay.y < 1.5 ? 0.80 : 0.68));
    o = vec4(c, a) * uLay.z;
}
