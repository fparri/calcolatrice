"""Genera l'icona adattiva (sfondo, primo piano, monocromatica) e le anteprime SVG dalla stessa geometria.

Uso: python3 tools/make_icon.py app/src/main/res <cartella delle anteprime>
"""
import math, os, sys

RES = sys.argv[1]
OUT = sys.argv[2]

BG_TOP = "#5A6CE0"     # gradiente dello sfondo, in diagonale
BG_BOTTOM = "#2F3E9E"
TILE = "#FFFFFF"
TILE_EQ = "#FFD7F1"    # tasto "=" (stesso rosa del tasto C nell'app)
INK = "#3346A8"        # simboli

# Griglia 2×2 di tasti, 48×48 dp al centro della tela da 108: anche gli angoli arrotondati restano
# dentro il cerchio della zona sicura (66 dp), che è l'unica parte sempre visibile con ogni maschera.
SIZE, GAP, RADIUS = 21.5, 5.0, 6.5
ORIGIN = (108 - (2 * SIZE + GAP)) / 2  # 27
STROKE = 3.1
ARM = 5.0  # metà lunghezza dei simboli

def f(x): return ("%.2f" % x).rstrip("0").rstrip(".")

def rrect(x, y, w, h, r):
    return (f"M{f(x+r)},{f(y)}H{f(x+w-r)}A{f(r)},{f(r)} 0,0 1,{f(x+w)},{f(y+r)}V{f(y+h-r)}"
            f"A{f(r)},{f(r)} 0,0 1,{f(x+w-r)},{f(y+h)}H{f(x+r)}A{f(r)},{f(r)} 0,0 1,{f(x)},{f(y+h-r)}"
            f"V{f(y+r)}A{f(r)},{f(r)} 0,0 1,{f(x+r)},{f(y)}Z")

def tiles():
    out = []
    for row in range(2):
        for col in range(2):
            x = ORIGIN + col * (SIZE + GAP); y = ORIGIN + row * (SIZE + GAP)
            out.append((x, y, x + SIZE / 2, y + SIZE / 2))
    return out

def line(x1, y1, x2, y2): return f"M{f(x1)},{f(y1)}L{f(x2)},{f(y2)}"

def symbols():
    (_, _, ax, ay), (_, _, bx, by), (_, _, cx, cy), (_, _, dx, dy) = tiles()
    d = ARM / math.sqrt(2) * 1.08
    return [
        line(ax - ARM, ay, ax + ARM, ay) + line(ax, ay - ARM, ax, ay + ARM),        # +
        line(bx - ARM, by, bx + ARM, by),                                           # −
        line(cx - d, cy - d, cx + d, cy + d) + line(cx - d, cy + d, cx + d, cy - d),  # ×
        line(dx - ARM, dy - 2.5, dx + ARM, dy - 2.5) + line(dx - ARM, dy + 2.5, dx + ARM, dy + 2.5),  # =
    ]

HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!--
{comment}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{extra}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''

def vpath(data, fill=None, stroke=None):
    attrs = [f'android:pathData="{data}"']
    if fill: attrs.append(f'android:fillColor="{fill}"')
    if stroke:
        attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{f(STROKE)}"', 'android:strokeLineCap="round"']
    return "    <path\n        " + "\n        ".join(attrs) + " />\n"

def background():
    s = HEADER.format(comment="    Livello di sfondo dell'icona adattiva: riempie tutta la tela da 108 dp (il sistema lo ritaglia\n    con la forma scelta dal launcher). Gradiente leggero in diagonale, nessuna ombra.",
                      extra='\n    xmlns:aapt="http://schemas.android.com/aapt"')
    s += '''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="%s" />
                <item android:offset="1" android:color="%s" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''' % (BG_TOP, BG_BOTTOM)
    return s

