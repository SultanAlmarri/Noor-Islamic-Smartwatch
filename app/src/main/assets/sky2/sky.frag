// The clear sky of this moment, exposed, for every direction the camera sees (drawn small: the sky is smooth).
out vec4 o;
void main() {
    vec3 v = viewRay(viewPx());
    float alt = max(v.y, sin(0.2 * DEG));
    vec2 hz = normalize(v.xz + 1e-6) * sqrt(1.0 - alt * alt);
    v = vec3(hz.x, alt, hz.y);
    vec3 c = skyRadiance(v, uSunTrue, uSunDir) * mExposure();
    float wN = pow(1.0 - sqrt(clamp(asin(alt) / (0.5 * PI), 0.0, 1.0)), 2.5);
    // A moonlit night is a deeper, brighter blue.
    float moon = uMoon.w * smoothstep(-0.02, 0.15, uMoon.y);
    c += uNight * mix(NIGHT_TOP, NIGHT_LOW, wN) * (1.0 + 1.3 * moon);
    o = vec4(c, 1.0);
}
