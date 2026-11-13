// What reaches the screen: the picture with a soft glow round its bright parts, through the filmic curve, kept dark
// enough behind the text for white letters to read, with frosted glass under the day's times, and a fine grain so
// slow gradients never show steps.
uniform sampler2D tScene;
uniform sampler2D tBlur;
uniform vec4 uText;       // bottom of the text written on the sky (px from the top); 0 = none
uniform vec4 uPanel;      // top of the glass (px from the top; beyond the view = none), corner radius (px), tint 0..1, 1 = light glass
uniform vec3 uPanelTint;  // the glass's own colour (display)
uniform float uFade;      // 0 = not yet shown .. 1 = fully shown
out vec4 o;

// Relative luminance at most `lim`, bright parts eased down rather than cut (their shapes stay).
vec3 easeTo(vec3 c, float lim) {
    float l = luma(c);
    if (l <= lim * 0.6) return c;
    float x = (l - lim * 0.6) / (lim * 0.4);
    float y = lim * 0.6 + lim * 0.4 * (1.0 - exp(-x));
    return c * (y / l);
}

// How bright the sky may be at a height of the view where text is written on it: small text needs 4.5:1 against
// white (luminance 0.18), the large countdown just above the text's bottom 3:1 (0.30); below the text the limit
// lifts over a short distance, so it reads as the natural darkening of the upper sky and not as a band.
float textLimit(float y) {
    float cap = uText.x;
    if (cap <= 0.0) return 1.0;
    float span = 0.085 * uView.y;
    if (y <= cap) return mix(0.30, 0.18, smoothstep(cap, cap - 0.9 * span, y));
    return mix(0.30, 1.0, smoothstep(cap, cap + span, y));
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
    d = easeTo(d, textLimit(vp.y));

    if (inPanel > 0.0) {
        vec3 g = toneLinear(b);
        vec3 tint = pow(uPanelTint, vec3(2.2));
        if (uPanel.w > 0.5) {
            // Light glass: dark ink must read on it, so it never falls below a bright haze.
            g = mix(g, tint, uPanel.z);
            float l = luma(g);
            float floorL = 0.62;
            if (l < floorL) g = mix(g, vec3(1.0), (floorL - l) / max(1.0 - l, 1e-3));
        } else {
            // Dark glass: white letters must read on it.
            g = easeTo(g, 0.20);
            g = mix(g, tint, uPanel.z);
            g = easeTo(g, 0.105);
        }
        // A hairline of light along the glass's upper edge.
        float edge = smoothstep(2.5, 0.0, abs(vp.y - uPanel.x - 1.0)) * 0.10;
        d = mix(d, g + edge, inPanel);
    }

    vec3 s = srgb(d);
    s += (ign(gl_FragCoord.xy + float(uFrame % 64) * 5.588) - 0.5) / 255.0 * 1.2;
    o = vec4(s * uFade, 1.0);
}
