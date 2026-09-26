---@meta

--[[
  CCLights2 API definitions for the Lua Language Server (VS Code "Lua" by sumneko, IntelliJ EmmyLua,
  Neovim lua_ls). Put this file in a folder listed under "workspace.library" (see docs/ide/README.md)
  and annotate your peripheral once:

      ---@type CCLights2.GPU
      local gpu = peripheral.find("GPU")

  Everything after that autocompletes: methods, parameters, return values and events.
  Coordinates are pixels from the top-left corner; colours are 0-255. Texture 0 is the current monitor.
]]

---@alias CCLights2.BlendMode
---| "normal"
---| "replace"
---| "add"
---| "subtract"
---| "multiply"
---| "screen"
---| "difference"
---| "lighten"
---| "darken"
---| "xor"

---@alias CCLights2.ImageFormat
---| "png"
---| "jpg"
---| "bmp"
---| "gif"

---Screen object returned by `gpu.getMonitor()`: a Monitor block or a Tablet Transceiver.
---@class CCLights2.Screen
local Screen = {}

---Screen size in pixels.
---@return integer width, integer height
function Screen.getResolution() end

---Same as `getResolution()`.
---@return integer width, integer height
function Screen.getSize() end

---Peripheral type of the screen: "Monitor", "ExternalMonitor" or "TabletTransceiver".
---@return string type
function Screen.getType() end

---World position of the screen block (the origin block for a wall).
---@return integer x, integer y, integer z
function Screen.getPosition() end

---Screen object of an External Monitor wall (returned by `gpu.getMonitor()` when the GPU touches one).
---@class CCLights2.ExternalMonitor : CCLights2.Screen
local ExternalMonitor = {}

---Pixels per block at the current scale (the configured pixels per block divided by the scale).
---@return integer dpm
function ExternalMonitor.getDPM() end

---Wall size in blocks.
---@return integer widthBlocks, integer heightBlocks
function ExternalMonitor.getBlockResolution() end

---Magnifies the pixels: the wall gets `pixelsPerBlock / scale` pixels per block (1..8).
---Existing pixels are kept where they fit. Returns true when the scale changed.
---@param scale integer
---@return boolean changed
function ExternalMonitor.setScale(scale) end

---Current pixel scale (1..8).
---@return integer scale
function ExternalMonitor.getScale() end

---The Tablet Transceiver peripheral (`peripheral.find("TabletTransceiver")`).
---@class CCLights2.TabletTransceiver
local TabletTransceiver = {}

---Screen size in pixels (512x288 by default).
---@return integer width, integer height
function TabletTransceiver.getResolution() end

---How many tablets are paired with this transceiver.
---@return integer count
function TabletTransceiver.getNumberOfTablets() end

---UUID of the paired tablet number `index` (1-based).
---@param index integer
---@return string uuid
function TabletTransceiver.getTabletUUID(index) end

---Unpairs every tablet.
function TabletTransceiver.disconnect() end

---The GPU peripheral (`peripheral.find("GPU")`).
---@class CCLights2.GPU
local GPU = {}

-- ------------------------------------------------------------------ state

---Drawing colour; also tints textures and text. Alpha defaults to 255.
---@param r integer
---@param g integer
---@param b integer
---@param a? integer
function GPU.setColor(r, g, b, a) end

---@return integer r, integer g, integer b, integer a
function GPU.getColor() end

---Stroke width for outlines, lines and curves.
---@param width number
function GPU.setLineWidth(width) end

---@return number width
function GPU.getLineWidth() end

---How new pixels combine with existing ones.
---@param mode CCLights2.BlendMode
function GPU.setBlendMode(mode) end

---@return CCLights2.BlendMode mode
function GPU.getBlendMode() end

---Smooth edges and bilinear texture scaling (default false).
---@param enabled? boolean
function GPU.setAntialias(enabled) end

---@return boolean enabled
function GPU.getAntialias() end

---Restricts drawing to a rectangle of the bound texture.
---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.setClip(x, y, w, h) end

---Removes the clip rectangle.
function GPU.resetClip() end

---The clip rectangle, or nothing when there is none.
---@return integer? x, integer? y, integer? w, integer? h
function GPU.getClip() end

---Moves the origin of the current transform.
---@param x number
---@param y number
function GPU.translate(x, y) end

