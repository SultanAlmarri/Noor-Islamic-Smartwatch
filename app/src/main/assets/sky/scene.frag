// Puts the picture together, in linear light: the sky of the moment, the Sun and the moon, the stars, the high ice
// clouds, the marched clouds lit with the colours of the moment, and the land or the sea under them.

uniform sampler2D tSky;       // the clear sky for every direction in view (sky.frag)
uniform sampler2D tCloudA;    // cloud.frag
uniform sampler2D tCloudB;
uniform sampler2D tG0;        // the land: colour and kind
uniform sampler2D tG1;        // its normal and how open to the sky it is
uniform sampler2D tG2;        // how far it is
uniform vec2 uCloudRes;       // size of the cloud buffers, px
uniform vec3 uCelX, uCelY, uCelZ, uGalN, uGalC;   // the turning sky: celestial axes, the Milky Way's pole and centre
uniform vec4 uSky;            // aurora strength, meteor (0..1 along its track, 0 = none), lightning flash, rainbow
uniform vec4 uLand;           // biome, season (0 winter .. 1 summer), snow, wetness
uniform vec4 uAir;            // mist near the ground, rain here, wind strength, dust
uniform vec4 uTouch;          // where the scene was last touched (px), seconds since, strength

out vec4 o;

vec3 skyAt(vec2 vp) { return texture(tSky, vec2(vp.x / uView.x, 1.0 - vp.y / uView.y)).rgb; }

// The sky colour in a direction (clamped to what the camera sees).
vec3 skyDir(vec3 d) {
    vec3 p = viewPoint(d);
    return skyAt(clamp(p.xy, vec2(0.0), uView));
}

float sphereExitS(float eye, vec3 rd, float h) {
    float r0 = RE + eye, r = RE + h;
    float b = r0 * rd.y;
    return -b + sqrt(max(b * b - (r0 * r0 - r * r), 0.0));
}

// Faint stars fixed to the turning sky, so they rise and set with the night.
vec3 faintStars(vec3 d, float alt) {
    vec3 c = vec3(dot(d, uCelX), dot(d, uCelY), dot(d, uCelZ));
    vec3 a = abs(c);
    // A cube round the sky keeps the cells nearly square everywhere.
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
        float px = n * 2.0 / uCam.x * 0.55;            // one pixel, in cells
        float spot = exp(-dot(dlt, dlt) / (px * px * (k == 0 ? 1.6 : 0.9)));
        float mag = pow(hash12(id + 5.0 + face), 6.0);
        float tw = 0.75 + 0.25 * sin(uTime * (2.0 + 5.0 * r.x) + r.y * 40.0) * smoothstep(40.0, 5.0, alt);
        vec3 tint = mix(vec3(1.0, 0.86, 0.72), vec3(0.78, 0.86, 1.0), r.y);
        sum += keep * spot * tw * tint * (k == 0 ? 0.035 + 0.75 * mag : 0.012 + 0.20 * mag);
    }
    return sum;
}

// The Milky Way, where it really is: a band along the galactic plane, bright towards the centre, cut by dust.
vec3 milkyWay(vec3 d) {
    float sb = dot(d, uGalN);
    vec3 e2 = cross(uGalN, uGalC);
    float l = atan(dot(d, e2), dot(d, uGalC));
    float b = asin(clamp(sb, -1.0, 1.0));
    float cen = exp(-sq(l / 0.9));
    float width = mix(0.11, 0.20, cen);
    float band = exp(-sq(b / width));
    vec2 q = vec2(l * 3.0, b * 9.0);
    float clumps = texture(tNoise2D, q * 0.13 + 0.3).r * 0.6 + texture(tNoise2D, q * 0.41).g * 0.4;
    float dust = smoothstep(0.42, 0.62, texture(tNoise2D, vec2(l * 0.55, b * 2.6) + 0.17).g) * exp(-sq((b + 0.015) / (0.05 + 0.04 * cen)));
    float grain = 0.6 + 0.8 * texture(tNoise2D, q * 1.7 + 0.5).a * texture(tNoise2D, q * 0.9).b;
    float glow = band * (0.30 + 1.0 * cen) * (0.45 + 1.1 * clumps * clumps) * (1.0 - 0.8 * dust) * grain;
    glow += 0.5 * exp(-sq(b / 0.09)) * exp(-sq(l / 0.22)) * (1.0 - 0.6 * dust);       // the bulge
    vec3 col = mix(vec3(0.62, 0.72, 1.0), vec3(1.0, 0.86, 0.68), cen * 0.8);
    return col * glow * 0.050;
}

