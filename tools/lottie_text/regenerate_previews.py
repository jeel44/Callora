"""Regenerates the outlined demo text in the permission-preview Lottie files.

The three app/src/main/assets/*_permission_preview.json files draw their text as shape paths
(no Lottie text layers, so no font is needed at runtime). This script rewrites only those paths,
using the current file as the template: every layer, transform, colour, keyframe and the frame
rate stay exactly as they are, and only the "sh" items inside the text groups listed in TEXTS are
replaced. Unlisted groups (and text that didn't change) are left byte-for-byte untouched.

Font: Liberation Sans 2.1.5 (SIL OFL), the font the original files were outlined from — sizes
were measured against it to within 0.02px. Download liberation-fonts-ttf-2.1.5.tar.gz from
https://github.com/liberationfonts/liberation-fonts/releases/tag/2.1.5 and extract it anywhere
outside the repo; the font files must not be committed.

Run (Python 3.9+, `pip install fonttools`), from the repo root:

    python tools/lottie_text/regenerate_previews.py --font-dir <dir with LiberationSans-*.ttf>

To change the demo text later, edit TEXTS below and run it again; it is safe to re-run.
"""

import argparse
import copy
import json
import os
import sys

from fontTools.pens.basePen import BasePen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont

ASSETS_DIR = os.path.join("app", "src", "main", "assets")

# file -> list of (layer name, group index within the layer, weight, size px, text, align).
# align "left": pen starts at the group origin (baseline-left), like every original text group.
# align "center-old": horizontally centred on where the replaced ink was (avatar initials).
TEXTS = {
    "calllog_permission_preview.json": [
        ("row1", 1, "Bold", 16, "+1 (555) 010-4821", "left"),
        ("row2", 1, "Bold", 16, "+44 7700 900123", "left"),
        ("row3", 1, "Bold", 16, "+1 (555) 010-7392", "left"),
    ],
    "contacts_permission_preview.json": [
        ("row1", 1, "Bold", 14, "JC", "center-old"),
        ("row1", 2, "Bold", 16, "James Carter", "left"),
        ("row1", 3, "Regular", 12, "+1 (555) 010-4821", "left"),
        ("row2", 1, "Bold", 14, "EW", "center-old"),
        ("row2", 2, "Bold", 16, "Emily Walker", "left"),
        ("row2", 3, "Regular", 12, "+44 7700 900456", "left"),
        ("row3", 1, "Bold", 14, "DM", "center-old"),
        ("row3", 2, "Bold", 16, "Daniel Moore", "left"),
        ("row3", 3, "Regular", 12, "+1 (555) 010-7392", "left"),
    ],
    "notification_permission_preview.json": [
        ("text5", 0, "Regular", 12, "Suspected spam - +44 7700 900123", "left"),
    ],
}


class LottiePathPen(BasePen):
    """Collects glyph contours as Lottie bezier paths (v/i/o with tangents relative to v)."""

    def __init__(self, glyph_set, transform):
        super().__init__(glyph_set)
        self.transform = transform
        self.paths = []
        self._nodes = None

    def _pt(self, p):
        return self.transform(p)

    def _moveTo(self, p):
        self._nodes = [[self._pt(p), (0.0, 0.0), (0.0, 0.0)]]  # [vertex, in, out]

    def _lineTo(self, p):
        self._nodes.append([self._pt(p), (0.0, 0.0), (0.0, 0.0)])

    def _curveToOne(self, c1, c2, p):
        prev = self._nodes[-1]
        c1, c2, p = self._pt(c1), self._pt(c2), self._pt(p)
        prev[2] = (c1[0] - prev[0][0], c1[1] - prev[0][1])
        self._nodes.append([p, (c2[0] - p[0], c2[1] - p[1]), (0.0, 0.0)])

    def _qCurveToOne(self, q, p):
        p0 = self._getCurrentPoint()
        c1 = (p0[0] + 2 / 3 * (q[0] - p0[0]), p0[1] + 2 / 3 * (q[1] - p0[1]))
        c2 = (p[0] + 2 / 3 * (q[0] - p[0]), p[1] + 2 / 3 * (q[1] - p[1]))
        self._curveToOne(c1, c2, p)

    def _closePath(self):
        nodes = self._nodes
        first, last = nodes[0], nodes[-1]
        if len(nodes) > 1 and _close(first[0], last[0]):
            first[1] = last[1]
            nodes.pop()
        r = lambda xy: [round(xy[0], 3), round(xy[1], 3)]
        self.paths.append({
            "ty": "sh",
            "ks": {"a": 0, "k": {
                "i": [r(n[1]) for n in nodes],
                "o": [r(n[2]) for n in nodes],
                "v": [r(n[0]) for n in nodes],
                "c": True,
            }},
        })
        self._nodes = None

    _endPath = _closePath


