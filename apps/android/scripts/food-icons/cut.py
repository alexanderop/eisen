"""Cuts the food icons out of their sheets.

    python3 cut.py <folder with 0.png … 6.png> <res/drawable-nodpi>

A sheet is a square picture with six icons in three columns and two rows on one flat dark colour.
The sheets were drawn by an image model in one style ("soft matte clay, light from the top left")
and are not in the repository; the cut icons are. Needs Pillow.
"""
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFilter

# One row per sheet, in reading order. A name is the file `food_<name>.webp` and FoodIcon's file name.
SHEETS = [
    ["milk", "egg", "poultry", "rice", "banana", "nuts"],
    ["yogurt", "cheese", "butter", "oil", "tofu", "shake"],
    ["meat", "sausage", "fish", "beans", "bar", "plate"],
    ["bread", "pasta", "cereal", "potato", "pastry", "pizza"],
    ["fruit", "berries", "vegetable", "salad", "soup", "burger"],
    ["chocolate", "ice_cream", "chips", "spread", "sauce", "cookie"],
    ["water", "juice", "soda", "coffee", "beer", "meal"],
]
SIZE = 160      # the file's edge in pixels: 40 dp on the densest screens
INSIDE = 148    # the icon's longer edge inside it
NEAR = 26       # how far a pixel may be from the sheet's colour and still be background


def background_of(sheet: Image.Image) -> Image.Image:
    """White where [sheet] is its background: near the corner's colour in every channel, and
    reachable from the sheet's edge, so a dark part inside an icon (coffee, a chocolate chip) stays."""
    flat = Image.new("RGB", sheet.size, sheet.getpixel((6, 6)))
    r, g, b = ImageChops.difference(sheet, flat).split()
    far = ImageChops.lighter(ImageChops.lighter(r, g), b)
    solid = far.point(lambda v: 255 if v > NEAR else 0)
    w, h = solid.size
    for x in range(0, w, 16):
        for y in (0, h - 1):
            if solid.getpixel((x, y)) == 0:
                ImageDraw.floodfill(solid, (x, y), 128)
    for y in range(0, h, 16):
        for x in (0, w - 1):
            if solid.getpixel((x, y)) == 0:
                ImageDraw.floodfill(solid, (x, y), 128)
    return solid.point(lambda v: 255 if v == 128 else 0)


def pieces(alpha: Image.Image) -> list[tuple[int, int, int, int]]:
    """The box of every connected piece of [alpha] that is big enough to be part of an icon."""
    left = alpha.copy()
    boxes = []
    while (box := left.getbbox()) is not None:
        x0, y0, x1, _ = box
        x = next(x for x in range(x0, x1) if left.getpixel((x, y0)))
        before = left.copy()
        ImageDraw.floodfill(left, (x, y0), 0)
        piece = ImageChops.difference(before, left).getbbox()
        if (piece[2] - piece[0]) * (piece[3] - piece[1]) > 400:
            boxes.append(piece)
    return boxes


def main(sheets: str, out: str) -> None:
    for number, names in enumerate(SHEETS):
        sheet = Image.open(f"{sheets}/{number}.png").convert("RGB")
        w, h = sheet.size
        alpha = ImageChops.invert(background_of(sheet))
        # A piece belongs to the cell its middle is in: an icon may reach over a cell's edge.
        boxes: dict[int, list[tuple[int, int, int, int]]] = {}
        for piece in pieces(alpha):
            column = min(2, (piece[0] + piece[2]) // 2 * 3 // w)
            row = min(1, (piece[1] + piece[3]) // 2 * 2 // h)
            boxes.setdefault(row * 3 + column, []).append(piece)
        soft = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
        cut = sheet.convert("RGBA")
        cut.putalpha(soft)
        for index, name in enumerate(names):
            mine = boxes[index]
            box = (min(b[0] for b in mine), min(b[1] for b in mine), max(b[2] for b in mine), max(b[3] for b in mine))
            icon = cut.crop(box)
            scale = INSIDE / max(icon.size)
            icon = icon.resize((max(1, round(icon.width * scale)), max(1, round(icon.height * scale))), Image.LANCZOS)
            tile = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
            tile.paste(icon, ((SIZE - icon.width) // 2, (SIZE - icon.height) // 2), icon)
            tile.save(f"{out}/food_{name}.webp", "WEBP", quality=90, method=6)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