---Rotates the current transform (radians).
---@param radians number
function GPU.rotate(radians) end

---Rotates the current transform around a point (radians).
---@param radians number
---@param x number
---@param y number
function GPU.rotateAround(radians, x, y) end

---Scales the current transform; `sy` defaults to `sx`.
---@param sx number
---@param sy? number
function GPU.scale(sx, sy) end

---Shears the current transform.
---@param shx number
---@param shy number
function GPU.shear(shx, shy) end

---Saves the current transform on a stack.
function GPU.push() end

---Restores the last pushed transform.
function GPU.pop() end

---Resets the transform to identity (and empties the stack).
function GPU.origin() end

---Frees every texture and shader and resets colour, transform and clip.
---Call it at the start of a program: a program killed with Ctrl+T leaves its textures behind.
function GPU.reset() end

-- ------------------------------------------------------------------ drawing

---Fills the bound texture with the colour (ignores transform and clip).
function GPU.fill() end

---Sets a rectangle to exactly the colour, without blending.
---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.clearRectangle(x, y, w, h) end

---Old name of `clearRectangle`.
---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.clearRect(x, y, w, h) end

---Sets one pixel.
---@param x integer
---@param y integer
function GPU.plot(x, y) end

---@param x1 integer
---@param y1 integer
---@param x2 integer
---@param y2 integer
function GPU.line(x1, y1, x2, y2) end

---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.rectangle(x, y, w, h) end

---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.filledRectangle(x, y, w, h) end

---Rectangle with rounded corners; `arcH` defaults to `arcW` (default 4).
---@param x integer
---@param y integer
---@param w integer
---@param h integer
---@param arcW? integer
---@param arcH? integer
function GPU.roundRectangle(x, y, w, h, arcW, arcH) end

---@param x integer
---@param y integer
---@param w integer
---@param h integer
---@param arcW? integer
---@param arcH? integer
function GPU.filledRoundRectangle(x, y, w, h, arcW, arcH) end

---Ellipse inside the bounding box.
---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.oval(x, y, w, h) end

---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.filledOval(x, y, w, h) end

---Arc of the ellipse inside the bounding box; angles in degrees, counter-clockwise from 3 o'clock.
---@param x integer
---@param y integer
---@param w integer
---@param h integer
---@param startDeg integer
---@param extentDeg integer
function GPU.arc(x, y, w, h, startDeg, extentDeg) end

---Pie slice of the ellipse inside the bounding box.
---@param x integer
---@param y integer
---@param w integer
---@param h integer
---@param startDeg integer
---@param extentDeg integer
function GPU.filledArc(x, y, w, h, startDeg, extentDeg) end

---@param x1 integer
---@param y1 integer
---@param x2 integer
---@param y2 integer
---@param x3 integer
---@param y3 integer
function GPU.triangle(x1, y1, x2, y2, x3, y3) end

---@param x1 integer
---@param y1 integer
---@param x2 integer
---@param y2 integer
---@param x3 integer
---@param y3 integer
function GPU.filledTriangle(x1, y1, x2, y2, x3, y3) end

---Outline through the points `x1, y1, x2, y2, ...` (at least three), or a flat table of them.
---@param ... integer|integer[]
function GPU.polygon(...) end

---Filled polygon through the points `x1, y1, x2, y2, ...` (at least three), or a flat table of them.
---@param ... integer|integer[]
function GPU.filledPolygon(...) end

---Quadratic curve from (x1, y1) to (x2, y2) bent towards the control point (cx, cy).
---@param x1 number
---@param y1 number
---@param cx number
---@param cy number
---@param x2 number
---@param y2 number
function GPU.curve(x1, y1, cx, cy, x2, y2) end

---Cubic curve from (x1, y1) to (x2, y2) with two control points.
---@param x1 number
---@param y1 number
---@param c1x number
---@param c1y number
---@param c2x number
---@param c2y number
---@param x2 number
---@param y2 number
function GPU.bezier(x1, y1, c1x, c1y, c2x, c2y, x2, y2) end

---Rectangle filled with a gradient from the current colour to `r2, g2, b2[, a2]`, left to right or top to bottom.
---@param x integer
---@param y integer
---@param w integer
---@param h integer
---@param r2 integer
---@param g2 integer
---@param b2 integer
---@param a2? integer
---@param vertical? boolean
function GPU.gradientRectangle(x, y, w, h, r2, g2, b2, a2, vertical) end