def _close(a, b):
    return abs(a[0] - b[0]) < 1e-6 and abs(a[1] - b[1]) < 1e-6


def text_ink_bounds(font, text, size):
    """Ink box (y down) of `text` set from x=0 on the baseline, unkerned, at `size` px."""
    glyph_set, cmap, scale = font.getGlyphSet(), font.getBestCmap(), size / font["head"].unitsPerEm
    x, box = 0.0, None
    for ch in text:
        name = cmap[ord(ch)]
        pen = BoundsPen(glyph_set)
        glyph_set[name].draw(pen)
        if pen.bounds:
            x0, y0, x1, y1 = pen.bounds
            b = (x + x0 * scale, -y1 * scale, x + x1 * scale, -y0 * scale)
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
        x += font["hmtx"][name][0] * scale
    return box


def outline_text(font, text, size, x_offset=0.0):
    """Lottie "sh" items for `text`, baseline at y=0, pen starting at x_offset. No kerning: the
    original files were set without it."""
    glyph_set, cmap, scale = font.getGlyphSet(), font.getBestCmap(), size / font["head"].unitsPerEm
    missing = [ch for ch in text if ord(ch) not in cmap]
    if missing:
        sys.exit(f"Font has no glyph for {missing!r} in {text!r}")
    paths, x = [], x_offset
    for ch in text:
        name = cmap[ord(ch)]
        pen = LottiePathPen(glyph_set, lambda p, x=x: (x + p[0] * scale, -p[1] * scale))
        glyph_set[name].draw(pen)
        paths.extend(pen.paths)
        x += font["hmtx"][name][0] * scale
    return paths


def path_ink_bounds(items):
    xs = [v[0] for it in items if it["ty"] == "sh" for v in it["ks"]["k"]["v"]]
    ys = [v[1] for it in items if it["ty"] == "sh" for v in it["ks"]["k"]["v"]]
    return (min(xs), min(ys), max(xs), max(ys))


def without_paths(doc):
    """The document with every text group's "sh" items removed — what must not change."""
    doc = copy.deepcopy(doc)
    for layer in doc["layers"]:
        for group in layer.get("shapes", []):
            if group.get("ty") == "gr":
                group["it"] = [it for it in group["it"] if it.get("ty") != "sh"]
    return doc


def regenerate(path, slots, fonts):
    with open(path, encoding="utf-8") as f:
        original = json.load(f)
    doc = copy.deepcopy(original)
    layers = {layer["nm"]: layer for layer in doc["layers"]}

    for layer_name, group_index, weight, size, text, align in slots:
        items = layers[layer_name]["shapes"][group_index]["it"]
        font = fonts[weight]
        x_offset = 0.0
        if align == "center-old":
            old = path_ink_bounds(items)
            new = text_ink_bounds(font, text, size)
            x_offset = (old[0] + old[2]) / 2 - (new[0] + new[2]) / 2
        first_sh = next(i for i, it in enumerate(items) if it["ty"] == "sh")
        rest = [it for it in items if it["ty"] != "sh"]
        items[:] = rest[:first_sh] + outline_text(font, text, size, x_offset) + rest[first_sh:]

    if json.dumps(without_paths(doc)) != json.dumps(without_paths(original)):
        sys.exit(f"{path}: something outside the text paths changed; refusing to write")

    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(doc))
    print(f"wrote {path} ({len(slots)} text groups)")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--font-dir", required=True, help="directory with LiberationSans-Regular.ttf and -Bold.ttf")
    parser.add_argument("--assets-dir", default=ASSETS_DIR)
    args = parser.parse_args()

    fonts = {w: TTFont(os.path.join(args.font_dir, f"LiberationSans-{w}.ttf")) for w in ("Regular", "Bold")}
    for name, slots in TEXTS.items():
        regenerate(os.path.join(args.assets_dir, name), slots, fonts)


if __name__ == "__main__":
    main()
