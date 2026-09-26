// Mandelbrot set with a slow zoom towards the seahorse valley and smooth colouring.
const int MAX_ITER = 60;

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 uv = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float zoom = 1.6 * exp(-0.35 * mod(iTime, 24.0));
    vec2 c = vec2(-0.745, 0.186) + uv * zoom;
    vec2 z = vec2(0.0);
    int escaped = MAX_ITER;
    for (int i = 0; i < MAX_ITER; i++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 16.0) {
            escaped = i;
            break;
        }
    }
    if (escaped == MAX_ITER) {
        fragColor = vec4(0.0, 0.0, 0.05, 1.0);
        return;
    }
    // smooth iteration count
    float n = float(escaped) + 1.0 - log2(log2(dot(z, z))) * 0.5;
    float t = n / float(MAX_ITER);
    vec3 col = 0.5 + 0.5 * cos(3.0 + t * 12.0 + vec3(0.0, 0.6, 1.2));
    fragColor = vec4(col, 1.0);
}