---Draws text with the Minecraft bitmap font (8 px high), tinted by the colour and scaled by the transform.
---@param text string
---@param x integer
---@param y integer
function GPU.drawText(text, x, y) end

---Width of `text` in pixels at scale 1.
---@param text string
---@return integer width
function GPU.getTextWidth(text) end

---Height of the font in pixels (8).
---@return integer height
function GPU.getTextHeight() end

---Writes raw pixels row by row into the bound texture: four (r, g, b, a) or three (r, g, b) values per pixel.
---@param w integer
---@param h integer
---@param x integer
---@param y integer
---@param pixels integer[]
function GPU.setPixels(w, h, x, y, pixels) end

---Reads one pixel (`r, g, b, a`) or, with `w, h`, a region as a flat table of `r, g, b, a` values.
---@param x integer
---@param y integer
---@param w? integer
---@param h? integer
---@return integer|integer[] rOrTable, integer? g, integer? b, integer? a
function GPU.getPixels(x, y, w, h) end

---Old name of `getPixels`.
---@param x integer
---@param y integer
---@param w? integer
---@param h? integer
---@return integer|integer[] rOrTable, integer? g, integer? b, integer? a
function GPU.getPixelColor(x, y, w, h) end

-- ------------------------------------------------------------------ textures

---Allocates a texture (costs `w * h / 32` memory units) and returns its id.
---@param w integer
---@param h integer
---@return integer id
function GPU.createTexture(w, h) end

---Chooses what the drawing methods draw on (0 = the current monitor).
---@param id integer
function GPU.bindTexture(id) end

---Id of the bound texture.
---@return integer id
function GPU.getBindedTexture() end

---@param id integer
function GPU.freeTexture(id) end

---Draws a texture, or the sub-rectangle `tx, ty, w, h` of it, tinted by the colour.
---@param id integer
---@param x integer
---@param y integer
---@param tx? integer
---@param ty? integer
---@param w? integer
---@param h? integer
function GPU.drawTexture(id, x, y, tx, ty, w, h) end

---Draws a texture stretched to `w x h`.
---@param id integer
---@param x integer
---@param y integer
---@param w integer
---@param h integer
function GPU.drawTextureScaled(id, x, y, w, h) end

---Duplicates a texture (0 copies the screen) and returns the new id.
---@param id integer
---@return integer newId
function GPU.copyTexture(id) end

---Rescales a texture in place.
---@param id integer
---@param w integer
---@param h integer
---@param smooth? boolean
function GPU.resizeTexture(id, w, h, smooth) end

---@param id integer
function GPU.flipVertically(id) end

---@param id integer
function GPU.flipHorizontally(id) end

---Old name of `flipVertically`.
---@param id integer
function GPU.flipTextureV(id) end

---Old name of `flipHorizontally`.
---@param id integer
function GPU.flipTextureH(id) end

---Box blur (default radius 2).
---@param id integer
---@param radius? integer
function GPU.blur(id, radius) end

---Gaussian blur (default radius 2).
---@param id integer
---@param radius? number
function GPU.gaussianBlur(id, radius) end

---Bloom-like glow (default amount 0.5).
---@param id integer
---@param amount? number
function GPU.glow(id, amount) end

---Unsharp-mask sharpening (default amount 0.5).
---@param id integer
---@param amount? number
function GPU.sharpen(id, amount) end

---@param id integer
function GPU.invert(id) end

---@param id integer
function GPU.grayscale(id) end

---Loads a PNG, JPEG, GIF or BMP from a table of bytes, a binary string, or a file name in the computer's folder.
---@param data integer[]|string
---@return integer id, integer width, integer height
function GPU.import(data) end

---Encodes a texture and returns the file as a table of bytes.
---@param id integer
---@param format CCLights2.ImageFormat
---@return integer[] bytes
function GPU.export(id, format) end

---Size of a texture (default: the bound one). `getSize(0)` is the screen size.
---@param id? integer
---@return integer width, integer height
function GPU.getSize(id) end

---Ids of every allocated texture.
---@return integer[] ids
function GPU.listTextures() end

---@return integer units
function GPU.getFreeMemory() end

