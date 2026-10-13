"""
CleanFishing - générateur du resource pack (interfaces de Gertrude et Francis).

Ce script dessine :
  - un portrait 3D (rendu ombré du skin Minecraft, vue de trois quarts) de Gertrude et de Francis ;
  - le fond de la boutique de Gertrude et celui de Francis, avec la bulle de dialogue ;
  - les polices du pack (fond d'interface + 3 lignes de texte décalées pour la bulle).

Tout est écrit dans le dossier ../pack (le resource pack lui-même), que le plugin embarque et envoie aux joueurs.

Personnaliser :
  1. Mets le vrai skin de Gertrude (fichier PNG 64x64, téléchargé sur namemc.com ou mineskin.org) dans
     pack-src/skins/gertrude.png, et celui de Francis dans pack-src/skins/francis.png.
     Sans fichier, un skin par défaut est dessiné (et enregistré dans pack-src/skins/ pour que tu puisses le retoucher).
  2. Relance :  python pack-src/build_pack.py
  3. Ou retouche directement les PNG produits (pack/assets/cleanfishing/textures/gui/*.png) dans ton logiciel
     de dessin. Garde les dimensions (176 x 222, la taille de l'interface) et l'emplacement des cases :
     les fichiers *_guide.png montrent où tombent les cases cliquables.

Nécessite Python 3 et Pillow (pip install pillow).
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PACK = os.path.join(ROOT, "pack")
GUI_DIR = os.path.join(PACK, "assets", "cleanfishing", "textures", "gui")
FONT_DIR = os.path.join(PACK, "assets", "cleanfishing", "font")
SKINS = os.path.join(HERE, "skins")

S = 1                       # 1 pixel d'image = 1 pixel d'interface (les polices limitent un glyphe à 256x256)
W, H = 176, 222             # interface de coffre 6 rangées (pixels d'interface)
PACK_FORMAT = 97            # Minecraft 26.3

# Géométrie partagée avec le plugin (gui/PackUi.java) : ne pas changer sans mettre le Java à jour.
PORTRAIT = (7, 17, 61, 89)          # x1, y1, x2, y2 (pixels d'interface)
BUBBLE = (64, 18, 169, 52)
BUBBLE_TEXT_X = (68, 166)           # zone du texte
BUBBLE_LINES_Y = (23, 32, 41)       # haut des 3 lignes de la bulle
BOARD = (64, 55, 169, 87)           # tableau d'infos de Francis
BOARD_LINES_Y = (59, 68, 77)        # haut des 3 lignes du tableau
# Police cleanfishing:lineN = texte vanilla dont le haut des lettres tombe à LINES_Y[N-1]
LINES_Y = BUBBLE_LINES_Y + BOARD_LINES_Y
SHIFTS = [y - 6 for y in LINES_Y]

random.seed(7)


# ---------------------------------------------------------------- outils


def slot_xy(slot):
    """Coin haut-gauche du cadre (18x18) d'un slot du coffre."""
    row, col = divmod(slot, 9)
    return 7 + col * 18, 17 + row * 18


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rect(d, box, fill=None, outline=None, width=1):
    x1, y1, x2, y2 = box
    d.rectangle([x1 * S, y1 * S, x2 * S - 1, y2 * S - 1], fill=fill, outline=outline, width=width * S if outline else 0)


