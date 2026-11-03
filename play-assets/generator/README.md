# Play Store asset generator

Regenerates every graphic in `play-assets/`:

```bash
node render.js
```

Requires Playwright with Chromium (already present in CI images).

## What it produces

| File | Size | Used for |
|---|---|---|
| `screenshot-01-protection-active.png` | 1080×1920 | Play phone screenshot |
| `screenshot-02-setup-step-1.png` | 1080×1920 | Play phone screenshot |
| `screenshot-03-setup-step-3.png` | 1080×1920 | Play phone screenshot |
| `screenshot-04-alarm-triggered.png` | 1080×1920 | Play phone screenshot |
| `feature-graphic-1024x500.png` | 1024×500 | Play feature graphic |
| `play-store-icon-512.png` | 512×512 | Play app icon (made by the icon script) |

## How the screenshots stay faithful

`screens.html` is authored at **1dp = 1px** and captured at
`deviceScaleFactor: 3`, which is exactly an xxhdpi phone
(360×640dp → 1080×1920px). Every colour, size, margin and string is copied
from the real sources:

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_stop_alarm.xml`
- `app/src/main/res/drawable/circle_bg.xml`, `circle_red_bg.xml`
- the status texts in `MainActivity.updateUI()`

If any of those change, update `screens.html` and re-run, so the store
never shows a screen the app does not actually display.

**The ad banner is deliberately omitted** — Google Play discourages
showing ads in store screenshots.

> Screenshots taken on a real device are always preferable. These exist so
> the listing can be completed without one, and because they reproduce the
> real UI exactly.

## Font

`heebo.woff2` is Heebo, licensed under the SIL Open Font License 1.1,
embedded as base64 in `font.css` so rendering needs no network access.
