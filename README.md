# CCLights 2

CCLights 2 adds pixel-addressable graphics to [CC: Tweaked](https://tweaked.cc/): a **GPU** peripheral that
executes 2D draw commands, **monitors** and multi-block **external monitors** that show the result, and a wireless
**tablet** that mirrors a **tablet transceiver** and can send camera images back to the computer.

## Versions and installation

| Minecraft | Loader | Requires |
| --- | --- | --- |
| 1.20.1 | Forge 47.x | CC: Tweaked 1.120.2 (Forge build) |
| 1.20.1 | Fabric Loader + Fabric API | CC: Tweaked 1.120.2 (Fabric build) |

Put the CCLights2 jar for your loader and the matching CC: Tweaked jar into `mods/`. The same jar is needed on the
server and on every client.

A legacy version for Minecraft 1.7.10 (Forge, ComputerCraft 1.7x) exists in the history of this repository.

## Building

```bash
./gradlew build
```

Run Gradle with a JDK 21 (Architectury Loom 1.17 requires it; the mod itself is compiled for Java 17). The
project has a loader-independent `common` module (blocks, the GPU, the shader interpreter, resources and Lua
programs) and one module per loader; each loader module produces its own jar under `<loader>/build/libs/`. The
per-loader `runClient` tasks start a dev client with CC: Tweaked on the classpath.

The Lua programs and shaders live in `common/src/main/resources/data/cclights/lua/`: CC: Tweaked mounts them from
the server's data packs (`ComputerCraftAPI.createResourceMount(server, "cclights", "lua")`), so a data pack can
override or add files under `data/cclights/lua/`.

## Blocks and items

| Block / item | What it does |
| --- | --- |
| GPU | Peripheral type `GPU`. Draws on every monitor touching it. Right click with Graphics RAM to add texture memory. |
| Monitor | A 256x144 (configurable) screen you look at up close: right click the block to open it full screen with mouse and keyboard, like a computer terminal. It does not show its picture on the block. |
| External Monitor | The in-world display. Place several next to each other facing the same way to build a wall of up to 16x9 blocks. Each block adds 64 pixels (configurable), so a 3x2 wall is 192x128 pixels; `gpu.getMonitor().setScale(n)` divides that by n for bigger, more readable pixels. Right click the screen to send a click. |
| Tablet Transceiver | A 512x288 screen that paired tablets show wirelessly within `tabletRange` blocks. |
| Tablet | Right click a transceiver to pair. Right click to open the screen. Sneak + right click sends a camera image (`tablet_image` event). |
| Graphics RAM | Four items: `Graphics RAM (1K)`, `(2K)`, `(4K)` and `(8K)` (`cclights:ram_1k`, `ram_2k`, `ram_4k`, `ram_8k`). The base recipe makes eight 1K sticks; two sticks of the same size combine in a crafting grid into one of the next size. Right click a GPU to install. |

Programs shipped on every computer next to a GPU under `/cclights2`: `tutorial` (interactive, one chapter per
feature), `gpudemo` (shows every feature), `shaderdemo`, `tabletcam` (shows tablet photos), `tabletdemo` (a paint
program driven from a tablet: taps, drags, keyboard, wheel and photos) and the `canvas` helper library.

## GPU API

All coordinates are pixels with the origin in the top-left corner. Colours are 0-255 `r, g, b[, a]`.
Texture 0 is always the current monitor. Wrap the peripheral with `peripheral.find("GPU")`.

### State

| Method | Description |
| --- | --- |
| `setColor(r, g, b[, a])` / `getColor()` | Drawing colour; also tints textures and text. |
| `setLineWidth(w)` / `getLineWidth()` | Stroke width for outlines, lines and curves. |
| `setBlendMode(mode)` / `getBlendMode()` | `normal`, `replace`, `add`, `subtract`, `multiply`, `screen`, `difference`, `lighten`, `darken`, `xor`. |
| `setAntialias(bool)` / `getAntialias()` | Smooth edges and bilinear texture scaling. |
| `setClip(x, y, w, h)` / `resetClip()` / `getClip()` | Restrict drawing to a rectangle. |
| `translate(x, y)`, `rotate(rad)`, `rotateAround(rad, x, y)`, `scale(sx[, sy])`, `shear(shx, shy)` | Modify the current transform. |
| `push()` / `pop()` / `origin()` | Save / restore / reset the transform. |
| `reset()` | Free every texture and shader and reset colour, transform and clip. Run it (or `gpudemo`) when a terminated program left the GPU out of memory. |

### Drawing

| Method | Description |
| --- | --- |
| `fill()` | Fill the bound texture with the colour (ignores transform and clip). |
| `clearRectangle(x, y, w, h)` | Set a rectangle to exactly the colour without blending. |
| `plot(x, y)`, `line(x1, y1, x2, y2)` | |
| `rectangle` / `filledRectangle(x, y, w, h)` | |
| `roundRectangle` / `filledRoundRectangle(x, y, w, h[, arcW[, arcH]])` | |
| `oval` / `filledOval(x, y, w, h)` | Bounding box, like rectangle. |
| `arc` / `filledArc(x, y, w, h, startDeg, extentDeg)` | |
| `triangle` / `filledTriangle(x1, y1, x2, y2, x3, y3)` | |
| `polygon` / `filledPolygon(x1, y1, x2, y2, ...)` or `(table)` | |
| `curve(x1, y1, cx, cy, x2, y2)` | Quadratic curve. |
| `bezier(x1, y1, c1x, c1y, c2x, c2y, x2, y2)` | Cubic curve. |
| `gradientRectangle(x, y, w, h, r2, g2, b2[, a2[, vertical]])` | Gradient from the current colour to the second. |
| `drawText(text, x, y)` | Minecraft bitmap font, 8 px high, scaled by the transform. |
| `getTextWidth(text)`, `getTextHeight()` | |
| `setPixels(w, h, x, y, {r, g, b, a, ...})` | Raw pixel write, row major. Three values per pixel are accepted too. |
| `getPixels(x, y)` -> `r, g, b, a` / `getPixels(x, y, w, h)` -> table | Read pixels of the bound texture. |

### Textures

| Method | Description |
| --- | --- |
| `createTexture(w, h)` -> id | Allocate a texture (costs `w*h/32` memory units). |
| `bindTexture(id)` / `getBindedTexture()` | Choose what the drawing methods draw on. |
| `freeTexture(id)` | |
| `drawTexture(id, x, y[, tx, ty, w, h])` | Draw a texture or a sub-rectangle of it, tinted by the colour. |
| `drawTextureScaled(id, x, y, w, h)` | Draw stretched. |
| `copyTexture(id)` -> id | Duplicate a texture (0 copies the screen). |
| `resizeTexture(id, w, h[, smooth])` | Rescale in place. |
| `flipVertically(id)`, `flipHorizontally(id)` | |
| `blur(id[, radius])`, `gaussianBlur(id[, radius])`, `glow(id[, amount])`, `sharpen(id[, amount])`, `invert(id)`, `grayscale(id)` | Whole-texture filters. |
| `import(data)` -> id, w, h | Load a PNG/JPEG/GIF/BMP from a table of bytes, a binary string, or a file name in the computer's folder. |
| `export(id, "png"|"jpg"|"bmp"|"gif")` -> table of bytes | |
| `getSize([id])` -> w, h | |
| `listTextures()` -> table of ids | |
| `getFreeMemory()`, `getUsedMemory()`, `getTotalMemory()` | |
| `getMonitor()` -> object with `getResolution()`, `getType()`, `getPosition()` (and `getBlockResolution()`, `getDPM()` on external monitors) | |
| `startFrame()` / `endFrame()` | Batch commands so clients receive them at once. |

Old names `flipTextureV`, `clearRect` and `getPixelColor` still work.

**IDE autocompletion:** `docs/ide/cclights2.lua` is a Lua Language Server definition file for this whole API
(VS Code "Lua" extension, EmmyLua, Neovim). See `docs/ide/README.md` for the two-line setup.

Programs should read `gpu.getSize(0)` instead of assuming a size: a bigger monitor means more pixels, not a
magnified picture. To draw at a fixed design size on any screen, use the `canvas` helper that ships with the mod
(the tutorial and `gpudemo` use it):

```lua
local canvas = dofile("/cclights2/canvas")
local c = canvas.fit(gpu, 256, 144)        -- letterboxed; pass "stretch" as a 4th argument to fill the screen
gpu.drawText("hello", 10, 10)              -- canvas coordinates, scaled to the real screen
local x, y = c.toCanvas(px, py)            -- convert monitor_down/move/up coordinates back
c.apply()                                  -- re-apply after gpu.origin()
```

Under the hood it is just `gpu.translate()` and `gpu.scale()`, so you can do the same by hand. Text is a bitmap
font and turns to mush when shrunk, so draw text with `c.text()` or in real pixels and word-wrap it to
`gpu.getSize(0)`, the way the tutorial does; scale the canvas for shapes and textures only.

### Events

Events carry the GPU's side as their last argument.

| Event | Arguments |
| --- | --- |
| `monitor_down`, `monitor_move`, `monitor_up` | `button, x, y, clickId` |
| `monitor_scroll` | `x, y, direction` |
| `key` | `keyCode, isRepeat` |
| `key_up` | `keyCode` |
| `char` | `character` |
| `tablet_image` | `bytes, playerName` (feed `bytes` to `import`) |

`monitor_down`, `monitor_move`, `monitor_up` and `monitor_scroll` are CCLights2's own events, raised by the GPU's
monitors, tablets and transceivers. They are not CC: Tweaked's `monitor_touch`, which only comes from CC: Tweaked's
own monitors.

Key codes in `key` and `key_up` are the ones CC: Tweaked uses, i.e. GLFW key codes. Compare them against the `keys`
API instead of numbers: `if key == keys.q then ... end`, `keys.backspace`, `keys.enter`, `keys.space`. Old
programs that compared against LWJGL 2 numbers (such as `14` for backspace or `16` for Q) need updating.

## GLSL shaders

The GPU runs fragment shaders written in a GLSL subset. There is no real OpenGL in the pipeline, so the shader
is compiled once to a small register program and interpreted per pixel, on the server and on every client
(deterministically, so all players see the same picture). Both entry styles work:

- Shadertoy style: `void mainImage(out vec4 fragColor, in vec2 fragCoord)` with the implicit uniforms
  `iResolution`, `iTime`, `iTimeDelta`, `iFrame`, `iMouse`, `iDate`, `iChannel0..3`, `iChannelResolution`.
- GLSL Sandbox / WebGL 1 style: `void main()` writing `gl_FragColor`, reading `gl_FragCoord`, with your own
  `uniform`s.

| Method | Description |
| --- | --- |
| `createShader(source)` -> id | Compile GLSL; compile errors are raised as Lua errors with the line number. |
| `setUniform(id, name, v1[, v2, v3, v4])` or `(id, name, table)` | Set a numeric uniform. For a `sampler2D`, pass a texture id. `iResolution`, `iTime`, `iFrame` and friends are filled in automatically unless you set them. |
| `runShader(id[, x, y, w, h])` | Evaluate the shader for every pixel of the region (default: the whole bound texture) and draw the result with the current blend mode and clip. |
| `getShaderUniforms(id)` -> table name -> type | |
| `listShaders()`, `freeShader(id)` | |

Supported language: `float int bool vec2-4 ivec bvec mat2-4 sampler2D`, `struct`s (including nested ones and
arrays of them), arrays, swizzles, all operators, `if`/`for`/`while`/`do`, functions with overloading (inlined;
no recursion), `#define` constants, and the usual built-ins
(`sin cos tan atan pow exp log sqrt abs sign floor ceil fract mod min max clamp mix step smoothstep length
distance dot cross normalize reflect refract texture2D/texture matrixCompMult transpose` and the vector
comparison functions). Not supported: `switch`, bitwise operators, derivatives (`dFdx` and `fwidth` return 0).

Shaders shipped in `/cclights2/shaders` (run `shaderdemo` to cycle through them): the Shadertoy starter
gradient, a plasma, Mandelbrot and Julia sets, a ray-marched scene, the tunnel effect and a `main()`-style
example. The interpreter costs roughly 10 ns per instruction per pixel, so render heavy shaders to a small
texture and `drawTextureScaled` it up, as the demo does. `shaderMaxOpsPerPixel` and `shaderMaxPixels` in the
config bound the work a single call may do.

## Configuration

A JSON config file in the `config/` folder: monitor size, external monitor limits, pixels per block, tablet range,
GPU memory, shader limits, whether screen contents are saved with the world, debug logging.

Crafting recipes are data-pack JSON (`data/cclights/recipes/`) and use vanilla ingredients only, so they work
the same on Forge and Fabric; a data pack can override them.