---@return integer units
function GPU.getUsedMemory() end

---@return integer units
function GPU.getTotalMemory() end

---The screen this GPU currently draws to, or nil when none is connected.
---@return CCLights2.Screen|CCLights2.ExternalMonitor|nil screen
function GPU.getMonitor() end

---Starts batching: commands are sent to the players together at `endFrame()` (or after 5 s).
function GPU.startFrame() end

---Ends batching and sends the frame.
function GPU.endFrame() end

-- ------------------------------------------------------------------ shaders

---Compiles a GLSL fragment shader (Shadertoy `mainImage` or `main()` with `gl_FragColor`) and returns its id.
---Compile errors are raised as Lua errors with the line number.
---@param source string
---@return integer id
function GPU.createShader(source) end

---@param id integer
function GPU.freeShader(id) end

---Sets a uniform: up to four numbers, a table of numbers, or a texture id for a `sampler2D`.
---`iResolution`, `iTime`, `iFrame` and friends are filled in automatically unless set here.
---@param id integer
---@param name string
---@param ... number|number[]
function GPU.setUniform(id, name, ...) end

---Runs the shader for every pixel of the region (default: the whole bound texture) and draws the result
---with the current blend mode and clip.
---@param id integer
---@param x? integer
---@param y? integer
---@param w? integer
---@param h? integer
function GPU.runShader(id, x, y, w, h) end

---Uniform names and their GLSL types.
---@param id integer
---@return table<string, string> uniforms
function GPU.getShaderUniforms(id) end

---Ids of every compiled shader.
---@return integer[] ids
function GPU.listShaders() end

-- ------------------------------------------------------------------ events

---Events queued on computers next to a GPU (and next to a Tablet Transceiver). The last argument is
---always the peripheral side, like other ComputerCraft events:
---
---    local event, button, x, y, clickId, side = os.pullEvent("monitor_down")
---
---| Event | Arguments |
---|---|---|
---| `monitor_down`, `monitor_move`, `monitor_up` | `button, x, y, clickId` (screen pixels; button 0 = left) |
---| `monitor_scroll` | `x, y, direction` |
---| `key` | `keyCode, isRepeat` (GLFW codes: compare with `keys.*`) |
---| `key_up` | `keyCode` |
---| `char` | `character` |
---| `tablet_image` | `bytes, playerName` (feed `bytes` to `gpu.import`) |
---@alias CCLights2.Event
---| "monitor_down"
---| "monitor_move"
---| "monitor_up"
---| "monitor_scroll"
---| "key"
---| "key_up"
---| "char"
---| "tablet_image"

---The `canvas` helper shipped at `/cclights2/canvas` (`local canvas = dofile("/cclights2/canvas")`).
---The `canvas` helper shipped at `/cclights2/canvas` (`local canvas = dofile("/cclights2/canvas")`).
---@class CCLights2.CanvasLib
local CanvasLib = {}

---Scales a design size to the real screen: letterboxed ("fit", the default) or stretched ("stretch").
---@param gpu CCLights2.GPU
---@param w integer design width
---@param h integer design height
---@param mode? "fit"|"stretch"
---@return CCLights2.Canvas canvas
function CanvasLib.fit(gpu, w, h, mode) end

---@class CCLights2.Canvas
---@field gpu CCLights2.GPU
---@field w integer design width
---@field h integer design height
---@field realW integer real screen width in pixels
---@field realH integer real screen height in pixels
---@field sx number horizontal scale in use
---@field sy number vertical scale in use
---@field ox integer horizontal letterbox offset in real pixels
---@field oy integer vertical letterbox offset in real pixels
local Canvas = {}

---Re-reads the screen size and recomputes the scale (call after the wall changed).
function Canvas.refresh() end

---Applies the canvas transform to the GPU (call after `gpu.origin()` or whenever the transform was reset).
function Canvas.apply() end

---Converts real screen pixels (from `monitor_down`/`monitor_move`/`monitor_up`) to canvas coordinates.
---@param px number
---@param py number
---@return number x, number y
function Canvas.toCanvas(px, py) end

---Draws text at a canvas position but in real, unshrunk pixels (bitmap text must not be scaled down).
---@param str string
---@param x number
---@param y number
function Canvas.text(str, x, y) end

---Fills the whole real screen with the current colour.
function Canvas.clear() end