// The aurora: curtains of light a hundred kilometres up and more, folded along the far sky, green at their sharp
// lower border and fading upward into red and violet, with fine rays along the field lines. They drift and pulse.
vec3 aurora(vec3 rd) {
    float s = uSky.x;
    if (s <= 0.01 || rd.y < 0.03) return vec3(0.0);
    vec3 col = vec3(0.0);
    float jit = ign(gl_FragCoord.xy);
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

// The moon as a lit sphere: its real phase, the grey seas, earthshine on the dark part.
vec4 moonDisc(vec3 rd) {
    float vis = uMoonLit.w;
    if (vis <= 0.0) return vec4(0.0);
    vec3 m = normalize(uMoon.xyz);
    float rad = 0.0105;                          // drawn a little over twice its real size, as the eye remembers it
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), m));
    vec3 up = cross(m, right);
    vec2 q = vec2(dot(rd, right), dot(rd, up)) / rad;
    if (uCam.w > 0.5) q.x = -q.x;
    float r2 = dot(q, q);
    if (r2 > 1.2) return vec4(0.0);
    float edge = smoothstep(1.0, 0.94, sqrt(r2));
    vec3 n = vec3(q.x, -q.y, sqrt(max(1.0 - r2, 0.0)));
    float lit = smoothstep(-0.05, 0.25, dot(n, normalize(uMoonLit.xyz)));
    float seas = texture(tNoise2D, q * 0.11 + 0.21).r * 0.6 + texture(tNoise2D, q * 0.27 + 0.6).g * 0.4;
    float alb = mix(0.62, 1.0, smoothstep(0.42, 0.58, seas)) * mix(0.82, 1.0, n.z);
    vec3 col = vec3(1.0, 0.97, 0.90) * alb * (lit * (0.9 + 6.0 * uNight) + 0.02 * uNight);
    return vec4(col * edge * vis, edge * vis * lit);
}

vec4 cloudTap(sampler2D t, vec2 uv) {
    vec2 px = 1.0 / uCloudRes;
    vec4 c = texture(t, uv) * 0.36;
    c += texture(t, uv + px * vec2(1.2, 0.4)) * 0.16;
    c += texture(t, uv + px * vec2(-1.2, -0.4)) * 0.16;
    c += texture(t, uv + px * vec2(-0.4, 1.2)) * 0.16;
    c += texture(t, uv + px * vec2(0.4, -1.2)) * 0.16;
    return c;
}

// The marched clouds at a point of the view, in the light of the moment: colour to add (rgb), what shows through (a).
vec4 cloudsAt(vec2 vp, vec3 hazeCol, vec3 ambLow, vec3 ambTop, float moonW) {
    vec2 cuv = vec2(vp.x / uView.x, 1.0 - vp.y / uView.y);
    vec4 ca = cloudTap(tCloudA, cuv);
    vec4 cb = cloudTap(tCloudB, cuv);
    vec3 lightLow = mSunCloud() + mMoonLight() * moonW;
    vec3 lightMid = mSunMid() + mMoonLight() * moonW;
    vec3 ambCloud = mix(ambLow, ambTop, 0.55) * 1.15;
    vec3 c = ca.r * lightLow + ca.g * lightMid + ca.b * ambCloud;
    // Lightning inside the storm lights it from within.
    c += uSky.z * vec3(0.75, 0.8, 1.0) * ca.b * 3.0;
    // Far clouds take the colour of the air in front of them.
    c = mix(c, hazeCol * (1.0 - ca.a), cb.x * 0.85);
    return vec4(c, ca.a);
}

float unpackDist(vec2 v) { return exp2((v.x * 255.0 * 256.0 + v.y * 255.0) / 65535.0 * 17.0); }

uniform vec4 uWater;      // roughness of the water (0 glass .. 1 storm), its colour (0 dark and deep .. 1 clear and green), -, -

