"""Draw the README artwork.

Everything here is original vector art drawn from scratch: no screenshots, no poster
scans, no network access, no third-party fonts or images. Titles, blurbs, ratings and
progress values are invented placeholders that mirror the current layout of the app, so
the pictures stay in sync with the UI without shipping anyone's media library.

Run: python scripts/generate-readme-art.py
"""

import html
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "docs" / "images"
OUT.mkdir(parents=True, exist_ok=True)

# Light-on-dark artwork palettes, one per placeholder poster.
PALETTES = [
    ("#153840", "#65d9c3"),
    ("#24284c", "#f5b676"),
    ("#352d42", "#d998bd"),
    ("#234340", "#c0d392"),
    ("#263e58", "#80b5ed"),
    ("#443738", "#efa783"),
    ("#313555", "#aca2e1"),
]
TITLES = ["星港来信", "微光列车", "雾岛手记", "风的形状", "蓝色回声", "山海之间", "第七颗种子"]
META = ["2030 · 科幻 · 104 分钟", "2029 · 剧情 · 96 分钟", "2031 · 纪录 · 52 分钟", "2028 · 动画 · 88 分钟"]

BG = "#0b1418"
CARD = "#172629"
CARD_SOFT = "#1d2f31"
ACCENT = "#bfd8c9"          # light sage: focus ring and active pill in the dark theme
ACCENT_FILL = "#29483d"     # focused card fill in the dark theme
TEXT = "#eff4ee"
MUTED = "#a9bdb8"
FAINT = "#7d928e"


def esc(value: str) -> str:
    return html.escape(str(value))


def text(x, y, value, size=22, fill=TEXT, weight=400, anchor="start", spacing=None):
    extra = f' text-anchor="{anchor}"' if anchor != "start" else ""
    extra += f' letter-spacing="{spacing}"' if spacing else ""
    return (f'<text x="{x}" y="{y}" fill="{fill}" font-size="{size}" font-weight="{weight}"'
            f'{extra}>{esc(value)}</text>')


def rect(x, y, w, h, fill, rx=18, extra=""):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" {extra}/>'


def sun(x, y, size=42):
    return (f'<g transform="translate({x} {y}) scale({size / 100})">'
            f'<g stroke="#ffd879" stroke-width="7" stroke-linecap="round">'
            f'<path d="M50 7V17 M50 83V93 M7 50H17 M83 50H93 M20 20L27 27 M73 73L80 80 '
            f'M20 80L27 73 M73 27L80 20"/></g>'
            f'<path fill="url(#gold)" fill-rule="evenodd" '
            f'd="M50 25 A25 25 0 1 1 50 75 A25 25 0 1 1 50 25Z M43 39V62L63 50Z"/></g>')


def canvas(body, label, width=1600, height=900, background=BG):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}" role="img" aria-label="{esc(label)}">'
            f'<defs><linearGradient id="gold" x2="1" y2="1">'
            f'<stop stop-color="#ffe99e"/><stop offset="1" stop-color="#f4a52d"/></linearGradient>'
            f'<linearGradient id="fade" x2="0" y2="1">'
            f'<stop stop-color="#0b1418" stop-opacity="0"/><stop offset="1" stop-color="#0b1418"/>'
            f'</linearGradient>'
            f'<linearGradient id="screen" x2="0" y2="1">'
            f'<stop stop-color="#132126"/><stop offset="1" stop-color="#0d181c"/></linearGradient>'
            f'<filter id="lift" x="-24%" y="-24%" width="148%" height="148%">'
            f'<feDropShadow dx="0" dy="14" stdDeviation="16" flood-color="#04090b" flood-opacity=".55"/>'
            f'</filter>'
            f'<filter id="glow" x="-30%" y="-30%" width="160%" height="160%">'
            f'<feGaussianBlur stdDeviation="70"/></filter></defs>'
            f'<g font-family="Microsoft YaHei, Noto Sans CJK SC, Source Han Sans SC, sans-serif">'
            f'{rect(0, 0, width, height, background, 0)}{body}</g></svg>')


