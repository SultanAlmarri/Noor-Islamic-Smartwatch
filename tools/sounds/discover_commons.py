#!/usr/bin/env python3
"""Lists every Adhan recording on Wikimedia Commons with the facts needed to judge it, for review by a person.

It only reads the Commons API (categories and file searches in several languages, then each file's licence, author,
description, date and length) and writes:
  candidates.json  one object per audio or video file
  candidates.md    the same as a table, freely licensed files first

Nothing is downloaded and nothing is added to the app by this script. A recording reaches the app only after review,
by being listed in adhan_sources.json, which fetch_commons.py then re-checks (licence, SHA-1) before downloading.
Requests carry a descriptive User-Agent, run one at a time with a pause, and respect 429/Retry-After.

usage: discover_commons.py <out_dir>
"""
import json
import os
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fetch_commons import API, get, plain  # noqa: E402  (same polite client and HTML cleaning)

CATEGORIES = ["Category:Adhan", "Category:Audio files of adhan", "Category:Videos of adhan", "Category:Ezan",
              "Category:Adhan in Turkey", "Category:Call to prayer", "Category:Muezzins", "Category:Azan"]
SEARCHES = ["adhan", "azan", "athan", "ezan", "ezanı", "call to prayer", "muezzin", "adzan", "azaan", "أذان", "اذان",
            "اذان مسجد", "اذان الفجر", "Gebetsruf", "appel à la prière", "llamada a la oración", "ezan sesi", "azon"]
FREE = ("CC0", "Public domain", "CC BY", "CC-BY", "PD", "GFDL")
PAUSE_S = 1.0


def api(params):
    params = dict(params, format="json", formatversion="2")
    time.sleep(PAUSE_S)
    return json.loads(get(f"{API}?{urllib.parse.urlencode(params)}"))


def members(cat, depth=1):
    out, subcats, cont = set(), [], {}
    while True:
        r = api(dict(action="query", list="categorymembers", cmtitle=cat, cmlimit="500", **cont))
        for m in r.get("query", {}).get("categorymembers", []):
            if m["ns"] == 6: out.add(m["title"])
            elif m["ns"] == 14: subcats.append(m["title"])
        if "continue" not in r: break
        cont = {"cmcontinue": r["continue"]["cmcontinue"]}
    if depth > 0:
        for s in subcats: out |= members(s, depth - 1)
    return out


def search(q, kind):
    out, cont = set(), {}
    while True:
        r = api(dict(action="query", list="search", srnamespace="6", srlimit="100", srsearch=f"{q} filetype:{kind}", **cont))
        for m in r.get("query", {}).get("search", []): out.add(m["title"])
        if "continue" not in r or len(out) >= 400: break
        cont = {"sroffset": r["continue"]["sroffset"]}
    return out


def details(titles):
    rows = []
    titles = sorted(titles)
    for i in range(0, len(titles), 40):
        batch = titles[i:i + 40]
        r = api(dict(action="query", prop="imageinfo|videoinfo", titles="|".join(batch),
                     iiprop="size|mime|extmetadata|url", viprop="size|mime|derivatives"))
        for p in r.get("query", {}).get("pages", []):
            ii = (p.get("imageinfo") or [{}])[0]
            mime = ii.get("mime", "")
            if not (mime.startswith("audio") or mime.startswith("video") or mime in ("application/ogg",)):
                continue
            meta = {k: v.get("value") for k, v in ii.get("extmetadata", {}).items()}
            vi = (p.get("videoinfo") or [{}])[0]
            rows.append(dict(
                title=p["title"], mime=mime, size_mb=round(ii.get("size", 0) / 1e6, 2),
                duration_s=round(float(ii.get("duration") or vi.get("duration") or 0), 1),
                licence=plain(meta.get("LicenseShortName")), artist=plain(meta.get("Artist"))[:120],
                credit=plain(meta.get("Credit"))[:160], date=plain(meta.get("DateTimeOriginal"))[:40],
                description=plain(meta.get("ImageDescription"))[:400], categories=plain(meta.get("Categories"))[:300],
                url=ii.get("descriptionurl", ""),
            ))
    return rows


def main(out):
    os.makedirs(out, exist_ok=True)
    titles = set()
    for c in CATEGORIES:
        try: titles |= members(c)
        except Exception as e: print(f"{c}: {e}", flush=True)
    for q in SEARCHES:
        for kind in ("audio", "video"):
            try: titles |= search(q, kind)
            except Exception as e: print(f"search {q}/{kind}: {e}", flush=True)
    print(f"{len(titles)} files to describe", flush=True)
    rows = details(titles)
    rows.sort(key=lambda r: (not r["licence"].startswith(FREE), -r["duration_s"]))
    json.dump(rows, open(os.path.join(out, "candidates.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    with open(os.path.join(out, "candidates.md"), "w", encoding="utf-8") as f:
        f.write(f"# Adhan recordings on Wikimedia Commons ({len(rows)} audio/video files)\n\n")
        f.write("| File | Licence | Length | Artist | Date | Description |\n|---|---|---|---|---|---|\n")
        for r in rows:
            f.write(f"| {r['title']} | {r['licence']} | {r['duration_s']} s | {r['artist']} | {r['date']} | {r['description'][:200]} |\n")
    print(f"wrote {len(rows)} candidates", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])
