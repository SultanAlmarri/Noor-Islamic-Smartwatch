// Puts the picture together, in linear light: the sky of the moment, the Sun and the Moon, the stars, the layers of
// cloud in the light of the moment, and under them the land (a path-traced picture relit) and the water (drawn live).

uniform sampler2D tSky;       // the clear sky for every direction in view (sky.frag)
uniform sampler2D tNoise2D;
uniform sampler2D tCloud0;    // the three layers of cloud, lit (layer.frag): high, middle, low
uniform sampler2D tCloud1;
uniform sampler2D tCloud2;
uniform vec4 uCloudF0, uCloudF1, uCloudF2;   // each layer's picture: px across the phone's view, row of the horizon, width, height
uniform vec4 uCloudD;         // how far the wind has slid each layer (px of its picture); 1 = there are clouds at all
uniform sampler2D tLandA;     // the land: its colour under white light, and where it is (alpha)
uniform sampler2D tLandK;     // the land lit by a plain white sky
uniform sampler2D tLandG;     // how far it is (16 bits, log)
uniform sampler2D tLandM;     // R: under water, G: moves in the wind, B: takes snow in winter
uniform sampler2D tLandLit;   // its shading under the Sun and the glow of the moment (lit.frag)
uniform vec4 uLandInfo;       // 1 if there is a land picture, the water's level (m under the eye; 0 = none), the
                              // brightest shading stored, 1 if there is a mask
uniform vec3 uCelX, uCelY, uCelZ, uGalN, uGalC;   // the turning sky: celestial axes, the Milky Way's pole and centre
uniform vec4 uSky;            // aurora strength, meteor (0..1 along its track, 0 = none), lightning flash, rainbow
uniform vec4 uLand;           // biome, season (0 winter .. 1 summer), snow, wetness
uniform vec4 uAir;            // mist near the ground, rain here, wind strength, dust
uniform vec4 uWater;          // roughness of the water (0 glass .. 1 storm), its clearness, -, -
uniform vec4 uTouch;          // where the scene was last touched (px), seconds since, strength
uniform vec4 uBirds;          // a flock crossing the sky: how far across (0..1; below 0 = none), its height (of the
                              // view's width above the horizon), its seed, +1 left to right / -1 right to left
uniform vec4 uMeteor;         // a shooting star: how far along its track (0..1; 0 = none), where it starts (x, y, of the
                              // view's width from the top-left), its bearing (radians from straight down)

out vec4 o;

vec3 skyAt(vec2 vp) { return texture(tSky, vec2(vp.x / uView.x, 1.0 - vp.y / uView.y)).rgb; }

float hg(float mu, float g) { float g2 = g * g; return (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * mu, 1.5); }

// ---- The night ----------------------------------------------------------------------------------------------------

// Faint stars fixed to the turning sky, so they rise and set with the night.
vec3 faintStars(vec3 d, float alt) {
    vec3 c = vec3(dot(d, uCelX), dot(d, uCelY), dot(d, uCelZ));
    vec3 a = abs(c);
    vec2 uv; float face;
    if (a.x > a.y && a.x > a.z) { uv = c.yz / a.x; face = c.x > 0.0 ? 0.0 : 1.0; }
    else if (a.y > a.z) { uv = c.xz / a.y; face = c.y > 0.0 ? 2.0 : 3.0; }
    else { uv = c.xy / a.z; face = c.z > 0.0 ? 4.0 : 5.0; }
    vec3 sum = vec3(0.0);
    for (int k = 0; k < 2; k++) {
        float n = k == 0 ? 190.0 : 420.0;
        vec2 g = uv * n;
        vec2 id = floor(g);
        vec2 r = hash22(id + face * 37.0 + float(k) * 91.0);
        float m = hash12(id * 1.37 + face * 11.0 + float(k) * 7.0);
        float keep = step(k == 0 ? 0.90 : 0.86, m);
        vec2 dlt = fract(g) - (0.2 + 0.6 * r);
        float px = n * 2.0 / uCam.x * 0.55;
        float spot = exp(-dot(dlt, dlt) / (px * px * (k == 0 ? 1.6 : 0.9)));
        float mag = pow(hash12(id + 5.0 + face), 6.0);
        float tw = 0.75 + 0.25 * sin(uTime * (2.0 + 5.0 * r.x) + r.y * 40.0) * smoothstep(40.0, 5.0, alt);
        vec3 tint = mix(vec3(1.0, 0.86, 0.72), vec3(0.78, 0.86, 1.0), r.y);
        sum += keep * spot * tw * tint * (k == 0 ? 0.035 + 0.75 * mag : 0.012 + 0.20 * mag);
    }
    return sum;
}

