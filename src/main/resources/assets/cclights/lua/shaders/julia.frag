// Julia set whose constant wanders over time. Click the screen to steer it with iMouse.
void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 uv = (fragCoord - 0.5 * iResolution.xy) / iResolution.y * 3.0;
    vec2 c = vec2(0.7885 * cos(iTime * 0.3), 0.7885 * sin(iTime * 0.3));
    if (iMouse.z > 0.0) {
        c = (iMouse.xy / iResolution.xy - 0.5) * 2.0;
    }
    vec2 z = uv;
    float n = 0.0;
    for (int i = 0; i < 48; i++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 4.0) break;
        n += 1.0;
    }
    float t = n / 48.0;
    vec3 col = mix(vec3(0.02, 0.0, 0.1), vec3(1.0, 0.8, 0.3), pow(t, 0.6));
    col = mix(col, vec3(0.2, 0.9, 1.0), smoothstep(0.5, 1.0, t));
    fragColor = vec4(col, 1.0);
}
