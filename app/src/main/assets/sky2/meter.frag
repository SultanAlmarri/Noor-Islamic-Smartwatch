// The light of the moment, as a photographer meters it: the exposure that follows the Sun down through twilight (as
// the eye adapts), skylight from above and from the horizon, and sunlight as it arrives at the ground, at the clouds
// and at the high ice clouds, each reddened by the air it has crossed. Ten values in a row; see atmosphere.glsl.
out vec4 o;

// Atmospheric refraction (Saemundsson), degrees, for a true elevation in degrees.
float refraction(float el) {
    float h = max(el, -1.9);
    return 1.02 / tan((h + 10.3 / (h + 5.11)) * DEG) / 60.0;
}

void main() {
    int k = int(gl_FragCoord.x);
    vec3 z = skyRadiance(vec3(0.0, sin(89.0 * DEG), cos(89.0 * DEG)), uSunTrue, uSunDir);
    float ex = min(600.0, targetLuminance(uSunEl) / max(luma(z), 1e-9));
    vec3 wb = sunWhite();
    float mu = sin(uSunEl * DEG);
    // Lit surfaces are held a little under the sky, as a photographer exposes for the highlights.
    float k0 = E_SUN * ex / PI * 0.80;
    vec3 c = vec3(0.0);
    float a = 1.0;
    if (k == 0) { c = z * ex + NIGHT_TOP * uNight * 1.6; a = ex; }
    else if (k == 1) {
        vec2 hz = normalize(vec2(uSunDir.z, -uSunDir.x) + 1e-5);
        vec3 side = vec3(hz.x * cos(4.0 * DEG), sin(4.0 * DEG), hz.y * cos(4.0 * DEG));
        c = skyRadiance(side, uSunTrue, uSunDir) * ex + NIGHT_TOP * uNight * 2.4;
        a = uNight;
    }
    else if (k == 2) c = transmittance(0.3, mu) * wb * k0;
    else if (k == 3) c = transmittance(2.5, mu) * wb * k0;
    else if (k == 4) c = transmittance(5.5, mu) * wb * k0;
    else if (k == 5) c = transmittance(9.5, mu) * wb * k0;
    else if (k == 6) {
        // The Sun's own disc: white-hot when high, a red-gold ball when the air has dimmed it. Never brighter than a
        // picture in half-floats can hold with room to spare (an overflow there turns the Sun black on some phones).
        vec3 t = transmittance(EYE_KM, sin((uSunEl + refraction(uSunEl)) * DEG)) * wb;
        // (Its hue is the air's doing; its brightness falls far less than its light does: a setting Sun is still
        // the brightest thing in the picture.)
        // High, it is far brighter than anything a picture can show: white-hot. Low, the air has dimmed it enough for
        // a camera to hold its colour: a clear red-gold ball, still the brightest thing in the picture.
        float m = max(max(t.r, t.g), max(t.b, 1e-5));
        float l = saturate(luma(t));
        c = min(pow(t / m, vec3(1.25)) * (9.0 + 40.0 * l * l), vec3(60.0));
    }
    else if (k == 8) {
        // The glow of the sky round a low Sun, as light on things: the bright air low in the Sun's quarter.
        vec2 hz = normalize(uSunDir.xz + 1e-5);
        vec3 at = vec3(hz.x * cos(5.0 * DEG), sin(5.0 * DEG), hz.y * cos(5.0 * DEG));
        c = skyRadiance(at, uSunTrue, uSunDir) * ex * 0.45 * smoothstep(12.0, 2.0, uSunEl) + NIGHT_LOW * uNight * 0.2;
    }
    else if (k == 9) c = vec3(0.0);
    else {
        // Moonlight: by how much of the moon is lit and how high it stands; it only matters once the Sun is well down.
        float up = smoothstep(-0.02, 0.12, uMoon.y);
        c = vec3(0.050, 0.072, 0.120) * uMoon.w * up * uNight;
    }
    o = vec4(c, a);
}