def poster(x, y, w, h, index, label=True, ring=False):
    dark, light = PALETTES[index % len(PALETTES)]
    uid = f"p{x}_{y}_{index}"
    radius = max(10, min(18, w * 0.09))
    body = (f'<defs><clipPath id="{uid}">{rect(x, y, w, h, "white", radius)}</clipPath></defs>'
            f'<g clip-path="url(#{uid})">')
    body += rect(x, y, w, h, dark, 0)
    body += f'<circle cx="{x + w * .72}" cy="{y + h * .26}" r="{w * .30}" fill="{light}" opacity=".85"/>'
    for band in range(5):
        body += (f'<path d="M{x - w * .2} {y + h * (.55 + band * .10)} '
                 f'Q{x + w * .5} {y + h * (.12 + band * .14)} {x + w * 1.2} {y + h * (.62 + band * .11)} '
                 f'L{x + w * 1.2} {y + h} H{x - w * .2}Z" '
                 f'fill="{dark if band % 2 else light}" opacity=".4"/>')
    body += rect(x, y + h * .55, w, h * .45, "url(#fade)", 0)
    if label and w >= 120:
        body += text(x + 14, y + h - 18, TITLES[index % len(TITLES)], min(24, w / 8), weight=600)
    body += "</g>"
    if ring:
        # The focused card: a light frame that follows the card outline, wrapping the corners.
        body += (f'<g filter="url(#lift)">'
                 f'<rect x="{x + 1.5}" y="{y + 1.5}" width="{w - 3}" height="{h - 3}" '
                 f'rx="{radius - 1}" fill="none" stroke="{ACCENT}" stroke-width="3"/></g>')
    return body


def brand():
    return sun(46, 30, 48) + text(108, 66, "SunnyTV", 29, weight=700)


def nav(active=0, x=676, y=26):
    """Pinned navigation pill: home, libraries, search, settings."""
    icons = ["⌂", "▥", "⌕", "⚙"]
    body = rect(x, y, 274, 62, "#1a2a2d", 31)
    for index, icon in enumerate(icons):
        cx = x + 40 + index * 66
        if index == active:
            body += f'<circle cx="{cx}" cy="{y + 31}" r="26" fill="{ACCENT_FILL}"/>'
        body += text(cx, y + 40, icon, 24, ACCENT if index == active else "#c8d6d2", anchor="middle")
    return body


def button(x, y, label, primary=False, width=None):
    width = width or (74 if len(label) <= 2 else 150)
    fill = ACCENT_FILL if primary else "#233335"
    ink = ACCENT if primary else "#e4eeea"
    return (rect(x, y, width, 52, fill, 26)
            + f'<rect x="{x + 1}" y="{y + 1}" width="{width - 2}" height="50" rx="25" fill="none" '
              f'stroke="{"#8fb9a6" if primary else "#39514f"}" stroke-width="2"/>'
            + text(x + 22, y + 34, label, 20, ink))


def footnote(value="界面示意 · 片名、简介、评分与进度均为原创虚构数据，未使用任何真实影视素材或媒体库截图"):
    return text(48, 866, value, 15, FAINT)


def home_screen():
    body = brand() + nav(0)
    body += f'<circle cx="1150" cy="400" r="340" fill="#274a44" opacity=".36" filter="url(#glow)"/>'
    body += text(48, 196, "首映推荐", 18, ACCENT)
    body += text(48, 276, "星港来信", 64, weight=700)
    body += text(48, 318, META[0] + "    ★ 8.2", 19, MUTED)
    body += text(48, 382, "一封来自无人星港的信，让远行者重新找到归途。", 21, "#c6d4d0")
    body += text(48, 418, "在缓慢转动的星环下，陌生人开始交换各自的旅程。", 21, "#c6d4d0")
    body += button(48, 462, "▶  播放", primary=True)
    body += button(180, 462, "ⓘ  查看详情")
    body += text(48, 566, "继续播放", 17, MUTED)
    for index in range(2):
        x = 48 + index * 264
        body += rect(x, 582, 248, 62, "#213032", 31)
        body += f'<circle cx="{x + 32}" cy="613" r="21" fill="{PALETTES[index][1]}"/>'
        body += text(x + 66, 608, TITLES[index + 1], 16)
        body += text(x + 66, 630, "剩余 28 分钟", 12, MUTED)
    body += text(48, 700, "向下查看更多媒体  ↓        （按「下」整屏切换到「我的媒体库」与各库最新入库）", 15, MUTED)
    body += poster(560, 252, 340, 340, 0)
    for index in range(6):
        body += poster(928 + index * 104, 252, 92, 340, index + 1, label=False)
    return body


