#!/usr/bin/env python3
"""Downloads the Adhan recordings listed in adhan_sources.json from Wikimedia Commons.

For every file it:
  1. asks the Commons API for the file's URL, SHA-1 and licence metadata;
  2. refuses the file if the live licence is not the one recorded in the manifest
     (so a licence change or deletion on Commons is noticed, never silently ignored);
  3. downloads the original upload and checks its SHA-1;
  4. extracts the audio losslessly to FLAC (videos are dropped, only the sound is kept);
  5. writes sources.json with the attribution data the app shows.

Requests identify the project in the User-Agent, as Wikimedia's policy asks, and are made
one at a time with a pause between them. A 429 answer is respected (Retry-After) rather than worked around.

usage: fetch_commons.py <manifest.json> <out_dir>
"""
import hashlib
import html
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "NoorIslamicSmartwatch-SoundFetcher/1.0 (https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch)"
PAUSE_S = 5
MAX_WAIT_S = 1800


def get(url, binary=False):
    waited = 0
    while True:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
                return data if binary else data.decode("utf-8")
        except urllib.error.HTTPError as e:
            if e.code != 429 or waited >= MAX_WAIT_S:
                raise
            wait = int(e.headers.get("Retry-After") or 60)
            wait = min(max(wait, 30), MAX_WAIT_S - waited)
            print(f"  429 from {urllib.parse.urlparse(url).netloc}; waiting {wait}s as asked", flush=True)
            time.sleep(wait)
            waited += wait


def plain(s):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", s or ""))).strip()


def info(title):
    q = urllib.parse.urlencode({
        "action": "query", "format": "json", "formatversion": "2", "titles": title,
        "prop": "imageinfo", "iiprop": "url|sha1|size|mime|extmetadata",
    })
    page = json.loads(get(f"{API}?{q}"))["query"]["pages"][0]
    if page.get("missing") or "imageinfo" not in page:
        raise SystemExit(f"{title}: not found on Commons (deleted or renamed?)")
    ii = page["imageinfo"][0]
    meta = {k: v.get("value") for k, v in ii.get("extmetadata", {}).items()}
    return ii, meta


def main(manifest_path, out):
    os.makedirs(out, exist_ok=True)
    manifest = json.load(open(manifest_path, encoding="utf-8"))
    results, failures = [], []
    for entry in manifest["files"]:
        fid, title = entry["id"], entry["title"]
        print(f"{fid}: {title}", flush=True)
        try:
            ii, meta = info(title)
            licence = (meta.get("LicenseShortName") or "").strip()
            if licence != entry["licence"]:
                raise RuntimeError(f"licence on Commons is '{licence}', manifest expects '{entry['licence']}'")
            ext = os.path.splitext(urllib.parse.urlparse(ii["url"]).path)[1].lower()
            raw = os.path.join(out, f"{fid}.orig{ext}")
            if not (os.path.exists(raw) and hashlib.sha1(open(raw, "rb").read()).hexdigest() == ii["sha1"]):
                time.sleep(PAUSE_S)
                data = get(ii["url"], binary=True)
                sha1 = hashlib.sha1(data).hexdigest()
                if sha1 != ii["sha1"]:
                    raise RuntimeError(f"SHA-1 mismatch: got {sha1}, Commons says {ii['sha1']}")
                open(raw, "wb").write(data)
            flac = os.path.join(out, f"{fid}.flac")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", raw, "-vn", "-c:a", "flac", flac], check=True)
            os.remove(raw) if ext in (".webm", ".ogv", ".mp4") else None
            results.append({
                "id": fid,
                "title": title,
                "page": ii.get("descriptionurl"),
                "file_url": ii["url"],
                "sha1": ii["sha1"],
                "bytes": ii.get("size"),
                "mime": ii.get("mime"),
                "licence": licence,
                "licence_url": meta.get("LicenseUrl"),
                "artist": plain(meta.get("Artist")),
                "credit": plain(meta.get("Credit")),
                "attribution_required": meta.get("AttributionRequired"),
                "provenance": entry.get("provenance"),
            })
            print(f"  ok ({licence}, {ii.get('size')} bytes)", flush=True)
        except Exception as e:  # keep going so one bad file does not hide the others
            print(f"  FAILED: {e}", flush=True)
            failures.append({"id": fid, "error": str(e)})
        time.sleep(PAUSE_S)
    json.dump({"fetched": results, "failed": failures}, open(os.path.join(out, "sources.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print(f"{len(results)} fetched, {len(failures)} failed")
    return 1 if failures and not results else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
