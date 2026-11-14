// The land's shading in the light of the moment. The land was rendered by a path tracer under a set of Suns, one
// picture each (how brightly every pixel is lit, shadows and all); the light of any moment lies between a few of
// them. This pass mixes those few, once whenever the Sun has moved; scene.frag then colours the result.
uniform sampler2D tS0, tS1, tS2, tS3, tS4;
uniform vec4 uLandW;      // weights of the first four
uniform float uLandW4;    // and of the fifth
out vec4 o;

void main() {
    vec2 uv = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y) / uRes;
    // (The pictures hold the square root of the shading: undo it, mix, and store the root again.)
    float s = sq(texture(tS0, uv).r) * uLandW.x + sq(texture(tS1, uv).r) * uLandW.y + sq(texture(tS2, uv).r) * uLandW.z
            + sq(texture(tS3, uv).r) * uLandW.w + sq(texture(tS4, uv).r) * uLandW4;
    o = vec4(sqrt(max(s, 0.0)), 0.0, 0.0, 1.0);
}