// The Milky Way as it is: NASA's map of the whole sky without its stars (Deep Star Maps 2020, public domain), fixed
// to the turning sky, so the bright centre, the dust lanes and the Magellanic Clouds stand where they really do for
// the place and the hour. (The picture runs right ascension leftwards from 0 h in its middle.)
uniform sampler2D tMilky;
vec3 milkyWay(vec3 d) {
    vec3 c = vec3(dot(d, uCelX), dot(d, uCelY), dot(d, uCelZ));
    vec2 uv = vec2(fract(0.5 - atan(c.y, c.x) / (2.0 * PI)), 0.5 - asin(clamp(c.z, -1.0, 1.0)) / PI);
    vec3 t = pow(texture(tMilky, uv).rgb, vec3(2.2));
    // (As a long exposure shows it: its bright parts brought out, its colour kept.)
    return t * (0.55 + 1.6 * luma(t)) * 0.20;
}

// The aurora: curtains of light a hundred kilometres up and more, folded along the far sky, green at their sharp
// lower border and fading upward into red and violet, with fine rays along the field lines. They drift and pulse.
vec3 aurora(vec3 rd, float jit) {
    float s = uSky.x;
    if (s <= 0.01 || rd.y < 0.03) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int i = 0; i < 20; i++) {
        float h = 95.0 + (float(i) + jit) * 8.0;
        vec2 p = rd.xz * (h / rd.y);
        vec2 q = p * 0.0035 + vec2(uTime * 0.0035, uTime * 0.0011);
        float w = texture(tNoise2D, q * 0.33 + 0.3).r;
        float band = 1.0 - abs(texture(tNoise2D, vec2(q.x * 0.55 + w * 0.6, q.y * 0.13 + 0.2)).g * 2.0 - 1.0);
        float fil = pow(band, 10.0);
        float ray = 0.45 + 0.55 * texture(tNoise2D, vec2(q.x * 11.0 + w * 4.0, q.y * 1.1 + uTime * 0.01)).r;
        float hk = (h - 95.0) / 160.0;
        float profile = smoothstep(0.0, 0.05, hk) * exp(-hk * 3.4);
        vec3 c = mix(vec3(0.16, 1.0, 0.42), vec3(0.55, 0.18, 0.80), smoothstep(0.22, 0.85, hk));
        float where = smoothstep(180.0, 650.0, length(p)) * smoothstep(0.30, 0.62, w) * smoothstep(0.045, 0.16, rd.y);
        col += c * fil * ray * profile * where;
    }
    return col * s * 0.30 * (0.75 + 0.25 * sin(uTime * 0.23));
}

