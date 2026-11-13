// Open ocean: nothing but the sea and the sky. Everything here is water, drawn and moved every frame (scene.frag).

const float LAND_STEP = 1.0;
const float LAND_TOP = 1.0;

float landWater() { return 0.0; }
float landObj(vec3 p) { return 1.0e6; }
float landH(vec2 p, float t) { return -400.0; }
void landMat(vec3 p, inout vec3 n, float t, bool isObj, out vec3 alb, out float kind, out float open) {
    alb = vec3(1.0); kind = K_WATER; open = 1.0;
}
