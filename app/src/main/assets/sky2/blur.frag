// A soft copy of the picture: the glow round bright things, and what is seen through the frosted glass under the
// day's times. uMode 0: shrink (each texel the mean of a block); 1: blur along uDir.
uniform sampler2D tSrc;
uniform int uMode;
uniform vec2 uDir;      // one source texel along the blur's direction, in uv
out vec4 o;
void main() {
    vec2 uv = gl_FragCoord.xy / uRes;
    if (uMode == 0) {
        vec2 d = uDir;
        vec3 c = texture(tSrc, uv + d * vec2(-1.0, -1.0)).rgb + texture(tSrc, uv + d * vec2(1.0, -1.0)).rgb
               + texture(tSrc, uv + d * vec2(-1.0, 1.0)).rgb + texture(tSrc, uv + d * vec2(1.0, 1.0)).rgb;
        // Keep the Sun's own disc from flooding the glow.
        o = vec4(min(c * 0.25, vec3(24.0)), 1.0);
    } else {
        vec3 c = texture(tSrc, uv).rgb * 0.2270270270;
        c += (texture(tSrc, uv + uDir * 1.3846153846).rgb + texture(tSrc, uv - uDir * 1.3846153846).rgb) * 0.3162162162;
        c += (texture(tSrc, uv + uDir * 3.2307692308).rgb + texture(tSrc, uv - uDir * 3.2307692308).rgb) * 0.0702702703;
        o = vec4(c, 1.0);
    }
}