def library_screen():
    body = brand() + nav(1)
    body += f'<circle cx="1180" cy="300" r="300" fill="#244540" opacity=".38" filter="url(#glow)"/>'
    body += text(48, 152, "媒体库 / 纪录片", 16, ACCENT, spacing="1.2")
    body += text(48, 220, "地球知识局", 48, weight=700)
    body += text(48, 258, "155 个条目    ★ 8.6", 18, MUTED)
    body += text(48, 312, "把地图、气候与城市的来龙去脉讲成一条看得见的线。", 19, "#c6d4d0")
    body += button(48, 344, "▶  继续播放", primary=True)
    body += button(196, 344, "ⓘ  查看详情")
    body += text(48, 428, "向下查看更多媒体  ↓", 15, MUTED)
    body += poster(586, 132, 280, 280, 2)
    for index in range(6):
        body += poster(902 + index * 108, 132, 92, 280, index + 3, label=False)

    tools_y = 448
    body += text(48, tools_y + 34, "地球知识局 · 全部内容", 22, weight=600)
    body += text(292, tools_y + 34, "155 个条目", 14, MUTED)
    for index, icon in enumerate(["⇅", "cc", "▤", "▥"]):
        x = 380 + index * 72
        body += f'<circle cx="{x + 26}" cy="{tools_y + 26}" r="26" fill="#233335"/>'
        body += text(x + 26, tools_y + 35, icon, 20, ACCENT if index == 0 else "#dfe9e5", anchor="middle")
    body += f'<path d="M48 {tools_y + 58}H1552" stroke="#22383a" stroke-width="2"/>'

    grid_y = tools_y + 52
    for index in range(6):
        body += poster(48 + index * 254, grid_y, 226, 268, index, ring=index == 0)
    body += text(48, grid_y + 306, "聚焦卡片：贴合圆角的柔光描边（内缩描边与卡片轮廓同心），四周阴影由行内留白保护", 14, FAINT)
    return body