// The Moon as a lit sphere with its real face (NASA's map from the Lunar Reconnaissance Orbiter, public domain): its
// phase, the seas and the bright rayed craters where they are, earthshine on the dark part.
uniform sampler2D tMoon;
vec4 moonDisc(vec3 rd) {
    float vis = uMoonLit.w;
    if (vis <= 0.0) return vec4(0.0);
    vec3 m = normalize(uMoon.xyz);
    float rad = 0.0135;                          // drawn larger than life, as the eye remembers it (and as the Sun is)
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), m));
    vec3 up = cross(m, right);
    vec2 q = vec2(dot(rd, right), dot(rd, up)) / rad;
    if (uCam.w > 0.5) q.x = -q.x;
    float r2 = dot(q, q);
    if (r2 > 1.2) return vec4(0.0);
    float edge = smoothstep(1.0, 0.93, sqrt(r2));
    vec3 n = vec3(q.x, -q.y, sqrt(max(1.0 - r2, 0.0)));
    float lit = smoothstep(-0.04, 0.22, dot(n, normalize(uMoonLit.xyz)));
    // The side that faces the Earth: longitude 0 in the middle of the disc.
    vec2 uv = vec2(0.5 + atan(n.x, n.z) / (2.0 * PI), 0.5 - asin(clamp(-n.y, -1.0, 1.0)) / PI);
    vec3 face = pow(texture(tMoon, uv).rgb, vec3(2.2));
    // (The full Moon is flat-lit: no darkening to its limb. Its face is given a little more contrast than the map's.)
    face = mix(face, vec3(luma(face)), 0.65);
    vec3 alb = clamp((face - 0.03) * 3.1, 0.0, 1.6);
    vec3 col = alb * vec3(1.0, 0.98, 0.93) * (lit * (1.2 + 4.2 * uNight) + 0.03 * uNight);
    return vec4(col * edge * vis, edge * vis * max(lit, 0.25 * uNight));
}

// ---- The Sun ------------------------------------------------------------------------------------------------------

// The Sun as a camera sees it: its disc (drawn a little over twice its real size, like the Moon: it is also the hand
// of this clock), white-hot when high and a clear red-gold ball when the air has dimmed it; the glare close round it;
// and the wide glow of the air it shines through. `shows`: how much of it the clouds in front let through.
vec3 sunLight(vec3 rd, float shows) {
    if (uSunEl < -3.0) return vec3(0.0);
    float th = acos(clamp(dot(rd, uSunDir), -1.0, 1.0));
    vec3 c = mSunDisc();                               // the disc's colour and brightness (white = about 40)
    const float R = 0.0135;
    float sd = th / R;
    float up = smoothstep(-3.0, -0.6, uSunEl);
    vec3 col = vec3(0.0);
    if (sd < 1.3) {
        float limb = 1.0 - 0.38 * (1.0 - sqrt(max(1.0 - sd * sd, 0.0)));
        col += c * limb * smoothstep(1.0, 0.86, sd) * shows;
    }
    // Round it: a tight glare, the wider glow of the air it shines through (fuller when it is low and its light
    // crosses much more air), and the fine rays a lens draws out of a point of light: by these a camera's Sun is
    // known for the Sun even against a bright sky.
    float low = smoothstep(12.0, 1.0, uSunEl);
    // (A low Sun's disc is drawn dimmer so that it keeps its colour; its glow is not.)
    vec3 cg = c * max(1.0, 9.0 / max(luma(c), 0.5));
    cg = mix(c, cg, low);
    vec3 g = cg * (0.022 * exp(-th / 0.030) * shows
                + 0.0085 * (1.0 + 1.6 * low) * exp(-th / 0.085) * (0.35 + 0.65 * shows)
                + 0.0018 * (1.0 + 1.2 * low) * exp(-th / 0.26));
    if (th < 0.30 && shows > 0.05) {
        vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), uSunDir));
        vec3 upv = cross(uSunDir, right);
        float phi = atan(dot(rd, upv), dot(rd, right));
        float rays = pow(abs(cos(phi * 7.0)), 40.0) + 0.55 * pow(abs(cos(phi * 7.0 + 0.9)), 90.0);
        g += c * rays * 0.015 * shows * shows * exp(-th / 0.050) * smoothstep(0.012, 0.03, th);
    }
    return (col + g) * up;
}

// ---- The clouds ---------------------------------------------------------------------------------------------------

