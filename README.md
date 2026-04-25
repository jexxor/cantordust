# cantordust-ng (Ghidra)

![cantordust-ng Bitmap Visualization](./resources/examplePic2Tuple/bitmap_2tup.png)

![cantordust-ng Code Visualization](./resources/examplePic2Tuple/armv5l.png)

`cantordust-ng` is a performance-focused continuation of the CantorDust Ghidra plugin, tuned for smooth interactive scrubbing/playback and robust large-binary workflows.

CantorDust (then `..cantor.dust..`) was originally created by Chris Domas (xoreaxeaxeax), with funding from Battelle. The Ghidra plugin version of CantorDust was primarily developed by Battelle interns AJ Snedden and Mike Sengelmann with funding from Battelle.

Background and history: [Battelle blog post](https://inside.battelle.org/blog-details/battelle-publishes-open-source-binary-visualization-tool)

**Dependency:** Ghidra `9.1+`

## Preview

https://github.com/user-attachments/assets/19ff888f-957d-4b63-aad2-89ba43ee2ba7

## What's New in cantordust-ng

The fork now includes all fixes and features tracked in [`CHANGELOG.md`](CHANGELOG.md). Highlights:

- Main window is explicitly resizable/fullscreen-friendly, and the main visualization area scales correctly with window size.
- Metric Map scales with window resize (including click-to-address mapping), and Two Tuple shows coordinate popups as `(0xXX, 0xYY)`.
- Fast-scroll stability was hardened (slider bitmap rendering, Two Tuple cache bounds, serialized Metric Map redraws) to prevent exceptions and concurrent map corruption.
- Playback controls were added: `Play`, `Pause`, `Stop`, `Loop`, `Step`, `ms`, with selectable targets (`Absolute`, `Macro`, `Micro`).
- Data-window updates use a latest-wins queue, and bitmap/two-tuple/metric-map renderers coalesce background work to avoid unbounded render thread buildup.
- Playback responsiveness was tuned with hidden-view redraw suppression, render coalescing, and optimized update pipelines.
- Metric Map popup address/classifier lookup now aligns with currently zoomed rendered state (including classifier mode), not stale coordinates.
- Classifier rendering was hardened to tolerate null/uninitialized classifier state.
- Linear Bitmap width/offset sliders update live during drag (not only on release).
- Metric Map first-open render reliability was fixed.
- Sequence guide path was added to Metric Map, with enable/disable and trail length controls.
- ARGB1555 decoding in Metric Map was fixed (correct 5-bit scaling and bounds behavior).
- 24bpp/32bpp decoding now uses unsigned channels and safe clamping.
- 64bpp decoding was reworked for proper 16-bit downconversion and alpha-aware behavior.
- Classifier wavelength conversion bug (green mask extraction) was fixed.
- `ColorGradient` and `ColorClass` now use unsigned byte semantics (`0..255`), not signed Java byte behavior.
- Entropy shading was hardened for short windows and removed expensive per-pixel debug logging.
- Fragile string identity checks (`==`) were replaced with value checks (`equals`) for mode/type detection.
- Per-frame `ColorSpectrum` recreation was removed; spectrum mapping is stable and no longer remapped each frame during scrubbing.
- Noisy symbol-map debug logging was removed to avoid console I/O bottlenecks.
- Remaining index safety gaps were fixed in `Color8bpp` and `ColorSpectrum`.
- Classifier tail/partial block coverage was fixed; `classAtIndex` now uses explicit bounds clamping.
- Entropy window math near EOF was fixed; probability normalization now uses actual sampled byte count.
- Metric Map redraw coalescing now reuses one render executor instead of creating new threads per burst.
- Metric Map square rendering now writes directly to 1D raster buffers and reuses sampled colors when possible.
- Entropy internals now use fixed-size histograms instead of hash-map iteration in hot paths.
- 8bpp/24bpp/32bpp color paths removed unnecessary `java.awt.Color` allocations.
- Bitmap/TwoTuple/Slider/Data-window workers now use reusable single-thread executors to cut thread creation overhead.
- Two Tuple now writes ARGB directly to image buffers (instead of per-pixel `Graphics2D` fill calls).
- Spectrum and classifier coloring now use precomputed LUTs.
- Bitmap entropy mode reuses a persistent entropy source object.
- Two Tuple cache blocks moved from object-heavy tuple maps to dense `int[65536]` tables with touched-index reuse.
- Bitmap rendering keeps full-resolution frame buffers and scales at paint time (removed expensive per-frame `getScaledInstance(...)`).
- Metric Map drag responsiveness improved with more frequent stale-frame cancellation checks while preserving full-resolution rendering.
- Initial window sizing now uses packed content dimensions and safer minimums, preventing first-launch clipping of left/right controls.
- Metric Map playback now avoids rebuilding per-frame index hash maps and derives source offsets directly from map index math.
- Metric Map now renders through packed ARGB/DataBuffer pipelines with reduced conversion overhead.
- Playback path keeps full-resolution map rendering and skips guide-overlay regeneration during active playback for smoother animation.
- Metric Map now caches full-resolution curve points (`index -> x,y`) and source-offset mappings to reduce repeated per-frame curve math/object churn.
- Color sources now expose a direct ARGB fast path; Metric Map consumes it to avoid per-pixel `Rgb` allocation.
- Entropy shading was rewritten with reusable histogram buffers (no per-pixel histogram allocations).
- Runtime `Interpolation` toggle was added in Metric Map and Linear Bitmap popup menus.
- Metric Map now supports drag selection (left-drag with threshold) and reports selected area size (`width x height`, cells, estimated bytes) via popup.

## Installation and Setup

1. Clone this repository.
2. Install Ghidra `9.1+`.
   1. Download: [https://ghidra-sre.org](https://ghidra-sre.org/)
   2. Install guide: https://ghidra-sre.org/InstallationGuide.html
3. Open Ghidra and create/open a project.
4. In Script Manager, add the local `cantordust-ng` directory as a script directory.
5. Run `Cantordust.java` from Script Manager.

> You can also assign a key binding to `Cantordust.java`.

## Updating

1. Go to your local repository directory.
2. Run `git pull`.
3. Run `python cleanup.py`.
4. Relaunch in Ghidra.

If `cleanup.py` fails, see compilation notes below.

## Development Notes

### Ghidra Script API

In Ghidra, open `Help -> Ghidra API Help` for API docs.

Ghidra scripts must extend `GhidraScript`:

```java
import ghidra.app.script.GhidraScript;
public class Cantordust extends GhidraScript { }
```

`Cantordust.java` is the entrypoint that can directly use Ghidra APIs and console output.

### Script Compilation / Class Refresh

Ghidra scripts are not always recompiled automatically at runtime. To ensure code changes are picked up, remove stale generated `.class` files from your Ghidra scripts `bin` directory.

This repo includes `cleanup.py`, which reads `ghidra_bin_location.txt` and deletes generated classes.

Example Linux path:

```txt
/home/user/.ghidra/.ghidra_9.1_PUBLIC/dev/ghidra_scripts/bin/
```

If your environment writes the path file in a non-UTF-8 encoding, adjust decoding in `cleanup.py` accordingly.
