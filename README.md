# cantordust-ng (Ghidra)

![cantordust-ng Bitmap Visualization](./resources/examplePic2Tuple/bitmap_2tup.png)

![cantordust-ng Code Visualization](./resources/examplePic2Tuple/armv5l.png)

`cantordust-ng` is a performance-focused continuation of the CantorDust Ghidra plugin, tuned for smooth interactive scrubbing/playback and robust large-binary workflows.

CantorDust (then `..cantor.dust..`) was originally created by Chris Domas (xoreaxeaxeax), with funding from Battelle. The Ghidra plugin version of CantorDust was primarily developed by Battelle interns AJ Snedden and Mike Sengelmann with funding from Battelle.

Background and history: [Battelle blog post](https://inside.battelle.org/blog-details/battelle-publishes-open-source-binary-visualization-tool)

**Dependency:** Ghidra `9.1+`

## Preview

https://github.com/user-attachments/assets/19d1f0c2-99b0-4ed6-992f-09116acd209c

## What's New in cantordust-ng

The fork now includes all fixes and features tracked in [`CHANGELOG.md`](CHANGELOG.md). Highlights:

1. Data slider playback
1. Fixed ARGB1555 correctness
1. Persistent bookmarks
1. Hot path optimizations

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
