# Quota Vault wordmark (drafts)

Custom lettering for the Quota Vault name. Every letter is drawn from strokes on a grid in `glyphs.py`;
nothing is taken from an existing font, so the shapes are original to Quota Vault.

- `qv-a-*.svg`: A, Target Q (capitals; the Q holds the app icon's bullseye)
- `qv-b-*.svg`: B, Vault dial (one lowercase word; the "o" is a safe's combination dial)
- `qv-c-*.svg`: C, Vault door (QV vault-door mark with QUOTA over VAULT); `qv-c-mark-*` is the mark alone

Rebuild the files with `python3 export.py` (writes to `out/`). Colors: ink #22252B, coral #E4572E, cream #F6ECE8.

## Chosen: C, Vault door

Final files are in `../final/`: lockup (light and dark, SVG and 2400 px PNG), the mark alone (SVG, 512 and 1024 px PNG),
a 1080 px social profile picture, and the 1024×500 Play Store feature graphic (`feature-graphic.html` is its source).
The app's welcome screen uses `qv_lockup_light.xml` / `qv_lockup_dark.xml`, made with
`python3 to_vector.py out/qv-c-light.svg <dest> 280`.