// The slope of the water at a place (m), from a few trains of waves and the ripples of the wind. `t` is how far the eye
// is: waves too small to be seen from there are left out, and only roughen the reflection (`rough`).
vec2 waterSlope(vec2 q, float t, out float rough) {
    float wind = mix(0.25, 1.0, uWater.x);
    // Metres under one pixel there: seen at a grazing angle a pixel covers a long strip of water.
    float px = t / uCam.x * mix(1.0, 3.2, smoothstep(0.25, 0.02, uCam.z / max(t, 1.0)));
    vec2 g = vec2(0.0);
    float lost = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float lambda = mix(62.0, 2.6, pow(fi / 5.0, 0.7)) * mix(0.55, 1.0, uWater.x);
        float k = 2.0 * PI / lambda;
        float ang = 0.35 + fi * 1.13 + sin(fi * 2.7) * 0.5;
        vec2 dir = vec2(sin(ang * 0.55 - 0.2), cos(ang * 0.55 - 0.2));
        float amp = lambda * 0.016 * wind * (i == 0 ? 1.5 : 1.0);
        float seen = saturate(lambda / (px * 9.0) - 0.5);
        float ph = dot(q, dir) * k - uTime * sqrt(9.8 * k) + fi * 1.7;
        float c = cos(ph), s1 = sin(ph);
        g += dir * (k * amp * c * (1.0 + 0.5 * s1)) * seen;       // crests sharper than a sine's
        lost += (1.0 - seen) * k * amp;
    }
    vec2 drift = vec2(uTime * 0.021, uTime * 0.013);
    const float e = 0.004;
    vec2 rq = q * 0.043 + drift;
    float n0 = texture(tNoise2D, rq).g, nx = texture(tNoise2D, rq + vec2(e, 0.0)).g, nz = texture(tNoise2D, rq + vec2(0.0, e)).g;
    float seenR = saturate(1.6 / (px * 5.0) - 0.4);
    g += vec2(nx - n0, nz - n0) / e * 0.012 * wind * seenR;
    rough = saturate(0.02 + lost * 0.9 + (1.0 - seenR) * 0.10 * wind);
    return g;
}

