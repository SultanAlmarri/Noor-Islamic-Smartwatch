// How much sunlight survives the air on its way to height h from a direction mu (the sine of its elevation there):
// the table every other pass reads. Zero where the ray meets the ground: the Earth's shadow. Rebuilt when the haze changes.
out vec4 o;
void main() {
    float i = floor(gl_FragCoord.y), j = floor(gl_FragCoord.x);
    float xh = i / (TRANS_H - 1.0);
    float h = xh * xh * (RA - RE);
    float r = RE + h;
    float x = j / (TRANS_W - 1.0) * 2.0 - 1.0;
    float mu = sign(x) * x * x;
    float b = r * mu;
    float discG = b * b - (r * r - RE * RE);
    if (mu < 0.0 && discG > 0.0 && -b - sqrt(discG) > 0.0) { o = vec4(0.0, 0.0, 0.0, 1.0); return; }
    float len = -b + sqrt(max(0.0, b * b - (r * r - RA * RA)));
    const int N = 48;
    float tr = 0.0, tm = 0.0, to = 0.0;
    for (int s = 0; s < N; s++) {
        float t = (float(s) + 0.5) / float(N) * len;
        float hh = sqrt(r * r + t * t + 2.0 * r * mu * t) - RE;
        tr += exp(-hh / HR); tm += exp(-hh / HM) * uHaze; to += max(0.0, 1.0 - abs(hh - 25.0) / 15.0);
    }
    float dt = len / float(N);
    o = vec4(exp(-(BR * tr + BME * tm + BO * to) * dt), 1.0);
}
