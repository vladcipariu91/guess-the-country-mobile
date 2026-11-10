#!/usr/bin/env python3
"""Render a change summary manifest (JSON) into one self-contained HTML file.

Usage: render.py <manifest.json>   -> prints the output path (summaries/<date>-<slug>.html)

Text limits are enforced on purpose: the page is meant to be scanned, not read.
"""
import base64
import datetime
import html
import json
import os
import re
import sys

LAYERS = ["UI", "ViewModel", "Domain", "Data", "Platform"]
PLATFORMS = {"android": "Android", "ios": "iOS"}
TYPES = {"feature": "UI feature", "bug": "UI bug fix", "change": "Change"}
LIMITS = {"title": 60, "summary": 160, "why": 160, "item": 110, "caption": 60, "note": 70}
MAX_ITEMS = 6

errors = []


def check(text, kind, where):
    if text and len(text) > LIMITS[kind]:
        errors.append(f"{where}: {len(text)} chars, max {LIMITS[kind]}. Shorten it.")
    return text or ""


def items(manifest, key):
    values = manifest.get(key, [])
    if len(values) > MAX_ITEMS:
        errors.append(f"{key}: {len(values)} items, max {MAX_ITEMS}.")
    return [check(v, "item", f"{key}[{i}]") for i, v in enumerate(values)]


def image(path, root):
    full = path if os.path.isabs(path) else os.path.join(root, path)
    if not os.path.isfile(full):
        errors.append(f"screenshot not found: {path}")
        return ""
    with open(full, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def shots(value, root, where):
    # A screenshot is a path, or {"android": path, "ios": path} to show both platforms side by side.
    if not value:
        return []
    if isinstance(value, str):
        return [("", image(value, root))]
    unknown = [k for k in value if k not in PLATFORMS]
    if unknown:
        errors.append(f"{where}: unknown platform {unknown}, use {list(PLATFORMS)}")
    return [(PLATFORMS[k], image(value[k], root)) for k in PLATFORMS if value.get(k)]


def esc(text):
    # Escape, then allow `code` spans.
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", html.escape(text))


def main():
    manifest_path = sys.argv[1]
    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    with open(manifest_path) as f:
        m = json.load(f)

    kind = m.get("type", "change")
    if kind not in TYPES:
        errors.append(f"type must be one of {list(TYPES)}")
    title = check(m.get("title"), "title", "title")
    summary = check(m.get("summary"), "summary", "summary")
    why = check(m.get("why"), "why", "why")
    changes, checks, try_steps = items(m, "changes"), items(m, "checks"), items(m, "try")

    layer_notes = {}
    for layer in m.get("layers", []):
        if layer.get("name") not in LAYERS:
            errors.append(f"layer {layer.get('name')!r} must be one of {LAYERS}")
        layer_notes[layer.get("name")] = check(layer.get("note"), "note", f"layer {layer.get('name')}")

    screens = []
    for i, s in enumerate(m.get("screens", [])):
        caption = check(s.get("caption"), "caption", f"screens[{i}].caption")
        before = shots(s.get("before"), root, f"screens[{i}].before")
        after = shots(s.get("after"), root, f"screens[{i}].after")
        if kind == "bug" and not (before and after):
            errors.append(f"screens[{i}]: a bug fix needs both before and after")
        screens.append((caption, before, after))
    if kind in ("feature", "bug") and not screens:
        errors.append("UI features and bug fixes need at least one screen")

    if errors:
        print("Summary not rendered:\n- " + "\n- ".join(errors), file=sys.stderr)
        sys.exit(1)

    flow = "".join(
        f'<div class="layer {"on" if l in layer_notes else ""}"><b>{l}</b>'
        f'<span>{esc(layer_notes.get(l, "unchanged"))}</span></div>'
        + ('<div class="arrow">→</div>' if i < len(LAYERS) - 1 else "")
        for i, l in enumerate(LAYERS)
    )

    def phone(src, label):
        return f'<figure class="phone"><figcaption>{label}</figcaption><img src="{src}" alt="{label}"></figure>'

    screen_html = ""
    for caption, before, after in screens:
        def label(stage, platform):
            return f"{stage} · {platform}" if platform else stage

        pair = "".join(phone(src, label("Before", p)) for p, src in before)
        pair += "".join(phone(src, label("After" if before else "New", p)) for p, src in after)
        screen_html += f'<div class="screen"><h3>{esc(caption)}</h3><div class="pair">{pair}</div></div>'

    def ul(values, cls=""):
        return f'<ul class="{cls}">' + "".join(f"<li>{esc(v)}</li>" for v in values) + "</ul>" if values else ""

    date = datetime.date.today().isoformat()
    slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")[:50]
    out_dir = os.path.join(root, "summaries")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"{date}-{slug}.html")

    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<style>
