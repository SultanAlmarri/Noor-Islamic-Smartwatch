// A star: a small soft point, brightest at its centre.
in vec4 vCol;
out vec4 o;
void main() {
    vec2 q = gl_PointCoord * 2.0 - 1.0;
    o = vec4(vCol.rgb * exp(-dot(q, q) * 4.0) * 1.8, 1.0);
}
