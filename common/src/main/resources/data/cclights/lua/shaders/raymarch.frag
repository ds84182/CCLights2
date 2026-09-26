// Sphere-tracing (ray marching) of a bouncing sphere and a torus over a checkered floor,
// with Lambert + Blinn-Phong shading and a soft shadow.
#define STEPS 64
#define FAR 20.0

float sdSphere(vec3 p, float r) { return length(p) - r; }

float sdTorus(vec3 p, vec2 t)
{
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

mat2 rot(float a)
{
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

// Distance plus which object was hit, carried around as a struct.
struct Hit { float d; int id; };

Hit closer(Hit a, Hit b) { return a.d < b.d ? a : b; }

Hit scene(vec3 p)
{
    Hit floorH = Hit(p.y + 1.0, 0);
    vec3 sp = p - vec3(-1.2, abs(sin(iTime * 2.0)) * 0.8 - 0.3, 0.0);
    Hit sphere = Hit(sdSphere(sp, 0.7), 1);
    vec3 tp = p - vec3(1.3, 0.0, 0.0);
    tp.xy = rot(iTime) * tp.xy;
    tp.yz = rot(iTime * 0.7) * tp.yz;
    Hit torus = Hit(sdTorus(tp, vec2(0.7, 0.25)), 2);
    return closer(floorH, closer(sphere, torus));
}

float map(vec3 p) { return scene(p).d; }

// Overloads: the material colour of an object, or of a hit point on the floor.
vec3 material(int id) { return id == 1 ? vec3(0.9, 0.35, 0.25) : vec3(0.3, 0.6, 0.95); }
vec3 material(vec3 p)
{
    float checker = mod(floor(p.x) + floor(p.z), 2.0);
    return mix(vec3(0.25), vec3(0.85), checker);
}

vec3 normalAt(vec3 p)
{
    vec2 e = vec2(0.001, 0.0);
    return normalize(vec3(map(p + e.xyy) - map(p - e.xyy),
                          map(p + e.yxy) - map(p - e.yxy),
                          map(p + e.yyx) - map(p - e.yyx)));
}

float shadow(vec3 ro, vec3 rd)
{
    float res = 1.0;
    float t = 0.05;
    for (int i = 0; i < 24; i++) {
        float h = map(ro + rd * t);
        res = min(res, 8.0 * h / t);
        t += clamp(h, 0.02, 0.5);
        if (h < 0.001 || t > 10.0) break;
    }
    return clamp(res, 0.0, 1.0);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 uv = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    vec3 ro = vec3(0.0, 0.8, -4.5);
    vec3 rd = normalize(vec3(uv, 1.4));

    float t = 0.0;
    Hit h = Hit(FAR, -1);
    for (int i = 0; i < STEPS; i++) {
        h = scene(ro + rd * t);
        if (h.d < 0.002 || t > FAR) break;
        t += h.d;
    }

    vec3 col = vec3(0.55, 0.75, 1.0) - rd.y * 0.4; // sky
    if (t < FAR) {
        vec3 p = ro + rd * t;
        vec3 n = normalAt(p);
        vec3 light = normalize(vec3(0.6, 0.9, -0.4));
        vec3 albedo = h.id == 0 ? material(p) : material(h.id);
        float diff = max(dot(n, light), 0.0) * shadow(p + n * 0.02, light);
        vec3 hv = normalize(light - rd);
        float spec = pow(max(dot(n, hv), 0.0), 32.0) * 0.5;
        col = albedo * (0.15 + 0.85 * diff) + spec;
        col = mix(col, vec3(0.55, 0.75, 1.0), 1.0 - exp(-0.01 * t * t)); // fog
    }
    fragColor = vec4(pow(col, vec3(0.4545)), 1.0);
}