# Petite police pixel 5x7 (majuscules) pour les étiquettes dessinées dans les fonds.
FONT5 = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "11110", "10001", "10001", "10001", "11110"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "11110", "10000", "10000", "10000", "11111"],
    "F": ["11111", "10000", "11110", "10000", "10000", "10000", "10000"],
    "G": ["01111", "10000", "10000", "10011", "10001", "10001", "01111"],
    "H": ["10001", "10001", "11111", "10001", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "J": ["00111", "00001", "00001", "00001", "10001", "10001", "01110"],
    "K": ["10001", "10010", "11100", "10010", "10001", "10001", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10001", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10010", "10001", "10001"],
    "S": ["01111", "10000", "01110", "00001", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "01010", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    "X": ["10001", "01010", "00100", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "01010", "00100", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00010", "00100", "01000", "10000", "10000", "11111"],
    "É": ["00110", "11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "È": ["01100", "11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "Ê": ["01010", "11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "0": ["01110", "10011", "10101", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00110", "01000", "10000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["01110", "10000", "11110", "10001", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00001", "01110"],
    ":": ["0", "1", "0", "0", "0", "1", "0"],
    "'": ["00100", "00100", "00000", "00000", "00000", "00000", "00000"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    " ": ["000", "000", "000", "000", "000", "000", "000"],
}


def text_width(text, px=1):
    return sum((len(FONT5.get(ch, FONT5["A"])[0]) + 1) * px for ch in text.upper()) - px


def draw_text(img, x, y, text, color, px=1, shadow=None):
    """Texte en police pixel. x, y en pixels d'IMAGE ; px = taille d'un pixel de police en pixels d'image."""
    d = ImageDraw.Draw(img)
    for pass_ in ((1, shadow),) if shadow else ():
        ox, col = pass_
        draw_text(img, x + ox * px, y + ox * px, text, col, px)
    cx = x
    for ch in text.upper():
        glyph = FONT5.get(ch, FONT5["A"])
        top = y - (len(glyph) - 7) * px
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([cx + gx * px, top + gy * px, cx + gx * px + px - 1, top + gy * px + px - 1], fill=color)
        cx += (len(glyph[0]) + 1) * px


# ---------------------------------------------------------------- skins par défaut


def paint(img, box, color):
    x1, y1, x2, y2 = box
    ImageDraw.Draw(img).rectangle([x1, y1, x2 - 1, y2 - 1], fill=color + (255,) if len(color) == 3 else color)


def noise_fill(img, box, color, amount=0.06):
    x1, y1, x2, y2 = box
    for y in range(y1, y2):
        for x in range(x1, x2):
            f = 1 + random.uniform(-amount, amount)
            img.putpixel((x, y), shade(color, f) + (255,))


def default_gertrude():
    """Tavernière : chignon brun, foulard rouge, chemisier crème, tablier brun."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = rgb("#e8b896")
    hair = rgb("#6b3f22")
    scarf = rgb("#b8322e")
    blouse = rgb("#efe3c8")
    apron = rgb("#7a4a2a")
    dress = rgb("#3c5a8a")
    # tête : base peau, cheveux dessus/derrière/côtés hauts
    for box in [(0, 8, 32, 16), (8, 0, 24, 8)]:
        noise_fill(img, box, skin, 0.03)
    noise_fill(img, (8, 0, 16, 8), hair)          # dessus
    noise_fill(img, (24, 8, 32, 16), hair)        # arrière
    for box in [(0, 8, 8, 11), (16, 8, 24, 11), (8, 8, 16, 10)]:
        noise_fill(img, box, hair)                # haut des côtés + front
    noise_fill(img, (0, 11, 2, 16), hair)         # mèches arrière côté droit
    noise_fill(img, (22, 11, 24, 16), hair)
    # visage (face avant x 8..16, y 8..16)
    def px(x, y, c):
        img.putpixel((x, y), c + (255,))
    for x in range(8, 16):
        px(x, 8, hair)                                       # frange
    for x in (8, 9, 14, 15):
        px(x, 9, hair)
    px(8, 10, hair); px(15, 10, hair)
    for y in range(11, 16):
        px(8, y, shade(hair, 0.9)); px(15, y, shade(hair, 0.9))   # cheveux longs encadrant le visage
    lash = rgb("#3a2414")
    px(9, 10, lash); px(10, 10, lash); px(13, 10, lash); px(14, 10, lash)   # cils / sourcils
    px(9, 11, rgb("#ffffff")); px(10, 11, rgb("#2f6db5"))
    px(13, 11, rgb("#2f6db5")); px(14, 11, rgb("#ffffff"))
    px(11, 12, shade(skin, 0.86)); px(12, 12, shade(skin, 0.86))           # nez
    px(9, 12, rgb("#f09a8c")); px(14, 12, rgb("#f09a8c"))                  # joues roses
    px(10, 13, rgb("#c25048")); px(13, 13, rgb("#c25048"))                 # sourire
    px(11, 14, rgb("#c25048")); px(12, 14, rgb("#c25048"))
    px(11, 13, rgb("#ffffff")); px(12, 13, rgb("#ffffff"))                 # dents
    # boucles d'oreilles (côtés)
    px(4, 13, rgb("#e8c040")); px(19, 13, rgb("#e8c040"))
    # couche chapeau : foulard noué + chignon à l'arrière
    noise_fill(img, (40, 0, 48, 8), scarf)                  # dessus
    for box in [(32, 8, 40, 10), (40, 8, 48, 9), (48, 8, 56, 10), (56, 8, 64, 10)]:
        noise_fill(img, box, scarf)
    for x in range(40, 48, 2):
        img.putpixel((x, 8), rgb("#f2e6c8") + (255,))       # pois du foulard
    noise_fill(img, (58, 10, 62, 14), shade(hair, 0.9))     # chignon (arrière)
    # corps : chemisier + tablier
    noise_fill(img, (16, 20, 40, 32), dress)
    noise_fill(img, (20, 16, 36, 20), blouse)
    noise_fill(img, (20, 20, 28, 26), blouse)               # buste devant
    noise_fill(img, (21, 23, 27, 32), apron)                # tablier
    noise_fill(img, (20, 26, 28, 27), shade(apron, 0.8))    # ceinture
    for x in (23, 24):
        img.putpixel((x, 21), rgb("#c99a6a") + (255,))      # décolleté
    # bras droit (manches retroussées)
    noise_fill(img, (40, 16, 56, 32), skin, 0.03)
    noise_fill(img, (40, 20, 56, 25), blouse)
    noise_fill(img, (44, 16, 48, 20), blouse)
    # bras gauche
    noise_fill(img, (32, 48, 48, 64), skin, 0.03)
    noise_fill(img, (32, 52, 48, 57), blouse)
    noise_fill(img, (36, 48, 40, 52), blouse)
    # jambes (non visibles sur le portrait)
    noise_fill(img, (0, 16, 16, 32), dress)
    noise_fill(img, (16, 48, 32, 64), dress)
    return img


def default_francis():
    """Vieux pêcheur : barbe grise, chapeau de pluie jaune, ciré jaune."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = rgb("#c98e68")
    beard = rgb("#b9b5ad")
    coat = rgb("#e8b52a")
    dark = rgb("#2d3a4a")
    noise_fill(img, (0, 8, 32, 16), skin, 0.04)
    noise_fill(img, (8, 0, 24, 8), beard)
    noise_fill(img, (24, 8, 32, 16), beard)
    # visage
    for (x, y), c in {(9, 11): rgb("#ffffff"), (10, 11): rgb("#2b4a6a"), (13, 11): rgb("#2b4a6a"), (14, 11): rgb("#ffffff")}.items():
        img.putpixel((x, y), c + (255,))
    for x in (9, 10, 13, 14):
        img.putpixel((x, 10), shade(beard, 0.75) + (255,))
    img.putpixel((11, 12), shade(skin, 0.8) + (255,))
    img.putpixel((12, 12), shade(skin, 0.8) + (255,))
    noise_fill(img, (8, 13, 16, 16), beard)                 # barbe
    noise_fill(img, (9, 12, 11, 13), beard)
    noise_fill(img, (13, 12, 15, 13), beard)
    img.putpixel((11, 13), shade(skin, 0.6) + (255,))      # bouche dans la barbe
    img.putpixel((12, 13), shade(skin, 0.6) + (255,))
    noise_fill(img, (0, 13, 8, 16), beard)
    noise_fill(img, (16, 13, 24, 16), beard)
    # chapeau de pluie (couche chapeau)
    noise_fill(img, (40, 0, 48, 8), coat)
    for box in [(32, 8, 40, 10), (40, 8, 48, 9), (48, 8, 56, 10), (56, 8, 64, 12)]:
        noise_fill(img, box, coat)
    # ciré
    noise_fill(img, (16, 16, 40, 32), coat)
    noise_fill(img, (23, 20, 25, 32), shade(coat, 0.8))     # fermeture
    for y in (22, 25, 28):
        img.putpixel((24, y), dark + (255,))                # boutons
    noise_fill(img, (20, 16, 28, 18), beard)                # col / barbe
    noise_fill(img, (40, 16, 56, 32), coat)
    noise_fill(img, (40, 30, 56, 32), skin)                 # mains
    noise_fill(img, (32, 48, 48, 64), coat)
    noise_fill(img, (32, 62, 48, 64), skin)
    noise_fill(img, (0, 16, 16, 32), dark)
    noise_fill(img, (16, 48, 32, 64), dark)
    return img


def load_skin(name, factory):
    os.makedirs(SKINS, exist_ok=True)
    path = os.path.join(SKINS, name + ".png")
    if not os.path.exists(path):
        factory().save(path)
        print("skin par défaut écrit :", path)
    img = Image.open(path).convert("RGBA")
    if img.size == (64, 32):  # ancien format : on recopie bras/jambe droits à gauche
        new = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        new.paste(img, (0, 0))
        new.paste(img.crop((40, 16, 56, 32)), (32, 48))
        new.paste(img.crop((0, 16, 16, 32)), (16, 48))
        img = new
    if img.size != (64, 64):
        raise SystemExit(f"{path} doit faire 64x64 pixels (skin Minecraft), pas {img.size}")
    return img


# ---------------------------------------------------------------- rendu 3D d'un buste


# Boîtes du modèle : (origine x, y, z, largeur, hauteur, profondeur, uv de base, uv de la couche externe, gonflement)
def boxes(slim=False):
    arm = 3 if slim else 4
    return [
        ("head", (-4, 24, -4), (8, 8, 8), (0, 0), (32, 0), 0.55),
        ("body", (-4, 12, -2), (8, 12, 4), (16, 16), (16, 32), 0.3),
        ("rarm", (-4 - arm, 12, -2), (arm, 12, 4), (40, 16), (40, 32), 0.3),
        ("larm", (4, 12, -2), (arm, 12, 4), (32, 48), (48, 48), 0.3),
    ]


def face_regions(u, v, w, h, d):
    """Régions de texture (x, y, largeur, hauteur) de chaque face d'une boîte, format skin standard."""
    return {
        "top": (u + d, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def quads_for_box(tex, origin, size, uv, inflate):
    ox, oy, oz = origin
    w, h, d = size
    x0, y0, z0 = ox - inflate, oy - inflate, oz - inflate
    x1, y1, z1 = ox + w + inflate, oy + h + inflate, oz + d + inflate
    sx, sy, sz = (x1 - x0) / w, (y1 - y0) / h, (z1 - z0) / d
    reg = face_regions(uv[0], uv[1], w, h, d)
    quads = []

    def add(color, pts, normal):
        if color[3] >= 128:
            quads.append((color, pts, normal))

    fx, fy, fw, fh = reg["front"]          # z = z1, u de x0 à x1, v du haut vers le bas
    for i in range(fw):
        for j in range(fh):
            c = tex.getpixel((fx + i, fy + j))
            xa, xb = x0 + i * sx, x0 + (i + 1) * sx
            ya, yb = y1 - j * sy, y1 - (j + 1) * sy
            add(c, [(xa, ya, z1), (xb, ya, z1), (xb, yb, z1), (xa, yb, z1)], (0, 0, 1))
    rx, ry, rw, rh = reg["right"]          # x = x0, u de z0 (arrière) à z1 (avant)
    for i in range(rw):
        for j in range(rh):
            c = tex.getpixel((rx + i, ry + j))
            za, zb = z0 + i * sz, z0 + (i + 1) * sz
            ya, yb = y1 - j * sy, y1 - (j + 1) * sy
            add(c, [(x0, ya, za), (x0, ya, zb), (x0, yb, zb), (x0, yb, za)], (-1, 0, 0))
    lx, ly, lw, lh = reg["left"]           # x = x1, u de z1 (avant) à z0 (arrière)
    for i in range(lw):
        for j in range(lh):
            c = tex.getpixel((lx + i, ly + j))
            za, zb = z1 - i * sz, z1 - (i + 1) * sz
            ya, yb = y1 - j * sy, y1 - (j + 1) * sy
            add(c, [(x1, ya, za), (x1, ya, zb), (x1, yb, zb), (x1, yb, za)], (1, 0, 0))
    tx, ty, tw, th = reg["top"]            # y = y1, u le long de x, v de z0 (arrière) à z1 (avant)
    for i in range(tw):
        for j in range(th):
            c = tex.getpixel((tx + i, ty + j))
            xa, xb = x0 + i * sx, x0 + (i + 1) * sx
            za, zb = z0 + j * sz, z0 + (j + 1) * sz
            add(c, [(xa, y1, za), (xb, y1, za), (xb, y1, zb), (xa, y1, zb)], (0, 1, 0))
    bx, by, bw, bh = reg["back"]           # z = z0, u de x1 à x0
    for i in range(bw):
        for j in range(bh):
            c = tex.getpixel((bx + i, by + j))
            xa, xb = x1 - i * sx, x1 - (i + 1) * sx
            ya, yb = y1 - j * sy, y1 - (j + 1) * sy
            add(c, [(xa, ya, z0), (xb, ya, z0), (xb, yb, z0), (xa, yb, z0)], (0, 0, -1))
    return quads


def render_bust(skin, width, height, yaw=34, pitch=13, head_tilt=-8):
    """Rendu ombré de la tête et du buste, vue de trois quarts (supersampling x4 puis réduction)."""
    ss = 4
    W2, H2 = width * ss, height * ss
    img = Image.new("RGBA", (W2, H2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    light = (-0.45, 0.75, 0.55)
    ln = math.sqrt(sum(v * v for v in light))
    light = tuple(v / ln for v in light)

    def rot(p, extra_yaw=0.0):
        x, y, z = p
        if extra_yaw:
            c, s = math.cos(math.radians(extra_yaw)), math.sin(math.radians(extra_yaw))
            # rotation de la tête autour de son cou (y = 24)
            x, z = x * c + z * s, -x * s + z * c
        x, z = x * cy_ + z * sy_, -x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    # bras fins (modèle « Alex ») : la dernière colonne du dos du bras droit est vide
    slim = skin.getpixel((54, 25))[3] == 0 and skin.getpixel((55, 25))[3] == 0
    quads = []
    for name, origin, size, uv, uv2, inflate in boxes(slim):
        extra = head_tilt if name == "head" else 0.0
        for layer, (u, inf) in enumerate(((uv, 0.0), (uv2, inflate))):
            for color, pts, normal in quads_for_box(skin, origin, size, u, inf):
                rp = [rot(p, extra) for p in pts]
                n = rot(normal, extra)
                if n[2] <= 0.01:
                    continue
                depth = sum(p[2] for p in rp) / 4 + layer * 0.01
                quads.append((depth, color, rp, n))
    quads.sort(key=lambda q: q[0])

    # cadrage : du haut du chapeau au milieu du torse
    top_y, bottom_y = 33.4, 14.0
    scale = (H2 * 0.96) / (top_y - bottom_y)
    cx, cy = W2 / 2 + W2 * 0.02, H2 * 0.02 + top_y * scale
    for depth, color, pts, n in quads:
        lum = 0.55 + 0.5 * max(0.0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
        c = shade(color, lum) + (255,)
        poly = [(cx + p[0] * scale, cy - p[1] * scale) for p in pts]
        d.polygon(poly, fill=c, outline=c)
    # léger contour sombre pour détacher le personnage du fond
    alpha = img.split()[3]
    outline = alpha.filter(ImageFilter.MaxFilter(5))
    shadow = Image.new("RGBA", img.size, (25, 15, 10, 0))
    shadow.putalpha(outline.point(lambda a: 170 if a else 0))
    shadow.alpha_composite(img)
    return shadow.resize((width, height), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=1, percent=80, threshold=2))


# ---------------------------------------------------------------- éléments d'interface


def wood(img, box, base, plank=12, vertical=True, grain=0.10):
    """Planches de bois avec veinure (box en pixels d'interface)."""
    x1, y1, x2, y2 = (v * S for v in box)
    px = img.load()
    for y in range(y1, y2):
        for x in range(x1, x2):
            k = ((x - x1) if vertical else (y - y1)) // (plank * S)
            seed = math.sin(k * 12.9898) * 43758.5453
            tone = 0.88 + (seed - math.floor(seed)) * 0.2
            along = (y if vertical else x)
            g = math.sin(along * 0.31 + k * 7 + math.sin(along * 0.05 + k) * 3) * grain
            f = tone + g + random.uniform(-0.03, 0.03)
            edge = ((x - x1) if vertical else (y - y1)) % (plank * S)
            if edge < S:
                f *= 0.62
            px[x, y] = shade(base, f) + (255,)


def bevel(img, box, light, dark, size=1):
    x1, y1, x2, y2 = (v * S for v in box)
    d = ImageDraw.Draw(img)
    for i in range(size * S):
        d.line([(x1 + i, y1 + i), (x2 - 1 - i, y1 + i)], fill=light)
        d.line([(x1 + i, y1 + i), (x1 + i, y2 - 1 - i)], fill=light)
        d.line([(x1 + i, y2 - 1 - i), (x2 - 1 - i, y2 - 1 - i)], fill=dark)
        d.line([(x2 - 1 - i, y1 + i), (x2 - 1 - i, y2 - 1 - i)], fill=dark)


def slot(img, slot_index=None, xy=None, style="dark"):
    """Case d'objet (18x18 pixels d'interface), creusée dans le décor."""
    x, y = xy if xy else slot_xy(slot_index)
    palette = {
        "dark": ((52, 38, 28), (24, 16, 11), (122, 96, 70)),
        "gold": ((70, 52, 20), (40, 28, 8), (232, 190, 80)),
        "sea": ((26, 44, 60), (12, 22, 32), (110, 160, 190)),
        "vanilla": ((139, 122, 102), (62, 46, 32), (236, 220, 196)),
        "tag": ((92, 70, 44), (46, 32, 18), (200, 170, 120)),
    }[style]
    inner, shadow_c, light_c = palette
    rect(ImageDraw.Draw(img), (x, y, x + 18, y + 18), fill=inner)
    d = ImageDraw.Draw(img)
    x1, y1, x2, y2 = x * S, y * S, (x + 18) * S - 1, (y + 18) * S - 1
    for i in range(S):
        d.line([(x1, y1 + i), (x2, y1 + i)], fill=shadow_c)
        d.line([(x1 + i, y1), (x1 + i, y2)], fill=shadow_c)
        d.line([(x1, y2 - i), (x2, y2 - i)], fill=light_c)
        d.line([(x2 - i, y1), (x2 - i, y2)], fill=light_c)


def round_corners(img, r=3):
    """Coins arrondis comme l'interface vanilla (transparent hors du cadre)."""
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius=r * S, fill=255)
    img.putalpha(Image.composite(img.split()[3], Image.new("L", img.size, 0), mask))


def frame_box(img, box, outer, inner_light, inner_dark, thickness=2):
    x1, y1, x2, y2 = box
    rect(ImageDraw.Draw(img), box, fill=outer)
    bevel(img, box, inner_light, inner_dark, 1)
    return (x1 + thickness, y1 + thickness, x2 - thickness, y2 - thickness)


def bubble(img, box, tail_to):
    """Bulle de dialogue (crème, contour sombre) avec sa pointe vers le portrait."""
    x1, y1, x2, y2 = (v * S for v in box)
    big = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    outline = (58, 40, 28, 255)
    fill = (250, 243, 226, 255)
    tx, ty = tail_to[0] * S, tail_to[1] * S
    tail = [(x1 + 2 * S, ty - 5 * S), (tx, ty), (x1 + 2 * S, ty + 3 * S)]
    d.polygon([(px, py) for px, py in tail], fill=outline)
    d.rounded_rectangle([x1, y1, x2 - 1, y2 - 1], radius=6 * S, fill=outline)
    d.rounded_rectangle([x1 + S, y1 + S, x2 - 1 - S, y2 - 1 - S], radius=5 * S, fill=fill)
    inner_tail = [(x1 + 3 * S, ty - 3 * S), (tx + 2 * S, ty), (x1 + 3 * S, ty + 1 * S)]
    d.polygon(inner_tail, fill=fill)
    # reflet en haut
    d.line([(x1 + 7 * S, y1 + 3 * S), (x2 - 8 * S, y1 + 3 * S)], fill=(255, 252, 244, 255), width=S)
    img.alpha_composite(big)


def player_inventory(img, label_y=128):
    """Inventaire du joueur (3 rangées + barre) sur un panneau de bois clair, cases comme l'interface vanilla."""
    panel = (4, label_y - 3, W - 4, H - 4)
    wood(img, panel, rgb("#b08a5e"), plank=60, vertical=False, grain=0.05)
    bevel(img, panel, (214, 186, 140, 255), (70, 46, 26, 255))
    draw_text(img, 8 * S, label_y * S, "INVENTAIRE", (70, 44, 24, 255), px=1)
    for row in range(3):
        for col in range(9):
            slot(img, xy=(7 + col * 18, 138 + row * 18), style="vanilla")
    for col in range(9):
        slot(img, xy=(7 + col * 18, 196), style="vanilla")


def portrait_panel(img, skin, backdrop, name, accent):
    """Cadre du portrait : décor peint derrière le personnage, rendu 3D, plaque du nom."""
    x1, y1, x2, y2 = PORTRAIT
    inner = frame_box(img, (x1, y1, x2, y2), (60, 38, 22), (186, 140, 82), (34, 20, 10), 2)
    ix1, iy1, ix2, iy2 = inner
    bw, bh = (ix2 - ix1) * S, (iy2 - iy1) * S
    bg = backdrop(bw, bh)
    bust = render_bust(skin, bw, bh)
    bg.alpha_composite(bust)
    # vignette
    vign = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    vd = ImageDraw.Draw(vign)
    for i in range(10):
        vd.rectangle([i, i, bw - 1 - i, bh - 1 - i], outline=(0, 0, 0, 90 - i * 9))
    bg.alpha_composite(vign)
    img.alpha_composite(bg, (ix1 * S, iy1 * S))
    if not name:
        return
    # plaque du nom
    pw = text_width(name, 1) + 6 * S
    px1 = (x1 + x2) * S // 2 - pw // 2
    py1 = (y2 - 10) * S
    d = ImageDraw.Draw(img)
    d.rectangle([px1, py1, px1 + pw - 1, py1 + 9 * S], fill=(46, 30, 18, 255), outline=accent + (255,), width=S)
    draw_text(img, px1 + 3 * S, py1 + 1 * S, name, accent + (255,), px=1)


def tavern_backdrop(w, h):
    """Intérieur de taverne : mur chaud, fenêtre sur la mer, étagère de bouteilles."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = y / h
            c = mix(rgb("#8a5a34"), rgb("#4a2c18"), t)
            plank = (x // 18) % 2
            f = 1.0 + (0.05 if plank else -0.03) + random.uniform(-0.03, 0.03)
            px[x, y] = shade(c, f) + (255,)
    d = ImageDraw.Draw(img)
    # fenêtre ronde (hublot) sur la mer
    cx, cy, r = int(w * 0.78), int(h * 0.22), int(w * 0.17)
    d.ellipse([cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4], fill=(150, 112, 50, 255))
    sea = Image.new("RGBA", (2 * r, 2 * r), (0, 0, 0, 0))
    sp = sea.load()
    for y in range(2 * r):
        for x in range(2 * r):
            t = y / (2 * r)
            sp[x, y] = (mix(rgb("#f6c27a"), rgb("#88c8e8"), t * 1.6) if t < 0.55 else mix(rgb("#2f7fb0"), rgb("#1b4f78"), t)) + (255,)
    mask = Image.new("L", (2 * r, 2 * r), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, 2 * r - 1, 2 * r - 1], fill=255)
    img.paste(sea, (cx - r, cy - r), mask)
    d.line([(cx - r, cy), (cx + r, cy)], fill=(150, 112, 50, 255), width=3)
    d.line([(cx, cy - r), (cx, cy + r)], fill=(150, 112, 50, 255), width=3)
    # étagère + bouteilles
    sy = int(h * 0.52)
    d.rectangle([0, sy, int(w * 0.32), sy + 4], fill=(60, 36, 20, 255))
    colors = [rgb("#3f8f4f"), rgb("#8f2f2f"), rgb("#c9a43a"), rgb("#3a6f9f")]
    for i in range(3):
        bx = 6 + i * 13
        c = colors[i % len(colors)]
        d.rectangle([bx, sy - 16, bx + 7, sy - 1], fill=c + (255,))
        d.rectangle([bx + 2, sy - 22, bx + 5, sy - 16], fill=c + (255,))
        d.line([(bx + 1, sy - 14), (bx + 1, sy - 4)], fill=(255, 255, 255, 120), width=1)
    # lanterne chaude (halo)
    halo = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    for i in range(12, 0, -1):
        hd.ellipse([int(w * 0.12) - i * 3, int(h * 0.15) - i * 3, int(w * 0.12) + i * 3, int(h * 0.15) + i * 3],
                   fill=(255, 190, 90, 6))
    img.alpha_composite(halo)
    return img


def dock_backdrop(w, h):
    """Ponton au coucher du soleil : ciel, mer, filet de pêche suspendu."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    px = img.load()
    horizon = int(h * 0.55)
    for y in range(h):
        for x in range(w):
            if y < horizon:
                t = y / horizon
                c = mix(rgb("#5a7fb8"), rgb("#f4a25c"), t)
            else:
                t = (y - horizon) / (h - horizon)
                c = mix(rgb("#3a78a8"), rgb("#163a5c"), t)
                if (x + y * 3) % 23 == 0:
                    c = shade(c, 1.35)
            px[x, y] = shade(c, 1 + random.uniform(-0.02, 0.02)) + (255,)
    d = ImageDraw.Draw(img)
    sun_x, sun_y, r = int(w * 0.25), horizon - 6, 14
    d.ellipse([sun_x - r, sun_y - r, sun_x + r, sun_y + r], fill=(255, 214, 120, 255))
    d.rectangle([0, horizon, w, horizon + 2], fill=(255, 200, 120, 140))
    # poteau du ponton + filet
    d.rectangle([w - 18, 0, w - 10, h], fill=(92, 62, 38, 255))
    for i in range(0, 60, 8):
        d.line([(w - 60 + i, 6), (w - 10, 6 + i)], fill=(210, 200, 170, 160), width=1)
        d.line([(w - 60, 6 + i), (w - 10 - i, 6)], fill=(210, 200, 170, 160), width=1)
    return img


def guide(img, slots, path):
    """Copie du fond avec les cases cliquables numérotées (aide pour retoucher le PNG)."""
    g = img.copy()
    d = ImageDraw.Draw(g)
    for name, s in slots.items():
        x, y = slot_xy(s)
        d.rectangle([x * S, y * S, (x + 18) * S - 1, (y + 18) * S - 1], outline=(255, 0, 255, 255), width=2)
        draw_text(g, x * S + 3, y * S + 4, str(s), (255, 0, 255, 255), px=1)
    for y in LINES_Y:
        d.rectangle([BUBBLE_TEXT_X[0] * S, y * S, BUBBLE_TEXT_X[1] * S, (y + 8) * S], outline=(0, 200, 255, 255), width=1)
    g.save(path)


# ---------------------------------------------------------------- fonds complets


def shop_background(skin):
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    wood(img, (0, 0, W, H), rgb("#5b3a22"), plank=14, vertical=True)
    # en-tête : enseigne
    wood(img, (3, 2, W - 3, 15), rgb("#7a4e2c"), plank=60, vertical=False, grain=0.06)
    bevel(img, (3, 2, W - 3, 15), (170, 120, 70, 255), (40, 24, 12, 255))
    title = "CHEZ GERTRUDE"
    draw_text(img, (W * S - text_width(title, 1)) // 2, 5 * S + 1, title, (255, 214, 120, 255), px=1, shadow=(40, 22, 10, 255))
    # portrait + bulle
    portrait_panel(img, skin, tavern_backdrop, None, (232, 190, 80))
    bubble(img, BUBBLE, (59, 44))
    # étagère des 5 articles (rangée 2) et étiquettes de prix (rangée 3)
    wood(img, (62, 53, 172, 89), rgb("#3e2716"), plank=40, vertical=False, grain=0.05)
    d = ImageDraw.Draw(img)
    rect(d, (62, 53, 172, 54), fill=(150, 110, 66))
    rect(d, (62, 87, 172, 89), fill=(30, 18, 10))
    # petit poisson accroché dans la dernière colonne
    fx, fy = 156 * S, 62 * S
    d.ellipse([fx, fy, fx + 10 * S, fy + 6 * S], fill=(120, 170, 200, 255), outline=(40, 60, 80, 255), width=S)
    d.polygon([(fx + 10 * S, fy + 3 * S), (fx + 14 * S, fy), (fx + 14 * S, fy + 6 * S)], fill=(90, 140, 170, 255))
    d.rectangle([fx + 2 * S, fy + 2 * S, fx + 3 * S, fy + 3 * S], fill=(20, 20, 20, 255))
    d.line([(fx + 5 * S, fy - 6 * S), (fx + 5 * S, fy)], fill=(200, 170, 120, 255), width=S)
    for s in range(21, 26):
        slot(img, s, style="dark")
    for s in range(30, 35):
        x, y = slot_xy(s)
        d.line([((x + 9) * S, (y - 2) * S), ((x + 9) * S, y * S)], fill=(200, 170, 120, 255), width=S)  # ficelle
        slot(img, s, style="tag")
    # cagnotte (37), relancer (40), améliorations (50-52), fermer (53)
    slot(img, 36, style="gold")
    x, y = slot_xy(36)
    draw_text(img, (x + 21) * S, (y + 6) * S, "CAGNOTTE", (232, 190, 80, 255), px=1, shadow=(30, 18, 8, 255))
    slot(img, 40, style="sea")
    x, y = slot_xy(40)
    draw_text(img, (x + 21) * S, (y + 6) * S, "RELANCER", (225, 236, 245, 255), px=1, shadow=(20, 30, 40, 255))
    draw_text(img, 9 * S, (slot_xy(50)[1] + 6) * S, "AMÉLIORATIONS", (232, 190, 80, 255), px=1, shadow=(30, 18, 8, 255))
    for s in (50, 51, 52):
        slot(img, s, style="gold")
    slot(img, 53, style="dark")
    player_inventory(img)
    round_corners(img)
    return img


def francis_background(skin):
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    wood(img, (0, 0, W, H), rgb("#40576a"), plank=14, vertical=False, grain=0.08)
    wood(img, (3, 2, W - 3, 15), rgb("#5e4630"), plank=60, vertical=False, grain=0.06)
    bevel(img, (3, 2, W - 3, 15), (170, 130, 90, 255), (30, 20, 12, 255))
    title = "FRANCIS - PÊCHEUR DES MERS"
    draw_text(img, (W * S - text_width(title, 1)) // 2, 5 * S + 1, title, (235, 225, 200, 255), px=1, shadow=(20, 14, 8, 255))
    portrait_panel(img, skin, dock_backdrop, "FRANCIS", (210, 200, 170))
    bubble(img, BUBBLE, (59, 44))
    # tableau d'infos (le texte est écrit par le plugin, police cleanfishing:line4..6)
    bx1, by1, bx2, by2 = BOARD
    inner = frame_box(img, (bx1 - 2, by1 - 2, bx2 + 2, by2 + 2), (70, 48, 28), (180, 140, 90), (30, 18, 8), 2)
    rect(ImageDraw.Draw(img), inner, fill=(38, 52, 44))
    for i in range(0, (inner[2] - inner[0]) * S, 7):
        ImageDraw.Draw(img).point([(inner[0] * S + i, inner[1] * S + (i * 13) % ((inner[3] - inner[1]) * S))], fill=(70, 90, 78, 255))
    # trois boutons : rentrer (46), journée (49), continuer (52)
    labels = {46: ("RENTRER", (130, 220, 130)), 49: ("JOURNÉE", (235, 225, 200)), 52: ("PÊCHER", (255, 210, 120))}
    for s_, (label, color) in labels.items():
        x, y = slot_xy(s_)
        plate = (x - 16, 90, x + 34, 125)
        wood(img, plate, rgb("#6b4a2c"), plank=60, vertical=False, grain=0.05)
        bevel(img, plate, (180, 140, 90, 255), (30, 18, 8, 255))
        tw = text_width(label, 1)
        draw_text(img, (x + 9) * S - tw // 2, 95 * S + 1, label, color + (255,), px=1, shadow=(30, 18, 8, 255))
        slot(img, s_, style="sea" if s_ == 49 else "dark")
    player_inventory(img)
    round_corners(img)
    return img


# ---------------------------------------------------------------- cartes de pouvoir


CARD_W, CARD_H = 144, 180   # le texte de la carte (police vanilla) fait jusqu'à ~130 px de large
CARD_TIERS = {  # nom : (caractère, couleur du cadre, couleur claire, couleur sombre)
    "common": ("\ue010", (70, 130, 170), (159, 216, 255), (24, 48, 72)),
    "rare": ("\ue011", (130, 80, 190), (197, 140, 255), (52, 26, 82)),
    "epic": ("\ue012", (200, 150, 40), (255, 204, 77), (82, 52, 10)),
}
# Lignes du texte (10 px chacune) : 0 = cadre, 6 = titre, 8-9 = description, 11 = niveau, 16 = rareté.


def card_frame(color, light, dark):
    """Carte de pouvoir : cadre coloré selon la rareté, médaillon en haut, parchemin pour le texte."""
    img = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, CARD_W - 1, CARD_H - 1], radius=9, fill=dark + (255,))
    d.rounded_rectangle([3, 3, CARD_W - 4, CARD_H - 4], radius=7, fill=color + (255,))
    d.rounded_rectangle([6, 6, CARD_W - 7, CARD_H - 7], radius=5, fill=dark + (255,))
    # dégradé et étoiles en haut
    for y in range(7, 56):
        d.line([(7, y), (CARD_W - 8, y)], fill=mix(dark, color, 0.35 * (1 - (y - 7) / 49)) + (255,))
    for _ in range(40):
        x, y = random.randint(9, CARD_W - 10), random.randint(9, 52)
        d.point((x, y), fill=light + (random.randint(120, 255),))
    # médaillon
    cx, cy, r = CARD_W // 2, 29, 19
    d.ellipse([cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3], fill=color + (255,))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=mix(dark, light, 0.22) + (255,))
    d.ellipse([cx - r + 4, cy - r + 4, cx + r - 4, cy + r - 4], outline=light + (255,), width=1)
    k = 12
    d.polygon([(cx, cy - k), (cx + 3, cy - 3), (cx + k, cy), (cx + 3, cy + 3), (cx, cy + k), (cx - 3, cy + 3),
               (cx - k, cy), (cx - 3, cy - 3)], fill=light + (255,))
    d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=(255, 255, 255, 255))
    # parchemin (titre + description : lignes 6 à 11)
    d.rounded_rectangle([8, 57, CARD_W - 9, 123], radius=4, fill=(244, 232, 205, 255), outline=mix(color, dark, 0.4) + (255,))
    for y in range(60, 122, 5):
        d.line([(11, y), (CARD_W - 12, y)], fill=(237, 224, 195, 255))
    # bandeau du bas (rareté, ligne 16)
    d.rectangle([14, 156, CARD_W - 15, 170], fill=color + (255,))
    d.line([(14, 156), (CARD_W - 15, 156)], fill=light + (255,))
    d.line([(14, 170), (CARD_W - 15, 170)], fill=dark + (255,))
    for x, y in ((10, 10), (CARD_W - 11, 10), (10, CARD_H - 11), (CARD_W - 11, CARD_H - 11)):
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=light + (255,))
    return img


def write_cards():
    out = os.path.join(PACK, "assets", "cleanfishing", "textures", "card")
    os.makedirs(out, exist_ok=True)
    providers = []
    for name, (char, color, light, dark) in CARD_TIERS.items():
        card_frame(color, light, dark).save(os.path.join(out, name + ".png"))
        providers.append({"type": "bitmap", "file": f"cleanfishing:card/{name}.png", "ascent": 7, "height": CARD_H,
                          "chars": [char]})
    with open(os.path.join(FONT_DIR, "card.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)


# ---------------------------------------------------------------- polices


def write_fonts():
    os.makedirs(FONT_DIR, exist_ok=True)
    advances = {}
    for i in range(9):  # .. : -1,-2,-4,...,-256 ; .. : +1,...,+256
        advances[chr(0xF801 + i)] = -(1 << i)
        advances[chr(0xF811 + i)] = 1 << i
    gui = {"providers": [
        {"type": "space", "advances": advances},
        {"type": "bitmap", "file": "cleanfishing:gui/shop.png", "ascent": 13, "height": H, "chars": [""]},
        {"type": "bitmap", "file": "cleanfishing:gui/francis.png", "ascent": 13, "height": H, "chars": [""]},
    ]}
    with open(os.path.join(FONT_DIR, "gui.json"), "w", encoding="utf-8") as f:
        json.dump(gui, f, ensure_ascii=True, indent=1)
    vanilla = json.load(open(os.path.join(HERE, "vanilla_font.json"), encoding="utf-8"))
    for old in os.listdir(FONT_DIR):
        if old.startswith("bubble") or old.startswith("line") or old == "card.json":
            os.remove(os.path.join(FONT_DIR, old))
    for n, shift in enumerate(SHIFTS, start=1):
        providers = [{"type": "space", "advances": {" ": 4}}]
        for p in vanilla:
            providers.append({"type": "bitmap", "file": p["file"], "height": p["height"],
                              "ascent": p["ascent"] - shift, "chars": p["chars"]})
        with open(os.path.join(FONT_DIR, f"line{n}.json"), "w", encoding="utf-8") as f:
            json.dump({"providers": providers}, f, ensure_ascii=True)


def write_meta():
    meta = {"pack": {
        "description": "CleanFishing - interfaces de Gertrude et Francis",
        "min_format": PACK_FORMAT,
        "max_format": PACK_FORMAT + 20,
    }}
    with open(os.path.join(PACK, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)


def pack_icon(gertrude):
    icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    bg = tavern_backdrop(128, 128)
    icon.alpha_composite(bg)
    icon.alpha_composite(render_bust(gertrude, 128, 128))
    icon.save(os.path.join(PACK, "pack.png"))


def main():
    os.makedirs(GUI_DIR, exist_ok=True)
    gertrude = load_skin("gertrude", default_gertrude)
    francis = load_skin("francis", default_francis)
    shop = shop_background(gertrude)
    shop.save(os.path.join(GUI_DIR, "shop.png"))
    fr = francis_background(francis)
    fr.save(os.path.join(GUI_DIR, "francis.png"))
    preview = os.path.join(HERE, "preview")
    os.makedirs(preview, exist_ok=True)
    guide(shop, {"offre": 21, "o2": 22, "o3": 23, "o4": 24, "o5": 25, "p1": 30, "p2": 31, "p3": 32, "p4": 33, "p5": 34,
                 "cagnotte": 36, "relancer": 40, "u1": 50, "u2": 51, "u3": 52, "fermer": 53},
          os.path.join(preview, "shop_guide.png"))
    guide(fr, {"rentrer": 46, "info": 49, "pecher": 52}, os.path.join(preview, "francis_guide.png"))
    write_fonts()
    write_cards()
    write_meta()
    pack_icon(gertrude)
    print("Pack écrit dans", PACK)


if __name__ == "__main__":
    main()