def episodes_screen():
    body = brand()
    body += text(48, 158, "单集排布，按你的习惯选择", 38, weight=700)
    body += text(48, 198, "《雾岛手记》· 虚构剧集 · 每个媒体库独立保存", 20, MUTED)
    for index, title in enumerate(["横向海报", "竖向列表", "数字选集"]):
        x = 48 + index * 512
        body += rect(x, 240, 480, 556, CARD, 24)
        body += text(x + 26, 292, title, 26, ACCENT, weight=600)
        if index == 0:
            body += poster(x + 24, 322, 300, 188, 2)
            body += poster(x + 338, 322, 118, 188, 3, label=False)
            body += text(x + 24, 548, "第 1 集 · 潮汐时刻", 21, weight=600)
            body += text(x + 24, 588, "一座灯塔亮起了陌生的信号，岛上的记录员", 18, "#b7c9c4")
            body += text(x + 24, 618, "沿着海岸寻找它的来处……", 18, "#b7c9c4")
            body += text(x + 24, 676, "每张卡片下方是片名与简介；左右键切换，", 16, MUTED)
            body += text(x + 24, 704, "长按确定键可刷新元数据或删除。", 16, MUTED)
        elif index == 1:
            for row in range(3):
                yy = 326 + row * 143
                # Concentric corners: 20dp card - 12dp inset = 8dp inner radius.
                body += rect(x + 20, yy - 2, 440, 108, CARD_SOFT, 20)
                body += poster(x + 32, yy + 10, 145, 84, row + 2, label=False)
                body += text(x + 190, yy + 26, f"第 {row + 1} 集 · " + ["潮汐时刻", "风中的地图", "无人来信"][row], 19, weight=600)
                body += text(x + 190, yy + 58, "雾散之前，新的线索留在岸边的石阶上。", 16, "#b7c9c4")
        else:
            for cell in range(30):
                xx = x + 24 + (cell % 5) * 88
                yy = 322 + (cell // 5) * 70
                active = cell == 16
                body += rect(xx, yy, 74, 54, ACCENT if active else "#283b3d", 12)
                body += text(xx + 37, yy + 35, cell + 1, 21, "#19302b" if active else "#dce8e2", anchor="middle")
    body += footnote()
    return body


def handset_screen():
    body = brand()
    body += text(48, 150, "同一套界面，电视、手机与平板都适配", 36, weight=700)
    body += text(48, 190, "触摸设备使用单列滚动与更紧凑的 banner 留白；电视保留遥控器焦点与整屏切换", 19, MUTED)

    # Phone, portrait.
    px, py, pw, ph = 60, 214, 400, 604
    body += rect(px, py, pw, ph, "#0f1b1f", 34, 'stroke="#2c4448" stroke-width="2"')
    body += f'<defs><clipPath id="phoneclip">{rect(px + 2, py + 2, pw - 4, ph - 4, "white", 32)}</clipPath></defs>'
    body += text(px + 24, py + 42, "手机竖屏", 17, MUTED)
    body += f'<g clip-path="url(#phoneclip)">'
    body += rect(px + 24, py + 62, pw - 48, 44, "#1a2a2d", 22)
    for icon_index, icon in enumerate(["⌂", "▥", "⌕", "⚙"]):
        cx = px + 60 + icon_index * 74
        if icon_index == 0:
            body += f'<circle cx="{cx}" cy="{py + 84}" r="18" fill="{ACCENT_FILL}"/>'
        body += text(cx, py + 91, icon, 17, ACCENT if icon_index == 0 else "#c8d6d2", anchor="middle")
    body += text(px + 24, py + 152, "媒体库 / 纪录片", 13, ACCENT)
    body += text(px + 24, py + 188, "地球知识局", 26, weight=700)
    body += text(px + 24, py + 216, "155 个条目", 13, MUTED)
    body += button(px + 24, py + 234, "▶  播放", primary=True, width=110)
    body += button(px + 146, py + 234, "ⓘ", width=52)
    body += text(px + 24, py + 322, "向下查看更多媒体  ↓", 13, MUTED)
    body += rect(px + 24, py + 340, pw - 48, 2, "#243b3e", 0)
    body += text(px + 24, py + 376, "地球知识局 · 全部内容", 16, weight=600)
    body += text(px + 24, py + 400, "banner 与媒体区之间 40dp", 12, FAINT)
    for cell in range(4):
        xx = px + 24 + (cell % 2) * 176
        yy = py + 418 + (cell // 2) * 176
        body += poster(xx, yy, 160, 160, cell, label=False)
    body += "</g>"

    # Tablet, landscape.
    tx, ty, tw, th = 520, 214, 1020, 604
    body += rect(tx, ty, tw, th, "#0f1b1f", 30, 'stroke="#2c4448" stroke-width="2"')
    body += f'<defs><clipPath id="tabletclip">{rect(tx + 2, ty + 2, tw - 4, th - 4, "white", 28)}</clipPath></defs>'
    body += text(tx + 24, ty + 42, "平板横屏", 17, MUTED)
    body += f'<g clip-path="url(#tabletclip)">'
    body += f'<circle cx="{tx + 760}" cy="{ty + 200}" r="200" fill="#244540" opacity=".35" filter="url(#glow)"/>'
    body += text(tx + 24, ty + 110, "媒体库 / 纪录片", 13, ACCENT)
    body += text(tx + 24, ty + 148, "地球知识局", 30, weight=700)
    body += text(tx + 24, ty + 176, "155 个条目", 13, MUTED)
    body += text(tx + 24, ty + 214, "把地图、气候与城市讲成一条看得见的线。", 15, "#c6d4d0")
    body += button(tx + 24, ty + 234, "▶  播放", primary=True, width=110)
    body += button(tx + 146, ty + 234, "ⓘ  详情")
    body += poster(tx + 566, ty + 86, 208, 208, 2)
    for index in range(5):
        body += poster(tx + 788 + index * 44, ty + 86, 36, 208, index + 3, label=False)
    body += text(tx + 24, ty + 344, "地球知识局 · 全部内容   155 个条目", 16, weight=600)
    for index, icon in enumerate(["⇅", "cc", "▤", "▥"]):
        cx = tx + 424 + index * 54
        body += f'<circle cx="{cx}" cy="{ty + 338}" r="19" fill="#233335"/>'
        body += text(cx, ty + 345, icon, 15, ACCENT if index == 0 else "#dfe9e5", anchor="middle")
    for index in range(6):
        body += poster(tx + 24 + index * 164, ty + 372, 148, 200, index, ring=index == 0)
    body += "</g>"
    return body


CONTENT = {
    "home.svg": (home_screen(), "SunnyTV 首页界面示意"),
    "library.svg": (library_screen(), "SunnyTV 媒体库界面示意"),
    "episodes.svg": (episodes_screen(), "SunnyTV 三种单集排布示意"),
    "handset.svg": (handset_screen(), "SunnyTV 手机与平板界面示意"),
}

for name, (body, label) in CONTENT.items():
    (OUT / name).write_text(canvas(body + (footnote() if name != "handset.svg" else
                                           text(48, 866, "界面示意 · 手机与平板为触摸布局，电视为遥控器焦点布局", 15, FAINT)), label),
                            encoding="utf-8")

logo = (f'<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">'
        f'<defs><linearGradient id="gold" x2="1" y2="1">'
        f'<stop stop-color="#ffe99e"/><stop offset="1" stop-color="#f4a52d"/></linearGradient></defs>'
        f'{rect(0, 0, 128, 128, "#fff8ea", 28)}{sun(16, 16, 96)}</svg>')
(OUT / "sunnytv-logo.svg").write_text(logo, encoding="utf-8")

print("Generated 5 original SVG assets; no external images, fonts or network requests.")