// A layer of cloud at a point of the view: colour (already multiplied by opacity) and opacity.
vec4 layerAt(sampler2D t, vec4 f, float slide, vec2 vp) {
    float k = f.x / uView.x;
    vec2 uv = vec2(0.5 + ((vp.x - 0.5 * uView.x) * k + slide) / f.z, (f.y + (vp.y - uCam.y) * k) / f.w);
    if (uv.y >= 1.0 || uv.y <= 0.0) return vec4(0.0);
    return texture(t, vec2(uv.x, 1.0 - uv.y));
}

// All three laid over a colour, the highest first.
vec3 cloudsOver(vec3 col, vec2 vp, out float through) {
    through = 1.0;
    if (uCloudD.w < 0.5) return col;
    vec4 c = layerAt(tCloud0, uCloudF0, uCloudD.x, vp);
    col = col * (1.0 - c.a) + c.rgb; through *= 1.0 - c.a;
    c = layerAt(tCloud1, uCloudF1, uCloudD.y, vp);
    col = col * (1.0 - c.a) + c.rgb; through *= 1.0 - c.a;
    c = layerAt(tCloud2, uCloudF2, uCloudD.z, vp);
    col = col * (1.0 - c.a) + c.rgb; through *= 1.0 - c.a;
    return col;
}

// How much sky shows through the clouds in a direction.
float cloudGap(vec3 d) {
    if (uCloudD.w < 0.5) return 1.0;
    vec3 p = viewPoint(d);
    if (p.z <= 0.0) return 1.0;
    return (1.0 - layerAt(tCloud0, uCloudF0, uCloudD.x, p.xy).a) * (1.0 - layerAt(tCloud1, uCloudF1, uCloudD.y, p.xy).a)
         * (1.0 - layerAt(tCloud2, uCloudF2, uCloudD.z, p.xy).a);
}

// ---- Life ---------------------------------------------------------------------------------------------------------

// A flock of birds far off, crossing the sky: small dark shapes with beating wings, in a loose line. How much of this
// pixel they cover.
float birds(vec2 vp) {
    if (uBirds.x < 0.0) return 0.0;
    float W = uView.x;
    vec2 c = vec2(mix(-0.12, 1.12, uBirds.w > 0.0 ? uBirds.x : 1.0 - uBirds.x) * W, uCam.y - uBirds.y * W);
    c.y += sin(uBirds.x * 9.0 + uBirds.z) * 0.012 * W;
    vec2 d = vp - c;
    if (abs(d.x) > 0.16 * W || abs(d.y) > 0.07 * W) return 0.0;
    float cover = 0.0;
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        vec2 r = hash22(vec2(fi * 7.3 + uBirds.z, uBirds.z * 3.1 + fi));
        // A ragged skein: each bird a little behind and to the side of the one before, bobbing.
        float side = mod(fi, 2.0) < 0.5 ? 1.0 : -1.0;
        float rank = floor((fi + 1.0) * 0.5);
        vec2 at = vec2(-uBirds.w * rank * 0.022 * W - uBirds.w * r.x * 0.012 * W, side * rank * 0.011 * W + (r.y - 0.5) * 0.012 * W);
        at.y += sin(uTime * 0.9 + fi * 1.7) * 0.0016 * W;
        float span = (0.0065 + 0.003 * r.x) * W;                    // half a wingspan
        float beat = sin(uTime * (5.2 + 1.6 * r.y) + fi * 2.4);
        vec2 q = d - at;
        if (dot(q, q) > span * span * 1.6) continue;
        // Two wings from the body, raised and lowered; thicker at the shoulder.
        float lift = beat * 0.55 + 0.15;
        vec2 tip = vec2(span * cos(lift), -span * sin(lift));
        vec2 qa = vec2(abs(q.x), q.y);
        float h = clamp(dot(qa, tip) / dot(tip, tip), 0.0, 1.0);
        float dist = length(qa - tip * h);
        float thick = mix(0.16, 0.05, h) * span;
        cover = max(cover, smoothstep(thick, thick * 0.35, dist));
    }
    return cover;
}

