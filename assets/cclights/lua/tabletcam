local args = {...}
local gpu=peripheral.wrap(args[1] or "left")
while true do
 local event,imagedata = os.pullEvent()
 if event == "tablet_image" then
  local textureID = gpu.import(imagedata)
  gpu.setColor(255,255,255)
  gpu.drawTexture(textureID,0,0)
  print("Drew tabletimage from textureID:"..textureID)
  gpu.freeTexture(textureID)
 end
end

