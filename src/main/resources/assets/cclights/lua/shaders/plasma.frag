// Old-school demoscene plasma: a sum of sines mapped through a palette.
#define PI 3.14159265

vec3 palette(float t)
{
    return 0.5 + 0.5 * cos(2.0 * PI * (t + vec3(0.0, 0.33, 0.67)));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 uv = fragCoord / iResolution.xy;
    vec2 p = (uv - 0.5) * vec2(iResolution.x / iResolution.y, 1.0) * 4.0;
    float t = iTime * 0.7;
    float v = sin(p.x + t);
    v += sin((p.y + t) * 0.5);
    v += sin((p.x + p.y + t) * 0.5);
    vec2 c = p + vec2(sin(t * 0.33), cos(t * 0.5)) * 2.0;
    v += sin(length(c) + t);
    v *= 0.5;
    fragColor = vec4(palette(v * 0.5 + t * 0.05), 1.0);
}