// A shooting star: a quick bright streak, brightest at its head, gone in under a second.
vec3 meteor(vec2 vp) {
    if (uMeteor.x <= 0.0) return vec3(0.0);
    float W = uView.x;
    vec2 dir = vec2(sin(uMeteor.w), cos(uMeteor.w));
    vec2 head = uMeteor.yz * W + dir * uMeteor.x * 0.30 * W;
    vec2 q = vp - head;
    float along = -dot(q, dir);                                   // behind the head: positive
    if (along < -3.0 || along > 0.16 * W) return vec3(0.0);
    float across = abs(dot(q, vec2(dir.y, -dir.x)));
    float tail = exp(-along / (0.045 * W)) * step(0.0, along) + exp(-along * along / 6.0);
    float glow = exp(-across * across / (1.6 + 0.00002 * along * W));
    float life = smoothstep(0.0, 0.12, uMeteor.x) * (1.0 - smoothstep(0.7, 1.0, uMeteor.x));
    return vec3(0.85, 0.93, 1.0) * tail * glow * life * 1.6;
}

// ---- The sky, whole -----------------------------------------------------------------------------------------------

// Everything above the land in a direction: the air, the night's lights, the Sun, the clouds. `soft`: 0 for the view
// itself; 1 for a mirror image in water (the finest things are left out).
vec3 heavens(vec3 rd, vec2 vp, float soft, float sunShows) {
    float alt = asin(clamp(rd.y, -1.0, 1.0)) / DEG;
    vec3 col = skyAt(vp);
    if (uNight > 0.01) {
        float clear = smoothstep(0.5, 14.0, alt);
        float dark = uNight * uNight * (1.0 - 0.6 * uMoon.w * smoothstep(0.0, 0.2, uMoon.y));
        col += milkyWay(rd) * smoothstep(0.55, 1.0, uNight) * clear * (1.0 - 0.7 * uMoon.w * smoothstep(0.0, 0.2, uMoon.y));
        if (soft < 0.5) col += faintStars(rd, alt) * dark * clear;
        col += aurora(rd, ign(gl_FragCoord.xy)) * uNight * uNight;
    }
    vec4 moon = moonDisc(rd);
    col = col * (1.0 - moon.a * 0.85 * uNight) + moon.rgb;
    if (uMoonLit.w > 0.0) {
        float mm = dot(rd, normalize(uMoon.xyz));
        float mth = acos(clamp(mm, -1.0, 1.0));
        col += vec3(0.62, 0.72, 0.95) * uMoon.w * uMoonLit.w * uNight * (0.22 * exp(-mth / 0.022) + 0.050 * exp(-mth / 0.09) + 0.012 * exp(-mth / 0.30));
    }
    col += sunLight(rd, sunShows);
    if (soft < 0.5) col += meteor(vp) * uNight;
    float through;
    col = cloudsOver(col, vp, through);
    // Thin cloud close to the Sun is lit through: the silver lining.
    if (uSunEl > -2.0 && through < 0.999) {
        float mu = dot(rd, uSunDir);
        col += mSunDisc() * 0.012 * min(hg(mu, 0.86), 30.0) * through * (1.0 - through) * smoothstep(-2.0, 1.0, uSunEl);
    }
    return col;
}

// ---- The land -----------------------------------------------------------------------------------------------------

float landDist(vec2 luv) {
    vec3 g = texture(tLandG, luv).rgb;
    return exp2((g.r * 255.0 * 256.0 + g.g * 255.0) / 65535.0 * 18.0);
}

