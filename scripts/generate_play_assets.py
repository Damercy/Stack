"""Render Stack's brand artwork and copy reviewed, unmodified device captures."""
from pathlib import Path
from shutil import copyfile
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "store-assets" / "play"
OUT.mkdir(parents=True, exist_ok=True)
SUN, INK, RED, PAPER, BLUE = "#F3E36B", "#111115", "#E84B2C", "#F4EFE0", "#263D9C"


def tower(draw, x, y, scale):
    for points, color in [
        ([(41,29),(73,36),(70,50),(38,43)], RED),
        ([(34,43),(67,48),(65,61),(32,56)], PAPER),
        ([(25,59),(82,63),(81,70),(24,66)], BLUE),
        ([(53,68),(43,80),(63,80)], INK),
    ]:
        draw.polygon([(x+a*scale,y+b*scale) for a,b in points], fill=color)


# Supersample original vector artwork, using the same paths as the launcher.
icon = Image.new("RGB", (2048,2048), SUN)
tower(ImageDraw.Draw(icon), 0, 0, 2048/108)
icon.resize((512,512),Image.Resampling.LANCZOS).save(OUT/"icon-512.png")
graphic = Image.new("RGB", (2048,1000), SUN)
draw = ImageDraw.Draw(graphic)
poster = ImageFont.truetype(str(ROOT/"app/src/main/res/font/anton.ttf"), 390)
utility = ImageFont.truetype(str(ROOT/"app/src/main/res/font/roboto_mono.ttf"), 36)
draw.text((95,70), "OFF BALANCE", font=utility, fill=INK)
draw.text((80,130), "STACK", font=poster, fill=INK)
draw.text((95,780), "TAP. TILT. RECOVER.", font=utility, fill=INK)
tower(draw, 1150, 80, 8.5)
graphic.resize((1024,500),Image.Resampling.LANCZOS).save(OUT/"feature-graphic-1024x500.png")
for number, screen in enumerate(["home","play","music","onboarding","today","settings"], 1):
    copyfile(ROOT/f"media/screenshots/{screen}-phone.png", OUT/f"phone-{number:02}-{screen}.png")
copyfile(ROOT/"media/screenshots/play-unfolded.png",OUT/"foldable-play.png")
print("Generated brand artwork and copied seven unmodified device screenshots.")
