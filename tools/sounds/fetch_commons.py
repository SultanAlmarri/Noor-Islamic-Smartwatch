#!/usr/bin/env python3
"""Fetches the recordings listed in adhan_sources.json (Adhans and sound effects) and checks each one.

Three kinds of source:
  "commons" (the default)  a Wikimedia Commons file. The Commons API gives its URL, SHA-1 and licence; the file is
                           refused if the live licence is not the one recorded in the manifest (so a licence change or
                           deletion on Commons is noticed, never silently ignored), and its SHA-1 is checked.
  "url"                    a file published by an archive with a stated licence (archive.org, the US National Park
                           Service). The manifest records the page, licence, author and the file's SHA-1, checked by
                           hand when the entry was added; the download must have exactly that SHA-1.
  "local"                  a recording supplied by the app's owner, kept in the repository under tools/sounds/owner/
                           ("path" is relative to the manifest's folder). The manifest records who made it, who holds
                           the rights and on what terms; the SHA-1 is checked when given.

Every file's audio is extracted losslessly to FLAC (video is dropped), and sources.json is written with the
attribution data the app shows.

Requests identify the project in the User-Agent, as Wikimedia's policy asks, and are made one at a time with a pause
between them. A 429 answer is respected (Retry-After) rather than worked around.

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
            with urllib.request.urlopen(req, timeout=300) as r:
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


def sha1_of(path):
    return hashlib.sha1(open(path, "rb").read()).hexdigest()


def download(url, raw, sha1):
    if os.path.exists(raw) and sha1_of(raw) == sha1:
        return
    time.sleep(PAUSE_S)
    data = get(url, binary=True)
    got = hashlib.sha1(data).hexdigest()
    if got != sha1:
        raise RuntimeError(f"SHA-1 mismatch: got {got}, expected {sha1}")
    open(raw, "wb").write(data)


def commons(entry, out):
    ii, meta = info(entry["title"])
    licence = (meta.get("LicenseShortName") or "").strip()
    if licence != entry["licence"]:
        raise RuntimeError(f"licence on Commons is '{licence}', manifest expects '{entry['licence']}'")
    ext = os.path.splitext(urllib.parse.urlparse(ii["url"]).path)[1].lower()
    raw = os.path.join(out, f"{entry['id']}.orig{ext}")
    download(ii["url"], raw, ii["sha1"])
    return raw, {
        "page": ii.get("descriptionurl"), "file_url": ii["url"], "sha1": ii["sha1"], "bytes": ii.get("size"),
        "mime": ii.get("mime"), "licence": licence, "licence_url": meta.get("LicenseUrl"),
        "artist": plain(meta.get("Artist")), "credit": plain(meta.get("Credit")),
        "attribution_required": meta.get("AttributionRequired"),
    }


def by_url(entry, out):
    ext = os.path.splitext(urllib.parse.urlparse(entry["url"]).path)[1].lower()
    raw = os.path.join(out, f"{entry['id']}.orig{ext}")
    download(entry["url"], raw, entry["sha1"])
    return raw, {"page": entry["page"], "file_url": entry["url"], "sha1": entry["sha1"], "bytes": os.path.getsize(raw),
                 "licence": entry["licence"], "licence_url": entry.get("licence_url"), "artist": entry["artist"]}


def local(entry, out, manifest_dir):
    raw = os.path.join(manifest_dir, entry["path"])
    if not os.path.isfile(raw):
        raise RuntimeError(f"{entry['path']}: not found (owner-supplied files live under tools/sounds/owner/)")
    sha1 = sha1_of(raw)
    if entry.get("sha1") and entry["sha1"] != sha1:
        raise RuntimeError(f"SHA-1 mismatch: got {sha1}, manifest says {entry['sha1']}")
    return raw, {"page": entry.get("page") or "", "file_url": "", "sha1": sha1, "bytes": os.path.getsize(raw),
                 "licence": entry["licence"], "licence_url": entry.get("licence_url"), "artist": entry["artist"]}


def main(manifest_path, out):
    os.makedirs(out, exist_ok=True)
    manifest = json.load(open(manifest_path, encoding="utf-8"))
    manifest_dir = os.path.dirname(os.path.abspath(manifest_path))
    results, failures = [], []
    for entry in manifest["files"]:
        fid, kind = entry["id"], entry.get("kind", "commons")
        print(f"{fid}: {entry.get('title') or entry.get('url') or entry.get('path')}", flush=True)
        try:
            if kind == "commons":
                raw, meta = commons(entry, out)
            elif kind == "url":
                raw, meta = by_url(entry, out)
            elif kind == "local":
                raw, meta = local(entry, out, manifest_dir)
            else:
                raise RuntimeError(f"unknown kind '{kind}'")
            flac = os.path.join(out, f"{fid}.flac")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", raw, "-vn", "-c:a", "flac", flac], check=True)
            if kind != "local" and os.path.splitext(raw)[1] in (".webm", ".ogv", ".mp4"):
                os.remove(raw)
            results.append(dict(id=fid, kind=kind, title=entry.get("title") or entry.get("name") or os.path.basename(raw),
                                provenance=entry.get("provenance"), **meta))
            print(f"  ok ({meta['licence']}, {meta.get('bytes')} bytes)", flush=True)
        except Exception as e:  # keep going so one bad file does not hide the others
            print(f"  FAILED: {e}", flush=True)
            failures.append({"id": fid, "error": str(e)})
        if kind != "local":
            time.sleep(PAUSE_S)
    json.dump({"fetched": results, "failed": failures}, open(os.path.join(out, "sources.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print(f"{len(results)} fetched, {len(failures)} failed")
    return 1 if failures and not results else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