// The land at a point of its picture, in the light of the moment and seen through the air between: colour, and how
// much of the pixel it covers. `t`: how far it is (m).
vec4 landAt(vec2 luv, vec3 rd, vec3 hazeCol, out float t) {
    t = 2.0e5;
    if (uLandInfo.x < 0.5 || luv.y <= 0.0 || luv.x <= 0.0 || luv.x >= 1.0) return vec4(0.0);
    luv.y = min(luv.y, 0.9995);
    vec4 A = texture(tLandA, luv);
    if (A.a < 0.004) return vec4(0.0);
    vec3 alb = pow(A.rgb, vec3(2.2));
    vec2 lit = texture(tLandLit, vec2(luv.x, 1.0 - luv.y)).rg;
    float S = sq(lit.r) * uLandInfo.z, G = sq(lit.g) * uLandInfo.z;
    vec3 K = pow(texture(tLandK, luv).rgb, vec3(2.2));
    // Winter (where the seasons are followed): snow lies on what the scenery says takes it.
    if (uLand.z > 0.01 && uLandInfo.w > 0.5) {
        float snow = smoothstep(0.25, 0.75, texture(tLandM, luv).b * (0.4 + 0.9 * uLand.z)) * uLand.z;
        vec3 white = vec3(0.80, 0.83, 0.88);
        K = mix(K, K * (luma(white) / max(luma(alb), 0.04)) * vec3(0.96, 0.99, 1.06), snow);
        alb = mix(alb, white, snow);
    }
    t = landDist(luv);
    vec3 p = vec3(0.0, uCam.z, 0.0) + rd * t;
    float moonW = smoothstep(-6.0, -10.0, uSunEl);
    // (Lit land is held a little under the sky, as a photographer exposes: its colour stays rich.)
    vec3 light = mSunGround() * 0.70 * (1.0 - moonW) + mMoonLight() * moonW;
    // The shadows of the low clouds, cast from where they stand in the picture.
    float sh = 1.0;
    vec3 L = uLightDir;
    if (uCloudD.w > 0.5 && L.y > 0.12) {
        vec3 q = p + L * ((1500.0 - p.y) / L.y);
        vec3 sp = viewPoint(normalize(q - vec3(0.0, uCam.z, 0.0)));
        if (sp.z > 0.0) sh = 1.0 - 0.85 * layerAt(tCloud2, uCloudF2, uCloudD.z, sp.xy).a * smoothstep(0.12, 0.3, L.y);
    }
    vec3 ambTop = mAmbTop(), ambLow = mAmbLow();
    // (At night the land is given what a long exposure finds in it by starlight: its forms, dim and blue.)
    vec3 amb = mix(ambLow, ambTop, 0.6) + vec3(0.034, 0.052, 0.105) * uNight;
    vec3 land = alb * (S * light * sh + G * mGlow()) + K * amb;

    // The air between: far things fade into the colour of the sky behind them.
    float tk = t * 0.001;
    float ya = uCam.z * 0.001, yb = max(p.y, 0.0) * 0.001;
    const float HH = 1.3;
    float layer = abs(yb - ya) > 1e-3 ? HH / (yb - ya) * (exp(-ya / HH) - exp(-yb / HH)) : exp(-ya / HH);
    float fog = 1.0 - exp(-tk * (0.006 + (0.022 + 0.024 * (uHaze - 1.0) + 0.06 * uAir.w + 0.5 * uAir.x) * layer));
    float glow = pow(saturate(dot(rd, uSunDir)), 8.0);
    vec3 airCol = mix(hazeCol * (1.0 + 0.35 * glow), amb, 0.26) * 0.93;
    land = mix(land, airCol, min(fog, 0.94) * 0.95);
    return vec4(land, A.a);
}

// ---- The water ----------------------------------------------------------------------------------------------------