:root {{ --bg:#f7f7f8; --card:#fff; --text:#1b1b1f; --muted:#5f6068; --line:#e3e3e8; --accent:#2f5bea; --accent-bg:#e8eeff; --ok:#1e7f4f; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#121214; --card:#1c1c20; --text:#ececf1; --muted:#9a9aa5; --line:#2c2c33; --accent:#8aa6ff; --accent-bg:#232b45; --ok:#5ed39a; }} }}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--bg); color:var(--text); font:15px/1.5 -apple-system, system-ui, sans-serif; }}
main {{ max-width:980px; margin:0 auto; padding:32px 16px 64px; }}
.badge {{ display:inline-block; font-size:12px; font-weight:600; letter-spacing:.04em; text-transform:uppercase; color:var(--accent); background:var(--accent-bg); padding:3px 10px; border-radius:99px; }}
h1 {{ font-size:28px; margin:10px 0 6px; }}
.summary {{ font-size:17px; margin:0; }}
.why {{ color:var(--muted); margin:4px 0 0; }}
h2 {{ font-size:13px; text-transform:uppercase; letter-spacing:.06em; color:var(--muted); margin:36px 0 12px; }}
h3 {{ font-size:15px; margin:0 0 12px; }}
.flow {{ display:flex; align-items:stretch; gap:8px; flex-wrap:wrap; }}
.layer {{ flex:1 1 150px; background:var(--card); border:1px solid var(--line); border-radius:12px; padding:10px 12px; opacity:.55; }}
.layer.on {{ opacity:1; border-color:var(--accent); box-shadow:0 0 0 1px var(--accent) inset; }}
.layer b {{ display:block; }} .layer span {{ font-size:13px; color:var(--muted); }}
.arrow {{ align-self:center; color:var(--muted); }}
.screen {{ background:var(--card); border:1px solid var(--line); border-radius:16px; padding:16px; margin-bottom:16px; }}
.pair {{ display:flex; gap:20px; flex-wrap:wrap; justify-content:center; }}
.phone {{ margin:0; text-align:center; }}
.phone figcaption {{ font-size:12px; font-weight:600; text-transform:uppercase; letter-spacing:.06em; color:var(--muted); margin-bottom:8px; }}
.phone img {{ width:min(280px, 80vw); border-radius:22px; border:6px solid #000; display:block; }}
.cols {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(260px, 1fr)); gap:16px; }}
.card {{ background:var(--card); border:1px solid var(--line); border-radius:12px; padding:4px 16px; }}
ul {{ padding-left:18px; }} li {{ margin:6px 0; }}
ul.checks {{ list-style:none; padding-left:0; }} ul.checks li::before {{ content:"✓ "; color:var(--ok); font-weight:700; }}
ol li {{ margin:6px 0; }}
code {{ font:13px ui-monospace, Menlo, monospace; background:var(--accent-bg); padding:1px 5px; border-radius:5px; }}
footer {{ margin-top:40px; color:var(--muted); font-size:12px; }}
</style></head>
<body><main>
<span class="badge">{TYPES[kind]}</span>
<h1>{esc(title)}</h1>
<p class="summary">{esc(summary)}</p>
{f'<p class="why">Why: {esc(why)}</p>' if why else ''}
<h2>Where it lives</h2>
<div class="flow">{flow}</div>
{f'<h2>Screens</h2>{screen_html}' if screen_html else ''}
<div class="cols">
{f'<section><h2>What changed</h2><div class="card">{ul(changes)}</div></section>' if changes else ''}
{f'<section><h2>Checks</h2><div class="card">{ul(checks, "checks")}</div></section>' if checks else ''}
</div>
{f'<h2>Try it</h2><div class="card"><ol>' + ''.join(f'<li>{esc(s)}</li>' for s in try_steps) + '</ol></div>' if try_steps else ''}
<footer>Generated {date}</footer>
</main></body></html>
"""
    with open(out, "w") as f:
        f.write(page)
    print(out)


if __name__ == "__main__":
    main()
