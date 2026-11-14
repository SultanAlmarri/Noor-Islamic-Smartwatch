// What reaches the screen: the picture with a soft glow round its bright parts, through the filmic curve, darkened
// behind the text the way a photographer's graduated filter darkens a sky (so white letters read, and clouds keep
// their shape), with frosted glass under the day's times, and a fine grain so slow gradients never show steps.
uniform sampler2D tScene;
uniform sampler2D tBlur;
uniform vec4 uText;       // bottom of the text written on the sky (px from the top); 0 = none
uniform vec4 uPanel;      // top of the glass (px from the top; beyond the view = none), corner radius (px), tint 0..1, 1 = light glass
uniform vec3 uPanelTint;  // the glass's own colour (display)
uniform float uFade;      // 0 = not yet shown (the waiting sky and the page's colour) .. 1 = fully shown
uniform float uCover;     // how much of the sky is cloud, 0..1
out vec4 o;

// Relative luminance at most `lim`, bright parts eased down rather than cut (their shapes stay).
vec3 easeTo(vec3 c, float lim) {
    float l = luma(c);
    if (l <= lim * 0.5) return c;
    float x = (l - lim * 0.5) / (lim * 0.5);
    float y = lim * 0.5 + lim * 0.5 * (1.0 - exp(-x));
    return c * (y / l);
}

// How bright the sky may be at a height of the view where text is written on it. The letters carry a soft dark
// shadow of their own (TodayScreen, SkyText), so the sky behind them need not be as dark as bare white text would
// ask: at most 0.26 behind the small text (3.4:1 before the shadow), 0.40 behind the large countdown. Below the
// text the limit lifts gradually, so it reads as the natural darkening of the upper sky and not as a band.
float textLimit(float y) {
    float cap = uText.x;
    if (cap <= 0.0) return 1.0;
    float span = 0.12 * uView.y;
    if (y <= cap) return mix(0.50, 0.34, smoothstep(cap, cap - 0.9 * span, y));
    return mix(0.50, 1.0, smoothstep(cap, cap + 1.9 * span, y));
}

