#!/usr/bin/env python3
"""Summarises `dumpsys gfxinfo <package> framestats` (tools/devicetest/smooth.sh).

For every frame Android records when each stage began (nanoseconds). From them:
  ui      the app's main thread: input, animation, measure/layout and recording the drawing
          (HandleInputStart -> SyncQueued; Compose's composition, layout and draw all happen here)
  render  the render thread up to the swap (SyncStart -> FrameCompleted); on an emulator this is mostly the software GPU
  total   IntendedVsync -> FrameCompleted, what the user waits for
A frame is late when its total passes the frame interval (16.7 ms at 60 Hz); the main-thread time is the part Minhad's
own code decides. Prints one line per scenario and appends machine-readable JSON to the given file.

usage: framestats.py <scenario name> <gfxinfo dump> <json out>
"""
import json
import sys


def frames(text):
    rows, head = [], None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("Flags,"):
            head = line.rstrip(",").split(",")
            continue
        if line.startswith("---PROFILEDATA---"):
            continue
        if head and line and line[0].isdigit():
            vals = line.rstrip(",").split(",")
            if len(vals) < len(head):
                continue
            r = dict(zip(head, (int(v) for v in vals[:len(head)])))
            if r.get("Flags", 0) != 0:   # frames the system marks (window changes, first frames) are not drawn normally
                continue
            rows.append(r)
    return rows


def pct(values, p):
    if not values:
        return 0.0
    v = sorted(values)
    return v[min(len(v) - 1, int(round(p / 100.0 * (len(v) - 1))))]


def main(name, dump, out):
    text = open(dump, encoding="utf-8", errors="replace").read()
    rows = frames(text)
    ms = lambda a, b, r: (r[b] - r[a]) / 1e6
    ui = [ms("HandleInputStart", "SyncQueued", r) for r in rows if r.get("SyncQueued", 0) > 0 and r.get("HandleInputStart", 0) > 0]
    render = [ms("SyncStart", "FrameCompleted", r) for r in rows if r.get("FrameCompleted", 0) > 0 and r.get("SyncStart", 0) > 0]
    total = [ms("IntendedVsync", "FrameCompleted", r) for r in rows if r.get("FrameCompleted", 0) > 0]
    interval = 16.67
    res = dict(scenario=name, frames=len(rows),
               ui_p50=round(pct(ui, 50), 2), ui_p90=round(pct(ui, 90), 2), ui_p99=round(pct(ui, 99), 2), ui_max=round(max(ui or [0]), 2),
               ui_over_8ms=sum(1 for x in ui if x > 8.0), ui_over_16ms=sum(1 for x in ui if x > interval),
               render_p50=round(pct(render, 50), 2), render_p90=round(pct(render, 90), 2),
               total_p50=round(pct(total, 50), 2), total_p90=round(pct(total, 90), 2),
               late=sum(1 for x in total if x > interval))
    # The system's own summary lines, for reference.
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("Janky frames:") and "legacy" not in line:
            res["system_janky"] = line.split(":", 1)[1].strip()
    print(f"{name:28s} frames {res['frames']:4d} | main thread ms p50 {res['ui_p50']:6.2f} p90 {res['ui_p90']:6.2f} "
          f"p99 {res['ui_p99']:7.2f} max {res['ui_max']:7.2f} | >16 ms {res['ui_over_16ms']:3d} | "
          f"render p50 {res['render_p50']:6.2f} | late {res['late']:4d}")
    with open(out, "a", encoding="utf-8") as f:
        f.write(json.dumps(res) + "\n")


if __name__ == "__main__":
    main(*sys.argv[1:4])