def foreground():
    s = HEADER.format(comment="    Livello in primo piano: quattro tasti (+ − × =) in una griglia 2×2 di 48 dp, al centro della tela\n    da 108 dp e quindi dentro la zona sicura di 66 dp. Generato da tools/make_icon.py.", extra="")
    t = tiles()
    s += vpath("".join(rrect(x, y, SIZE, SIZE, RADIUS) for x, y, _, _ in t[:3]), fill=TILE)
    s += vpath(rrect(t[3][0], t[3][1], SIZE, SIZE, RADIUS), fill=TILE_EQ)
    s += vpath("".join(symbols()), stroke=INK)
    return s + "</vector>\n"

def monochrome():
    s = HEADER.format(comment="    Livello monocromatico per le icone a tema (Android 13+): stessa geometria del primo piano,\n    solo i simboli con un contorno sottile dei tasti. Il sistema usa la sola trasparenza e lo colora.", extra="")
    t = tiles()
    outline = "".join(rrect(x, y, SIZE, SIZE, RADIUS) for x, y, _, _ in t)
    s += ('    <path\n        android:pathData="%s"\n        android:strokeColor="#FF000000"\n        android:strokeWidth="1.8" />\n' % outline)
    s += vpath("".join(symbols()), stroke="#FF000000")
    return s + "</vector>\n"

open(os.path.join(RES, "drawable/ic_launcher_background.xml"), "w").write(background())
open(os.path.join(RES, "drawable/ic_launcher_foreground.xml"), "w").write(foreground())
open(os.path.join(RES, "drawable/ic_launcher_monochrome.xml"), "w").write(monochrome())

# Verifica: il punto più lontano dal centro (sugli angoli arrotondati) sta nel cerchio di raggio 33.
half = SIZE + GAP / 2
assert (half - RADIUS) * math.sqrt(2) + RADIUS <= 33, "fuori dalla zona sicura"

# --- Anteprime SVG: maschera tonda, squircle, a tema chiaro, con la zona sicura ---
def svg(mask, themed=False, guides=False):
    t = tiles()
    clip = {"circle": '<circle cx="54" cy="54" r="36"/>',
            "squircle": '<path d="M54,18C24,18 18,24 18,54S24,90 54,90 90,84 90,54 84,18 54,18Z"/>',
            "rounded": '<rect x="18" y="18" width="72" height="72" rx="16"/>'}[mask]
    body = []
    if themed:
        body.append('<rect width="108" height="108" fill="#E3E8FF"/>')
        outline = "".join(rrect(x, y, SIZE, SIZE, RADIUS) for x, y, _, _ in t)
        body.append(f'<path d="{outline}" fill="none" stroke="#2F3E9E" stroke-width="1.8"/>')
        body.append(f'<path d="{"".join(symbols())}" fill="none" stroke="#2F3E9E" stroke-width="{STROKE}" stroke-linecap="round"/>')
    else:
        body.append(f'<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{BG_TOP}"/><stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient></defs>')
        body.append('<rect width="108" height="108" fill="url(#g)"/>')
        body.append(f'<path d="{"".join(rrect(x, y, SIZE, SIZE, RADIUS) for x, y, _, _ in t[:3])}" fill="{TILE}"/>')
        body.append(f'<path d="{rrect(t[3][0], t[3][1], SIZE, SIZE, RADIUS)}" fill="{TILE_EQ}"/>')
        body.append(f'<path d="{"".join(symbols())}" fill="none" stroke="{INK}" stroke-width="{STROKE}" stroke-linecap="round"/>')
    if guides:
        body.append('<circle cx="54" cy="54" r="33" fill="none" stroke="#FF5252" stroke-width="0.6" stroke-dasharray="2 2"/>')
    # Il launcher mostra solo i 72 dp centrali della tela da 108.
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="288" height="288">'
            f'<clipPath id="m">{clip}</clipPath>'
            f'<g clip-path="url(#m)">{"".join(body)}</g></svg>')

for name, args in {"circle": ("circle",), "squircle": ("squircle",), "rounded": ("rounded",),
                   "themed": ("circle", True), "safezone": ("circle", False, True)}.items():
    open(os.path.join(OUT, f"{name}.svg"), "w").write(svg(*args))