void main() {
    vec2 uv = gl_FragCoord.xy / uRes;
    vec2 vp = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y) * (uView / uRes);
    vec3 c = texture(tScene, uv).rgb;
    vec3 b = texture(tBlur, uv).rgb;
    // Glow: only what is brighter than white spreads far; a breath of softness everywhere.
    c += max(b - 1.1, 0.0) * 0.10 + (b - c) * 0.02;

    // Frosted glass: the scene behind it out of focus, tinted with the page.
    float inPanel = 0.0;
    if (vp.y > uPanel.x - 1.0) {
        float r = uPanel.y;
        vec2 q = vec2(min(vp.x, uView.x - vp.x), vp.y - uPanel.x);
        float d = (q.x < r && q.y < r) ? r - length(vec2(r) - q) : min(q.y, 1e4);
        inPanel = saturate(d + 0.5);
    }

    vec3 d = toneLinear(c);
    // The hour of colour is printed as a photographer prints it: a little richer than the rest of the day.
    d = clamp(mix(vec3(luma(d)), d, 1.0 + 0.28 * smoothstep(14.0, 3.0, uSunEl) * (1.0 - uNight)), 0.0, 1.0);
    // The graduated filter: one smooth darkening towards the text, as strong as the brightest thing likely to stand
    // behind it needs (a sunlit cloud when there are clouds, the sky itself when it is clear). Shapes and colours
    // keep their relations; only then is anything still too bright eased down.
    float lim = textLimit(vp.y);
    // The Sun itself is never dimmed: at midday it stands just under the text, and a dimmed Sun is a smudge. Its
    // disc, its rays and the glare round it keep all their light (what the filter would have made of them is simply
    // not used there); the letters' own shadow carries them. Nor is the Moon, the brightest thing of the night.
    float keep = 0.0;
    if (lim < 1.0 && uSunEl > -2.0) {
        vec3 sp = viewPoint(uSunDir);
        vec2 ds = (vp - sp.xy) / (0.13 * uView.x);
        keep = exp(-dot(ds, ds)) * step(0.0, sp.z) * smoothstep(-2.0, 1.0, uSunEl);
    }
    if (lim < 1.0 && uMoonLit.w > 0.0 && uNight > 0.05) {
        vec3 mp = viewPoint(normalize(uMoon.xyz));
        vec2 dm = (vp - mp.xy) / (0.06 * uView.x);
        keep = max(keep, exp(-dot(dm, dm)) * step(0.0, mp.z) * uMoonLit.w);
    }
    vec3 open = d;
    if (lim < 1.0) {
        float clouds = saturate(uCover * 2.0);
        float sunlit = luma(toneLinear(mSunCloud() + mAmbTop()));
        float clear = luma(toneLinear(mAmbTop() * 1.25 + mAmbLow() * 0.25));
        float ref = max(mix(clear, max(sunlit, clear), smoothstep(0.0, 0.3, clouds)), 1e-3);
        // (Only part of the way by the filter: a sky dimmed all the way to what its brightest cloud asks is a dull
        // sky. The rest is taken from the brightest parts alone.)
        float k = mix(1.0, min(1.0, lim / ref * 1.25), 0.6);
        d *= k;
        // Dimmed warm light turns muddy; as the eye sees shade, it is cool. Lean the darkened part towards blue.
        d = mix(d, luma(d) * vec3(0.88, 0.97, 1.16), 0.5 * (1.0 - k));
        d = easeTo(d, lim);
        d = mix(d, open, keep);
    }

    if (inPanel > 0.0) {
        vec3 g = toneLinear(b);
        vec3 tint = pow(uPanelTint, vec3(2.2));
        if (uPanel.w > 0.5) {
            // Light glass: dark ink must read on it, so it never falls below a bright haze. The scene behind it is
            // lifted whole (its light and shade keep their relations), not only its dark parts.
            const float floorL = 0.60;
            float l0 = luma(g);
            float l1 = floorL + 0.02 + (0.98 - floorL) * saturate((l0 - 0.06) / 0.62);
            // Its colours keep their hue, much paler: frosted, not painted.
            g = mix(g * (l1 / max(l0, 1e-3)), vec3(l1), 0.62);
            g = min(g, vec3(1.0));
            g = mix(g, tint, uPanel.z * 0.3);
            float l = luma(g);
            if (l < floorL) g = mix(g, vec3(1.0), (floorL - l) / max(1.0 - l, 1e-3));
        } else {
            // Dark glass, clear: the land is seen through it as through sunglasses, its forms and its texture kept,
            // a little out of focus; white letters read on it because nothing behind it is left brighter than they
            // can stand against. It is as dark as the brightest part of the scene behind it asks, all over, so light
            // and shade keep their relations.
            g = mix(d, g, 0.45);
            float top = 1.0 - uPanel.x / uView.y;
            float ref = 0.0;
            for (int i = 0; i < 6; i++) {
                vec2 at = vec2(0.18 + 0.32 * float(i % 3), top * (i < 3 ? 0.30 : 0.78));
                ref = max(ref, luma(toneLinear(texture(tBlur, at).rgb)));
            }
            g *= min(0.82, 0.20 / max(ref, 1e-3));
            // (Dimmed warm light alone turns to mud: the glass cools what it dims, a little.)
            g = mix(g, luma(g) * vec3(0.86, 0.97, 1.15), 0.22);
            g = mix(g, tint, uPanel.z * 0.25);
            g = easeTo(g, 0.24);
        }
        // A hairline of light along the upper edge of the glass.
        float edge = smoothstep(2.5, 0.0, abs(vp.y - uPanel.x - 1.0)) * 0.10;
        d = mix(d, g + edge, inPanel);
    }

    vec3 s = srgb(d);
    s += (ign(gl_FragCoord.xy + float(uFrame % 64) * 5.588) - 0.5) / 255.0 * 1.2;
    // The scene comes in out of what stood there while it was being made ready: a dusky sky (RealSky.WAITING_SKY)
    // above, the page's own colour where the glass will be.
    o = vec4(mix(mix(vec3(0.149, 0.208, 0.361), uPanelTint, inPanel), s, uFade), 1.0);
}