void main() {
    vec2 vp = viewPx();
    vec3 rd = viewRay(vp);
    float eye = uCam.z * 0.001;
    float alt = asin(clamp(rd.y, -1.0, 1.0)) / DEG;
    float mu = dot(rd, uSunDir);
    float moonW = smoothstep(-6.0, -10.0, uSunEl);
    vec3 ambTop = mAmbTop(), ambLow = mAmbLow();

    vec3 col = skyAt(vp);

    // Night: the Milky Way, the stars (dimmed by the air near the horizon), the moon.
    if (uNight > 0.01) {
        float clear = smoothstep(0.5, 14.0, alt);
        float dark = uNight * uNight * (1.0 - 0.6 * uMoon.w * smoothstep(0.0, 0.2, uMoon.y));
        col += milkyWay(rd) * smoothstep(0.55, 1.0, uNight) * clear * (1.0 - 0.7 * uMoon.w * smoothstep(0.0, 0.2, uMoon.y));
        col += faintStars(rd, alt) * dark * clear;      // the catalogue's bright stars are drawn as points (stars.vert)
        col += aurora(rd) * uNight * uNight;
    }
    vec4 moon = moonDisc(rd);
    col = col * (1.0 - moon.a * 0.85 * uNight) + moon.rgb;
    // The moon's glow in the air around it.
    if (uMoonLit.w > 0.0) {
        float mm = dot(rd, normalize(uMoon.xyz));
        col += vec3(0.55, 0.66, 0.9) * uMoon.w * uMoonLit.w * uNight * (0.035 * pow(saturate(mm), 90.0) + 0.012 * pow(saturate(mm), 12.0));
    }

    // The marched clouds, in the light of the moment (looked up first: the Sun's disc does not show through thick cloud).
    vec3 hazeCol = skyAt(vec2(vp.x, min(vp.y, uCam.y - 0.012 * uView.y)));
    vec4 cl = cloudsAt(vp, hazeCol, ambLow, ambTop, moonW);
    float sunShows = smoothstep(0.02, 0.22, cl.a);

    // The Sun's disc, with a darker limb.
    float sunR = 0.0058;
    float sd = acos(clamp(mu, -1.0, 1.0)) / sunR;
    if (sd < 1.3 && uSunEl > -2.0) {
        float limb = 1.0 - 0.45 * (1.0 - sqrt(max(1.0 - sd * sd, 0.0)));
        col += mSunDisc() * limb * smoothstep(1.0, 0.9, sd) * sunShows;
    }
    // Its glare: light scattered in the eye and the lens, tight round the disc and a wide soft bloom.
    if (uSunEl > -3.0) {
        float th = acos(clamp(mu, -1.0, 1.0));
        vec3 sc = mSunDisc() / 2500.0;
        col += sc * (2.6 * exp(-th / 0.016) * sunShows + 0.36 * exp(-th / 0.07) * (0.3 + 0.7 * sunShows) + 0.06 * exp(-th / 0.22)) * smoothstep(-3.0, 0.0, uSunEl);
    }

    // High ice clouds, lit long after the ground has gone dark.
    float tH = sphereExitS(eye, rd, HIGH_ALT);
    if (rd.y > 0.003 && tH < 420.0) {
        vec3 pw = vec3(0.0, RE + eye, 0.0) + rd * tH;
        // How many texels of the streak pattern a pixel covers there (its tile is 15 km across 256 texels).
        float hlod = max(log2(tH / uCam.x * (uView.x / uRes.x) / max(rd.y, 0.04) / 0.0586), 0.0);
        float c = highCover(pw.xz, hlod);
        if (c > 0.002) {
            float thin = 1.0 - exp(-c * 1.5 / max(rd.y, 0.10));
            float far = exp(-tH * 0.006 * uHaze);
            vec3 lightHigh = mSunHigh() + mMoonLight() * moonW;
            float e = 0.34 + 0.20 * min(hg(mu, 0.72), 14.0) + 0.05 * hg(mu, -0.4);
            vec3 cc = lightHigh * e + mix(ambLow, ambTop, 0.7) * 0.42;
            float a = thin * mix(0.25, 0.72, saturate(c)) * far;
            col = mix(col, cc, a);
            // A veil of ice crystals bends light into a ring 22 degrees from the Sun (or the moon).
            float ring = exp(-sq((acos(clamp(mu, -1.0, 1.0)) / DEG - 22.4) / 1.1));
            col += lightHigh * ring * uCloudC.z * thin * 0.22 * vec3(1.0, 0.93, 0.86);
        }
    }

    col = col * cl.a + cl.rgb;

    // The land and the water, relit from what was stored of them (gbuffer.frag).
    ivec2 ip = ivec2(gl_FragCoord.xy);
    vec4 g0 = texelFetch(tG0, ip, 0);
    float kind = floor(g0.a * 8.0);
    if (kind > 0.5) {
        vec4 g1 = texelFetch(tG1, ip, 0), g2 = texelFetch(tG2, ip, 0);
        vec3 alb = g0.rgb * g0.rgb;
        vec3 n = normalize(g1.xyz * 2.0 - 1.0);
        float open = g1.a;
        float t = unpackDist(g2.xy);
        vec3 p = vec3(0.0, uCam.z, 0.0) + rd * t;
        vec3 L = uLightDir;
        vec3 light = mSunGround() * 0.72 + mMoonLight() * moonW;
        // Shadows: the land's own (how high it stands towards the light) and the clouds'.
        float az = atan(L.x, L.z);
        float hz = mix(sq(g2.z), sq(g2.w), clamp(az / 0.30 * 0.5 + 0.5, 0.0, 1.0)) * 1.6 - 0.2;
        float lt = L.y / max(length(L.xz), 1e-3);
        float shLand = smoothstep(hz - 0.006, hz + 0.028, lt);
        float shCloud = mix(1.0, cloudShadow(p.xz * 0.001), smoothstep(0.02, 0.2, L.y));
        float sh = shLand * shCloud;
        vec3 land;

        if (kind > 6.5) {
            // Water: a moving mirror. The sky and its clouds in it, the Sun's glitter path, the deep under it.
            float rough;
            vec2 sl = waterSlope(p.xz, t, rough);
            vec3 wn = normalize(vec3(-sl.x, 1.0, -sl.y));
            vec3 calm = normalize(vec3(-sl.x * 0.35, 1.0, -sl.y * 0.35));
            vec3 R = reflect(rd, calm);
            R.y = abs(R.y) + 0.015;
            R = normalize(R);
            float fres = 0.02 + 0.98 * pow(1.0 - saturate(dot(-rd, wn)), 5.0);
            fres = mix(fres, 0.5 * fres + 0.25, rough);        // unseen waves turn many facets towards the eye
            vec3 rp = viewPoint(R);
            vec2 rv = clamp(rp.xy, vec2(0.0), vec2(uView.x, uCam.y - 2.0));
            vec3 refl = skyAt(rv);
            vec4 rc = cloudsAt(rv, hazeCol, ambLow, ambTop, moonW);
            refl = refl * rc.a + rc.rgb;
            // The glitter path: the Sun's (or the moon's) image broken up by the waves, wider the rougher they are.
            bool day = uSunEl > -1.0;
            vec3 src = day ? uSunDir : normalize(uMoon.xyz);
            vec3 hv = normalize(src - rd);
            float gl = pow(saturate(dot(wn, hv)), mix(2400.0, 90.0, sqrt(rough)));
            vec3 srcCol = day ? mSunDisc() / 2500.0 * 26.0 : vec3(0.8, 0.9, 1.0) * uMoon.w * uMoonLit.w * 3.0 * uNight;
            vec3 sp = viewPoint(src);
            float through = texture(tCloudA, vec2(sp.x / uView.x, 1.0 - sp.y / uView.y)).a;
            vec3 glitter = srcCol * gl * mix(1.0, 0.16, sqrt(rough)) * through * step(0.0, src.y);
            // The water's own colour: deep blue-green, lit a little from within; the bed shows in the shallows.
            vec3 deep = mix(vec3(0.010, 0.040, 0.070), vec3(0.015, 0.16, 0.19), uWater.y);
            vec3 body = deep * (light * (0.25 + 0.5 * saturate(L.y)) * shCloud + mix(ambLow, ambTop, 0.7) * 1.3);
            float shallow = 1.0 - alb.r;
            body = mix(body, vec3(0.28, 0.30, 0.22) * (light * saturate(L.y) * shCloud + ambTop) * 0.55, shallow * 0.55);
            land = mix(body, refl, fres) + glitter;
            // Whitecaps when it blows.
            float capN = texture(tNoise2D, p.xz * 0.011 + vec2(uTime * 0.008, 0.0)).b * texture(tNoise2D, p.xz * 0.047 - vec2(0.0, uTime * 0.02)).r;
            float caps = smoothstep(0.42 - 0.2 * uWater.x, 0.6 - 0.2 * uWater.x, capN) * smoothstep(0.45, 0.9, uWater.x) * saturate(1.6 - t / 2500.0);
            land = mix(land, vec3(0.85) * (light * saturate(L.y + 0.2) * shCloud + ambTop * 1.2), caps * 0.8);
        } else {
            float ndl = saturate(dot(n, L));
            // Skylight from above, the warmer light of the horizon from the side, and light thrown back up by the ground.
            vec3 skyAmb = mix(ambLow, ambTop, saturate(n.y * 0.6 + 0.4)) * open;
            vec3 bounce = alb * light * saturate(L.y) * 0.18 * saturate(0.6 - 0.5 * n.y);
            vec3 direct = light * ndl * sh;
            land = alb * (direct + skyAmb + bounce);
            float toSun = saturate(dot(rd, uSunDir));
            vec3 hv = normalize(L - rd);
            if (kind < 2.5 && kind > 1.5) {
                // Sand: a sheen towards the Sun, and grains that flash.
                land += light * sh * 0.10 * pow(saturate(dot(n, hv)), 14.0);
                float spark = step(0.992, hash12(floor(p.xz * (t < 60.0 ? 90.0 : 30.0)) + floor(uTime * 1.5) * 0.37));
                land += light * sh * spark * pow(saturate(dot(n, hv)), 6.0) * 1.2 * saturate(1.0 - t / 180.0);
            } else if (kind < 4.5 && kind > 2.5) {
                // Snow and ice: light gets into them, so their shade is never dead; ice shines.
                land += alb * light * 0.10 * (1.0 - ndl) * shCloud;
                bool ice = kind > 3.5;
                land += light * sh * (ice ? 0.9 : 0.25) * pow(saturate(dot(n, hv)), ice ? 90.0 : 24.0);
                if (ice) {
                    float fr = 0.03 + 0.6 * pow(1.0 - saturate(dot(-rd, n)), 4.0);
                    land = mix(land, skyDir(reflect(rd, n)), fr * 0.7);
                }
                float spark = step(0.9975, hash12(floor(p.xz * (t < 40.0 ? 60.0 : 14.0)) + floor(uTime * 2.0) * 0.21));
                land += light * sh * spark * 1.5 * saturate(1.0 - t / 250.0);
            } else if (kind > 4.5) {
                // Leaves and grass: the Sun shines through them when it stands behind.
                land += alb * light * sh * 0.55 * pow(toSun, 3.0) * (1.0 - ndl * 0.5);
                if (kind < 5.5) {
                    // The wind runs through the grass in waves.
                    float gust = texture(tNoise2D, p.xz / 90.0 - vec2(uTime * 0.035, uTime * 0.012) * (0.4 + uAir.z)).r;
                    land *= 0.88 + 0.26 * gust * (0.3 + 0.7 * uAir.z) * saturate(1.0 - t / 1500.0);
                }
            }
            // Rain leaves the ground darker, and shining where water stands on it (level places, in patches).
            if (uLand.w > 0.01) {
                float level = smoothstep(0.80, 0.98, n.y);
                float pool = smoothstep(0.52, 0.66, texture(tNoise2D, p.xz / 23.0 + 0.4).r) * level;
                float fr = 0.03 + 0.30 * pow(1.0 - saturate(dot(-rd, n)), 5.0);
                vec3 wetted = land * 0.72;
                land = mix(land, mix(wetted, wetted * 0.8 + skyDir(reflect(rd, vec3(0.0, 1.0, 0.0))) * fr, pool), uLand.w * (0.5 + 0.5 * level));
            }
        }

        // The air between: far things fade into the colour of the sky behind them; mist lies in the low ground.
        // Haze lies low (most of it in the first kilometre or two), so a far peak stands clear above a hazed foot.
        float tk = t * 0.001;
        float ya = uCam.z * 0.001, yb = max(p.y, 0.0) * 0.001;
        const float HH = 1.3;
        float layer = abs(yb - ya) > 1e-3 ? HH / (yb - ya) * (exp(-ya / HH) - exp(-yb / HH)) : exp(-ya / HH);
        float fog = 1.0 - exp(-tk * (0.006 + (0.020 + 0.024 * (uHaze - 1.0) + 0.06 * uAir.w) * layer));
        float glow = pow(saturate(dot(rd, uSunDir)), 8.0);
        land = mix(land, hazeCol * (1.0 + 0.35 * glow), fog);
        if (uAir.x > 0.005) {
            const float hf = 16.0;
            float y0 = max(min(uCam.z, p.y), 0.0), y1 = max(max(uCam.z, p.y), 0.0);
            float od = uAir.x * 0.0045 * min(hf * (exp(-y0 / hf) - exp(-y1 / hf)) / max(abs(rd.y), 1e-4), t);
            od *= 0.6 + 0.8 * texture(tNoise2D, p.xz / 900.0 + vec2(uTime * 0.0012, 0.0)).r;
            vec3 mistCol = mix(ambLow, ambTop, 0.4) * 1.1 + (mSunGround() * 0.22 + mMoonLight() * moonW * 0.3) * (0.4 + 1.6 * glow);
            land = mix(land, mistCol, 1.0 - exp(-od));
        }
        col = land;
    }

    // Rain here: fine streaks, slanted by the wind.
    if (uAir.y > 0.01) {
        vec2 rp = vp / uView.x;
        float streaks = 0.0;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float sc = 26.0 + fi * 19.0;
            vec2 q = vec2(rp.x * sc + rp.y * sc * (0.10 + 0.2 * uAir.z), rp.y * (2.2 - fi * 0.5) + uTime * (1.9 + fi * 0.6));
            float r = hash12(floor(q) + fi * 17.0);
            float x = abs(fract(q.x) - 0.5);
            streaks += step(0.72, r) * smoothstep(0.06, 0.0, x) * smoothstep(0.0, 0.3, fract(q.y)) * smoothstep(1.0, 0.5, fract(q.y)) * (0.5 - fi * 0.12);
        }
        col = mix(col, col + (ambTop + mSunGround() * 0.1) * 0.5, streaks * uAir.y);
        col = mix(col, hazeCol, uAir.y * 0.18);
    }

    o = vec4(max(col, 0.0), 1.0);
}
