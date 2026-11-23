# Design specification: verified vs. approximated

Golden Gate Desktop is an **unofficial, independent** recreation of the *look and feel* of macOS 27
("Golden Gate") for Android. It contains no Apple code, artwork, icons, fonts, wallpapers or sounds.
Every icon, glyph, wallpaper, and sound effect is drawn or synthesized in code; text uses the
open-source Inter typeface (SIL OFL 1.1).

This document separates what has been **publicly reported** about macOS 27 from what we had to
**design ourselves**.

## Verified (publicly reported) characteristics

| Characteristic | Source |
|---|---|
| macOS 27 is named "Golden Gate"; announced at WWDC on June 8, 2026 | [The Verge](https://www.theverge.com/tech/943695/apple-wwdc-2026-macos-27-macbook-mac-announcement-features), [MacRumors roundup](https://www.macrumors.com/roundup/macos-27/) |
| Released to the public on September 14, 2026 | [9to5Mac](https://9to5mac.com/2026/09/14/macos-27-golden-gate-now-available-here-is-everything-new/) |
| Liquid Glass continues as the system material and gets a slider from **clear to tinted** | [MacRumors](https://www.macrumors.com/2026/06/09/macos-golden-gate-liquid-glass/), [Apple Support](https://support.apple.com/en-us/127257) |
| Glass gets darker edges and brighter specular highlights | [MacRumors](https://www.macrumors.com/2026/06/09/macos-golden-gate-liquid-glass/) |
| More uniform toolbars; edge-to-edge sidebars; colored sidebar icons | [MacRumors](https://www.macrumors.com/2026/06/09/macos-golden-gate-liquid-glass/), [Ars Technica](https://arstechnica.com/gadgets/2026/06/five-things-i-like-and-one-thing-i-still-want-in-the-macos-27-golden-gate-beta/) |
| Tighter window corner radii and more distinct active vs. inactive windows | [Ars Technica](https://arstechnica.com/gadgets/2026/06/five-things-i-like-and-one-thing-i-still-want-in-the-macos-27-golden-gate-beta/) |
| Layered "glass" app icons; glass traffic-light buttons; transparent menu bar | [9to5Mac](https://9to5mac.com/2026/09/14/macos-27-golden-gate-now-available-here-is-everything-new/), [Apple Newsroom (Tahoe 26 baseline)](https://www.apple.com/newsroom/2025/06/macos-tahoe-26-makes-the-mac-more-capable-productive-and-intelligent-than-ever/) |
| Liquid Glass background: translucent, refracts/reflects content, adapts to light/dark | [Wikipedia: Liquid Glass](https://en.wikipedia.org/wiki/Liquid_Glass), [Apple Newsroom](https://www.apple.com/newsroom/2025/06/macos-tahoe-26-makes-the-mac-more-capable-productive-and-intelligent-than-ever/) |
| macOS 27.2 adds glass brightness/volume HUD sliders | [9to5Mac](https://9to5mac.com/2026/09/14/macos-27-golden-gate-now-available-here-is-everything-new/) |

## Our approximations (not from Apple documentation)

* **Exact metrics.** Corner radii (windows 12 dp, menus 10 dp, notifications 20 dp, Dock 24 dp), blur radii,
  menu-bar height (26 dp), Dock tile size (50 dp default, 1.65× magnification), traffic-light size
  (12 dp). These were tuned by eye; Apple has not published values.
* **Refraction model.** An AGSL lens shader bends the backdrop inside a bevel near each rounded
  edge with slight chromatic dispersion. Apple's actual renderer is unknown.
* **Specular highlight & dark rim.** Drawn as gradient strokes whose intensity follows the
  clear↔tinted slider, with a fixed top-left light direction.
* **Spring parameters.** Damping and stiffness values were tuned by eye to feel similar.
* **Genie minimize.** Approximated by shrinking and translating the window toward its Dock tile,
  squeezing width faster than height. A true mesh warp would need custom GL and is not implemented.
* **Wallpapers.** Original procedural gradients ("Golden Hour", "Bay Fog", "Aurora", "Midnight Bloom", "Citrus", "Graphite", …),
  inspired by the release's San Francisco theme but not copies of any Apple wallpaper.
* **App icons.** Original layered vector icons with a glass bevel; the "System" menu uses an
  abstract emblem rather than any trademarked logo.
* **Sounds.** Synthesized at runtime (short sine/noise envelopes); none are Apple recordings.
* **Apps.** Functionality of Finder/Notes/Calendar/etc. is modelled on general desktop conventions,
  not reverse-engineered from macOS.
