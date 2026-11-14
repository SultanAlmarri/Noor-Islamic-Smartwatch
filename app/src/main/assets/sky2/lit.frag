// The shading of a baked picture (the land, or a set of clouds) in the light of the moment. The picture was rendered
// by a path tracer under a set of Suns, one image each; the light of any moment lies between a few of them. This
// pass mixes those few, whenever the Sun has moved. R: the Sun's (or the Moon's) light; G: the light of the glow
// that stands in the sky round a Sun at or under the horizon. Both as square roots, as the images are.
uniform sampler2D tS0, tS1, tS2, tS3, tS4;   // the Suns the moment lies between
uniform sampler2D tG0, tG1;                  // the glow on the left and on the right
uniform vec4 uLitW;       // weights of the first four Suns
uniform vec4 uLitV;       // weight of the fifth; of the two glows; 1 = the picture is mirrored (right to left)
out vec4 o;

void main() {
    vec2 uv = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y) / uRes;
    if (uLitV.w > 0.5) uv.x = 1.0 - uv.x;
    float s = sq(texture(tS0, uv).r) * uLitW.x + sq(texture(tS1, uv).r) * uLitW.y + sq(texture(tS2, uv).r) * uLitW.z
            + sq(texture(tS3, uv).r) * uLitW.w + sq(texture(tS4, uv).r) * uLitV.x;
    float g = sq(texture(tG0, uv).r) * uLitV.y + sq(texture(tG1, uv).r) * uLitV.z;
    o = vec4(sqrt(max(s, 0.0)), sqrt(max(g, 0.0)), 0.0, 1.0);
}
