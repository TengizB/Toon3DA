"""Contact sheet: python3 sheet.py SPRITE_DIR OUT.png [scale] -- every PNG in SPRITE_DIR side by side."""
import sys, glob, os
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
scale = int(sys.argv[3]) if len(sys.argv) > 3 else 3
ims = [Image.open(p) for p in sorted(glob.glob(os.path.join(src, "*.png")))]
w = sum(i.width * scale + 20 for i in ims) + 20
h = max(i.height for i in ims) * scale + 40
sheet = Image.new("RGBA", (w, h), (14, 18, 24, 255))
x = 20
for im in ims:
    big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
    sheet.alpha_composite(big, (x, h - 20 - big.height)); x += big.width + 20
sheet.save(out)
