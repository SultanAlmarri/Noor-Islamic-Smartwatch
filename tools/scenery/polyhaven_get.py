"""Fetches scans from Poly Haven (https://polyhaven.com, all CC0) as its public API serves them: the .blend of an
asset with its textures folder, into <NoorTools>/assets/polyhaven/<id>/. Nothing fetched is ever committed.

    python3 tools/scenery/polyhaven_get.py <resolution, e.g. 2k> <id> [<id> ...]
    python3 tools/scenery/polyhaven_get.py 2k @canyon          the scans the Red Rock scenery uses (canyon.py)

Uses curl with the User-Agent the project fetches with (scenery-tools/1.0).
"""
import json
import os
import subprocess
import sys

TOOLS = os.environ.get('NOOR_TOOLS', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools'))
DST = os.environ.get('NOOR_POLYHAVEN', os.path.join(TOOLS, 'assets', 'polyhaven'))
SETS = {
    # (id, resolution or None for the one asked for)
    # Seen only from afar or small in the picture: 1k is finer than a pixel there, and a quarter of the memory.
    'canyon': [('rock_face', '1k'), ('cliff_side', '1k'), ('red_laterite_soil_stones', '1k'), ('red_mud_stones', '1k'),
               ('red_dirt_mud_01', '1k'), ('forest_leaves_02', '1k'), ('bark_willow_02', '1k'), ('bark_platanus', '1k'),
               ('sandstone_cracks', None), ('dry_riverbed_rock', None), ('river_small_rocks', None),
               ('namaqualand_boulder_02', '1k'), ('namaqualand_boulder_05', '1k'), ('namaqualand_boulders_01', '1k'),
               ('grass_medium_01', '1k'), ('dead_tree_trunk_02', '1k')],
}


def curl(url, path=None):
    cmd = ['curl', '-sS', '-L', '--fail', '--retry', '3', '-A', 'scenery-tools/1.0', url]
    if path:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        subprocess.run(cmd + ['-o', path], check=True)
        return None
    return subprocess.run(cmd, check=True, capture_output=True).stdout


def get(aid, res):
    files = json.loads(curl('https://api.polyhaven.com/files/' + aid))
    blends = files['blend']
    if res not in blends:
        res = sorted(blends, key=lambda k: int(k[:-1]))[0]
    b = blends[res]['blend']
    d = os.path.join(DST, aid)
    for root, _, names in os.walk(d):                 # (a folder holds one resolution: the scripts take what is there)
        for n in names:
            if ('_%s.' % res) not in n and any(('_%s.' % r) in n for r in ('1k', '2k', '4k', '8k')):
                os.remove(os.path.join(root, n))
    todo = [(b['url'], os.path.join(d, os.path.basename(b['url'])), b.get('size'))]
    for rel, inc in b.get('include', {}).items():
        todo.append((inc['url'], os.path.join(d, rel), inc.get('size')))
    got = 0
    for url, path, size in todo:
        if os.path.exists(path) and (size is None or os.path.getsize(path) == size):
            continue
        curl(url, path)
        got += 1
    print('%-28s %s  %d files (%d fetched), %.1f MB' % (aid, res, len(todo), got, sum(os.path.getsize(p) for _, p, _ in todo) / 1e6), flush=True)


def main():
    res = sys.argv[1]
    for a in sys.argv[2:]:
        if a.startswith('@'):
            for aid, r in SETS[a[1:]]:
                get(aid, r or res)
        else:
            get(a, res)


if __name__ == '__main__':
    main()
