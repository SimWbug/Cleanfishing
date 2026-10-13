"""
Génère les données de police à partir de la police vanilla de Minecraft :
  - pack-src/vanilla_font.json : fournisseurs bitmap vanilla (fichier, hauteur, ascent, caractères)
  - src/main/resources/font-widths.txt : largeur (avance en pixels) de chaque caractère, utilisée par le plugin
    pour couper le texte de la bulle de dialogue.
Télécharge les fichiers vanilla depuis github.com/misode/mcmeta (à relancer seulement si Mojang change sa police).
Usage : python pack-src/make_font_data.py [version]   (par défaut 26.3)
"""
import io
import json
import os
import sys
import urllib.request

from PIL import Image

VERSION = sys.argv[1] if len(sys.argv) > 1 else "26.3"
BASE = f"https://raw.githubusercontent.com/misode/mcmeta/{VERSION}-assets/assets/minecraft/"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def fetch(path):
    cache = os.path.join(HERE, ".cache", path.replace("/", "_"))
    if os.path.exists(cache):
        return open(cache, "rb").read()
    os.makedirs(os.path.dirname(cache), exist_ok=True)
    data = urllib.request.urlopen(BASE + path, timeout=30).read()
    open(cache, "wb").write(data)
    return data


def main():
    providers = json.loads(fetch("font/include/default.json"))["providers"]
    bitmaps = [p for p in providers if p["type"] == "bitmap"]
    widths = {}
    for p in bitmaps:
        file = p["file"].split(":")[-1]
        img = Image.open(io.BytesIO(fetch("textures/" + file))).convert("RGBA")
        rows = p["chars"]
        cell_w = img.width // len(rows[0])
        cell_h = img.height // len(rows)
        height = p.get("height", 8)
        scale = height / cell_h
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch in widths or ch == "\u0000":
                    continue
                actual = 0
                for x in range(cell_w - 1, -1, -1):
                    if any(img.getpixel((c * cell_w + x, r * cell_h + y))[3] for y in range(cell_h)):
                        actual = x + 1
                        break
                widths[ch] = int(0.5 + actual * scale) + 1
    widths[" "] = 4
    with open(os.path.join(HERE, "vanilla_font.json"), "w", encoding="utf-8") as f:
        json.dump([{k: p[k] for k in ("file", "ascent", "chars") if k in p} | {"height": p.get("height", 8)} for p in bitmaps],
                  f, ensure_ascii=False, indent=1)
    out = os.path.join(ROOT, "src", "main", "resources", "font-widths.txt")
    with open(out, "w", encoding="utf-8") as f:
        f.write("# codepoint largeur (généré par pack-src/make_font_data.py)\n")
        for ch in sorted(widths):
            f.write(f"{ord(ch)} {widths[ch]}\n")
    print(len(widths), "caractères ->", out)


if __name__ == "__main__":
    main()
