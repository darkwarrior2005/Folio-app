import json, sys
import numpy as np
from PIL import Image
import potrace
from scipy import ndimage

SHEETS = {
    'A': ('sheet-2.webp', [9,163,328,498,668,834,1001,1169,1336,1486], [15,176,348,519,691,862,1033,1203,1372,1539,1702,1865,2000]),
    'B': ('sheet-1.webp', [10,162,332,497,666,834,1002,1170,1336,1485], [14,175,347,518,689,860,1032,1206,1374,1538,1703,1865,2000]),
}
UP = 3            # trace at 3x for smooth curves
THICKEN = 2.0     # original-pixel radius added around every line
UNITS_PER_PX = 20.0 / 118.0   # a typical 118px icon fills 20 of the 24 units

disk = lambda r: (lambda y, x: x * x + y * y <= r * r)(*np.ogrid[-r:r + 1, -r:r + 1])

def fmt(v):
    s = f"{v:.1f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

FILLED = {'A3.2','A3.3','A3.4','A3.5','A3.6','A3.7','A3.8','A4.1','A4.2','A4.6','B3.2','B9.1'}

def trace_cell(img, x0, x1, y0, y1, thicken=THICKEN):
    cell = img.crop((x0, y0, x1, y1)).convert('L')
    big = cell.resize((cell.width * UP, cell.height * UP), Image.LANCZOS)
    dark = np.array(big) < 140
    if not dark.any():
        return None
    if thicken > 0:
        dark = ndimage.binary_dilation(dark, structure=disk(int(thicken * UP)))
    ys, xs = np.nonzero(dark)
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    span = max(xs.max() - xs.min(), ys.max() - ys.min()) / UP
    s = UNITS_PER_PX / UP
    if span * UNITS_PER_PX > 22:              # never past the 24-unit box
        s = 22 / span / UP
    tx = lambda p: (fmt(12 + (p.x - cx) * s), fmt(12 + (p.y - cy) * s))
    bm = potrace.Bitmap(~dark)
    out = []
    for curve in bm.trace(turdsize=40 * UP, alphamax=1.0, opttolerance=0.3):
        x, y = tx(curve.start_point)
        d = [f"M{x} {y}"]
        for seg in curve.segments:
            if seg.is_corner:
                a = tx(seg.c); e = tx(seg.end_point)
                d.append(f"L{a[0]} {a[1]}L{e[0]} {e[1]}")
            else:
                a, b, e = tx(seg.c1), tx(seg.c2), tx(seg.end_point)
                d.append(f"C{a[0]} {a[1]} {b[0]} {b[1]} {e[0]} {e[1]}")
        d.append("Z")
        out.append(''.join(d))
    return ''.join(out)

result = {}
for key, (path, cols, rows) in SHEETS.items():
    img = Image.open(path)
    for r in range(12):
        for c in range(9):
            k = f"{key}{r+1}.{c+1}"
            d = trace_cell(img, cols[c], cols[c + 1], rows[r], rows[r + 1], 0.6 if k in FILLED else THICKEN)
            if d:
                result[f"{key}{r+1}.{c+1}"] = d
json.dump(result, open('traced.json', 'w'))
print(len(result), 'icons,', sum(len(v) for v in result.values()) // 1024, 'KB of path data')
