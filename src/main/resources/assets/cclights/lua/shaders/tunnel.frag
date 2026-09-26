// The demoscene tunnel: polar coordinates turned into a texture lookup.
// Bind any texture to iChannel0 (gpu.setUniform(id, "iChannel0", tex)); a procedural pattern is used otherwise.
uniform float useTexture;

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 p = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float r = length(p);
    float a = atan(p.y, p.x);
    vec2 uv = vec2(0.3 / r + iTime * 0.5, a / 3.14159265 + sin(iTime * 0.2));
    vec3 col;
    if (useTexture > 0.5) {
        col = texture2D(iChannel0, uv).rgb;
    } else {
        vec2 g = fract(uv * vec2(4.0, 8.0));
        float lines = step(0.9, g.x) + step(0.9, g.y);
        col = mix(vec3(0.1, 0.2, 0.6), vec3(1.0, 0.9, 0.5), clamp(lines, 0.0, 1.0));
    }
    col *= r * 2.5;              // darken towards the centre
    fragColor = vec4(col, 1.0);
}