// The slope of the water at a place (m), from a few trains of waves and the ripples of the wind. `t` is how far the eye
// is: waves too small to be seen from there are left out, and only roughen the reflection (`rough`).
vec2 waterSlope(vec2 q, float t, out float rough) {
    float wind = mix(0.20, 1.0, uWater.x);
    float px = t / uCam.x * mix(1.0, 3.2, smoothstep(0.25, 0.02, uLandInfo.y / max(t, 1.0)));
    vec2 g = vec2(0.0);
    float lost = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float lambda = mix(46.0, 1.9, pow(fi / 5.0, 0.7)) * mix(0.35, 1.0, uWater.x);
        float k = 2.0 * PI / lambda;
        float ang = 0.35 + fi * 1.13 + sin(fi * 2.7) * 0.5;
        vec2 dir = vec2(sin(ang * 0.55 - 0.2), cos(ang * 0.55 - 0.2));
        float amp = lambda * 0.013 * wind * (i == 0 ? 1.4 : 1.0);
        float seen = saturate(lambda / (px * 9.0) - 0.5);
        float ph = dot(q, dir) * k - uTime * sqrt(9.8 * k) + fi * 1.7;
        float c = cos(ph), s1 = sin(ph);
        g += dir * (k * amp * c * (1.0 + 0.5 * s1)) * seen;
        lost += (1.0 - seen) * k * amp;
    }
    vec2 drift = vec2(uTime * 0.021, uTime * 0.013);
    const float e = 0.004;
    vec2 rq = q * 0.043 + drift;
    float n0 = texture(tNoise2D, rq).g, nx = texture(tNoise2D, rq + vec2(e, 0.0)).g, nz = texture(tNoise2D, rq + vec2(0.0, e)).g;
    float seenR = saturate(1.6 / (px * 5.0) - 0.4);
    g += vec2(nx - n0, nz - n0) / e * 0.010 * wind * seenR;
    rough = saturate(0.015 + lost * 0.9 + (1.0 - seenR) * 0.08 * wind);
    return g;
}

