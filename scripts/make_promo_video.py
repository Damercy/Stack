from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from moviepy import (
    AudioClip,
    ColorClip,
    CompositeVideoClip,
    ImageClip,
    VideoFileClip,
    concatenate_videoclips,
    vfx,
)


ROOT = Path(__file__).resolve().parents[1]
PROMO = ROOT / "build" / "promo"
OUT = PROMO / "stack-short-fresh.mp4"
W, H = 1080, 1920
FPS = 30
PEARL = (247, 248, 245)


def font(size: int, mono: bool = False) -> ImageFont.FreeTypeFont:
    filename = "fragment_mono.ttf" if mono else "instrument_sans.ttf"
    path = ROOT / "app" / "src" / "main" / "res" / "font" / filename
    return ImageFont.truetype(str(path), size=size)


def text_layer(
    title: str,
    duration: float,
    *,
    subtitle: str | None = None,
    y: int = 720,
    title_size: int = 86,
    dark: bool = True,
) -> ImageClip:
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    title_font = font(title_size)
    subtitle_font = font(34, mono=True)
    foreground = (26, 31, 28, 248) if dark else (247, 248, 245, 250)
    muted = (91, 107, 98, 225) if dark else (224, 234, 228, 235)

    title_box = draw.textbbox((0, 0), title, font=title_font)
    title_x = (W - (title_box[2] - title_box[0])) // 2
    draw.text((title_x, y), title, font=title_font, fill=foreground)
    if subtitle:
        subtitle_box = draw.textbbox((0, 0), subtitle, font=subtitle_font)
        subtitle_x = (W - (subtitle_box[2] - subtitle_box[0])) // 2
        draw.text((subtitle_x, y + 132), subtitle, font=subtitle_font, fill=muted)

    return (
        ImageClip(np.array(image))
        .with_duration(duration)
        .with_effects([vfx.FadeIn(0.18), vfx.FadeOut(0.22)])
    )


def glass_pill(label: str, duration: float, y: int = 1540) -> ImageClip:
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    pill_font = font(32)
    box = draw.textbbox((0, 0), label, font=pill_font)
    width = box[2] - box[0] + 74
    height = 78
    x = (W - width) // 2
    draw.rounded_rectangle(
        (x, y, x + width, y + height),
        radius=39,
        fill=(255, 255, 255, 214),
        outline=(93, 136, 115, 105),
        width=2,
    )
    draw.line((x + 24, y + 8, x + width - 24, y + 8), fill=(255, 255, 255, 235), width=2)
    draw.text((x + 37, y + 17), label, font=pill_font, fill=(53, 93, 74, 248))
    return (
        ImageClip(np.array(image))
        .with_duration(duration)
        .with_effects([vfx.FadeIn(0.16), vfx.FadeOut(0.18)])
    )


def app_clip(filename: str, start: float, end: float) -> CompositeVideoClip:
    source = VideoFileClip(str(PROMO / filename)).subclipped(start, end)
    # Android screenrecord pillarboxes the 9:20 app surface in a 9:16 stream.
    # Remove those bars, preserve the full app height, and frame it on pearl.
    source = source.cropped(x1=54, x2=485).resized(height=H)
    return CompositeVideoClip(
        [
            ColorClip((W, H), color=PEARL).with_duration(source.duration),
            source.with_position(("center", "center")),
        ],
        size=(W, H),
    ).with_duration(source.duration)


def punch_frame(clip: CompositeVideoClip, at: float, duration: float) -> CompositeVideoClip:
    frame = ImageClip(clip.get_frame(at)).with_duration(duration).resized(1.045)
    return CompositeVideoClip(
        [
            ColorClip((W, H), color=PEARL).with_duration(duration),
            frame.with_position(("center", "center")),
        ],
        size=(W, H),
    )


def original_timing_track(duration: float) -> AudioClip:
    impact_times = (3.18, 3.47, 3.76)

    def frame(t):
        t = np.asarray(t)
        intro = 0.035 * np.sin(2 * np.pi * 94 * t)
        intro += 0.018 * np.sin(2 * np.pi * 188 * t)
        pulse = np.zeros_like(t, dtype=float)
        for beat in np.arange(0.28, duration, 0.56):
            elapsed = np.maximum(0, t - beat)
            pulse += 0.075 * np.exp(-18 * elapsed) * (t >= beat) * np.sin(2 * np.pi * 430 * elapsed)
        for impact in impact_times:
            elapsed = np.maximum(0, t - impact)
            noise = np.sin(2 * np.pi * 73 * elapsed) + 0.5 * np.sin(2 * np.pi * 910 * elapsed)
            pulse += 0.19 * np.exp(-32 * elapsed) * (t >= impact) * noise
        outro = 0.022 * np.sin(2 * np.pi * 282 * t) * np.clip((t - 8.0) / 1.2, 0, 1)
        return np.clip(intro + pulse + outro, -0.34, 0.34)

    return AudioClip(frame, duration=duration, fps=44100)


def main() -> None:
    tap = app_clip("fresh-tap-540.mp4", 0.15, 5.75)
    leader = app_clip("fresh-leader-540.mp4", 0.1, 6.3)
    stack = app_clip("fresh-stack-540.mp4", 0.0, 1.52)

    intro = CompositeVideoClip(
        [
            ColorClip((W, H), color=PEARL).with_duration(0.82),
            text_layer("STACK", 0.82, subtitle="A tiny daily ritual", y=690, title_size=104),
        ],
        size=(W, H),
    )

    tap_scene = CompositeVideoClip(
        [tap.subclipped(0.2, 2.56), glass_pill("tap  |  stack  |  repeat", 1.65, 1580).with_start(0.48)],
        size=(W, H),
    ).with_duration(2.36)

    bang_one = punch_frame(tap, 2.95, 0.29)
    bang_two = punch_frame(leader, 2.05, 0.29)
    bang_three = punch_frame(stack, 1.15, 0.29)

    leader_scene = CompositeVideoClip(
        [leader.subclipped(1.65, 4.04), glass_pill("A fresh climb every day", 1.7, 1580).with_start(0.38)],
        size=(W, H),
    ).with_duration(2.39)

    stack_scene = CompositeVideoClip(
        [stack, glass_pill("Real materials. Quiet sounds.", 1.2, 1580).with_start(0.16)],
        size=(W, H),
    ).with_duration(1.52)

    outro = CompositeVideoClip(
        [
            ColorClip((W, H), color=PEARL).with_duration(2.24),
            text_layer("Build yours.", 2.24, subtitle="stack.dayaonweb.dev", y=610, title_size=82),
            glass_pill("Join the Android waitlist", 1.7, 1210).with_start(0.2),
        ],
        size=(W, H),
    )

    base = concatenate_videoclips(
        [intro, tap_scene, bang_one, bang_two, bang_three, leader_scene, stack_scene, outro],
        method="compose",
    )
    final = base.with_audio(original_timing_track(base.duration))
    final.write_videofile(
        str(OUT),
        fps=FPS,
        codec="libx264",
        audio_codec="aac",
        bitrate="9000k",
        preset="veryfast",
        threads=4,
    )


if __name__ == "__main__":
    main()
