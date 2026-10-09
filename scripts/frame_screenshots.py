"""Wrap unmodified app screenshots in consistent vector device frames."""
import base64
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def frame(source, target, fold=False):
    raw = (ROOT / 'media/screenshots' / source).read_bytes()
    width, height = struct.unpack('>II', raw[16:24])
    display_width = 800 if fold else 400
    display_height = display_width * height / width
    bezel, margin = (14, 24) if fold else (12, 24)
    x = y = bezel + margin
    outer_w, outer_h = display_width + 2 * bezel, display_height + 2 * bezel
    view_w, view_h = outer_w + 2 * margin, outer_h + 2 * margin
    encoded = base64.b64encode(raw).decode()
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{view_w}" height="{view_h}" viewBox="0 0 {view_w} {view_h}">
<title>Stack on {'an unfolded device' if fold else 'a phone'}</title>
<defs><clipPath id="display"><rect x="{x}" y="{y}" width="{display_width}" height="{display_height}" rx="{15 if fold else 20}"/></clipPath></defs>
<rect x="{margin}" y="{margin}" width="{outer_w}" height="{outer_h}" rx="{28 if fold else 32}" fill="#151518" stroke="#45454a" stroke-width="2"/>
<image x="{x}" y="{y}" width="{display_width}" height="{display_height}" clip-path="url(#display)" href="data:image/png;base64,{encoded}"/>
</svg>'''
    output = ROOT / 'media/showcase' / target
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(svg)

if __name__ == '__main__':
    frame('play-unfolded.png', 'foldable.svg', True)
    for name in ('home', 'play', 'music', 'onboarding', 'today', 'settings'):
        frame(f'{name}-phone.png', f'{name}.svg')