void main() {
    vec2 vp = viewPx();
    vec3 rd = viewRay(vp);
    vec3 hazeCol = skyAt(vec2(vp.x, min(vp.y, uCam.y - 0.012 * uView.y)));
    float sunShows = mix(0.04, 1.0, cloudGap(uSunDir));

    vec3 col = heavens(rd, vp, 0.0, sunShows);
    // Birds, between us and the clouds: dark against the sky, a little of its light on them.
    float bird = birds(vp);
    if (bird > 0.0) col = mix(col, mAmbTop() * 0.10 + hazeCol * 0.04, bird * 0.9);

    vec2 luv = landUv(vp);
    // Grass and leaves stir: the picture is looked up a hair's breadth aside, by the wind.
    float water = 0.0;
    if (uLandInfo.w > 0.5 && luv.y > 0.0) {
        vec3 m = texture(tLandM, vec2(luv.x, min(luv.y, 0.9995))).rgb;
        water = step(0.5, m.r) * step(0.001, uLandInfo.y);
        float gust = sin(uTime * 1.7 + vp.x * 0.021 + vp.y * 0.013) * 0.6 + sin(uTime * 2.9 + vp.x * 0.047 - vp.y * 0.031) * 0.4;
        luv.x += m.g * gust * (0.35 + 1.2 * uAir.z) / uView.x * (1.0 - water);
    }
    float t;
    vec4 land = landAt(luv, rd, hazeCol, t);

    if (water > 0.5 && rd.y < -0.0005) {
        // Still or ruffled water: it mirrors the sky and the land beyond it, and shows its bed through the shallows.
        float h = uLandInfo.y;
        float tw = h / -rd.y;
        vec2 q = rd.xz * tw;
        float rough;
        vec2 sl = waterSlope(q, tw, rough);
        vec3 n = normalize(vec3(-sl.x, 1.0, -sl.y));
        vec3 r = reflect(rd, n);
        r.y = abs(r.y) + 0.002;
        r = normalize(r);
        vec3 rp = viewPoint(r);
        vec2 rvp = clamp(rp.xy, vec2(0.0), vec2(uView.x, uCam.y));
        vec3 refl = heavens(r, rvp, 1.0, sunShows);
        // The land beyond, mirrored: a thing at distance D is seen in the water 2h/D lower than a far thing would be.
        float te = r.y / max(length(r.xz), 1e-4);
        float D = 3000.0;
        vec2 mluv = luv;
        for (int i = 0; i < 3; i++) {
            float tl = te - 2.0 * h / D;
            vec3 dd = normalize(vec3(r.x, tl * length(r.xz), r.z));
            vec3 pp = viewPoint(dd);
            mluv = landUv(pp.xy);
            if (mluv.y <= 0.0 || mluv.y >= 1.0) { D = 1.0e6; break; }
            float dist = landDist(mluv) * length(dd.xz);
            D = max(dist, tw + 1.0);
        }
        if (D < 9.0e5 && mluv.y > 0.0) {
            float mt;
            float notWater = uLandInfo.w > 0.5 ? 1.0 - step(0.5, texture(tLandM, mluv).r) : 1.0;
            vec4 ml = landAt(mluv, r, hazeCol, mt);
            refl = mix(refl, ml.rgb, ml.a * notWater);
        }
        float fres = 0.02 + 0.98 * pow(1.0 - saturate(dot(-rd, n)), 5.0);
        // What lies under: the bed, dimmed and coloured by the depth of water over it.
        float depth = max(t - tw, 0.0) * -rd.y;
        vec3 deep = mix(vec3(0.010, 0.045, 0.060), vec3(0.020, 0.090, 0.085), uWater.y) * (mAmbTop() * 2.0 + mSunGround() * max(uSunTrue.y, 0.0));
        vec3 under = mix(deep, land.rgb, exp(-depth * mix(1.6, 0.45, uWater.y)) * land.a);
        col = mix(under, refl, fres);
    } else {
        col = mix(col, land.rgb, land.a);
    }

    // Rain here: fine streaks, slanted by the wind. Where snow lies it is cold enough to fall as snow: soft flakes,
    // drifting down slowly.
    if (uAir.y > 0.01) {
        vec2 rp = vp / uView.x;
        float streaks = 0.0;
        bool snowing = uLand.z > 0.5;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            if (snowing) {
                float sc = 30.0 + fi * 24.0;
                vec2 q = vec2(rp.x * sc + 0.7 * sin(rp.y * 6.0 + uTime * 0.5 + fi * 2.1) + uTime * (0.15 + 0.5 * uAir.z), rp.y * sc - uTime * (1.0 + fi * 0.45));
                vec2 cell = floor(q);
                vec2 f = fract(q) - 0.5 - (hash22(cell + fi * 9.1) - 0.5) * 0.6;
                streaks += step(0.78, hash12(cell + fi * 17.0)) * smoothstep(0.17, 0.05, length(f)) * (0.60 - fi * 0.13);
            } else {
                float sc = 26.0 + fi * 19.0;
                vec2 q = vec2(rp.x * sc + rp.y * sc * (0.10 + 0.2 * uAir.z), rp.y * (2.2 - fi * 0.5) + uTime * (1.9 + fi * 0.6));
                float r = hash12(floor(q) + fi * 17.0);
                float x = abs(fract(q.x) - 0.5);
                streaks += step(0.72, r) * smoothstep(0.06, 0.0, x) * smoothstep(0.0, 0.3, fract(q.y)) * smoothstep(1.0, 0.5, fract(q.y)) * (0.5 - fi * 0.12);
            }
        }
        col = mix(col, col + (mAmbTop() + mSunGround() * 0.1) * (snowing ? 0.9 : 0.5), streaks * uAir.y);
        col = mix(col, hazeCol, uAir.y * 0.18);
    }

    // (Nothing in the picture is brighter than a half-float can hold, whatever a driver does with the Sun.)
    o = vec4(clamp(col, 0.0, 200.0), 1.0);
}
