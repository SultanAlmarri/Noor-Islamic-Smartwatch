"""The parts of a cumulonimbus that are our own, as volumes (OpenVDB grids): the anvil, the storm's dark base, the
rain under it and the cap of smooth cloud over a rising tower. The towers themselves are CGHEVEN's towering
cumulus, stacked (sky_cb.py).

What photographs of real anvils show, and what is built here:

- an anvil is not a sheet: it is a lens, kilometres deep where the towers feed it (they flare out into it like the
  bell of a trumpet) and thinning to a blade at its rim;
- its top is one long clean line, a little domed, with the overshooting top standing over the updraught;
- the ice is combed out along the wind: fibres that fan out from the updraught and then run side by side, dense
  ones and nearly empty ones next to each other, in layers; downwind they end in tongues of different lengths;
- upwind the rim is thick, short and lumpy (the back-sheared edge); downwind it is thin and torn;
- the underside is soft: the ice thins out downwards over hundreds of metres, more so far from the towers.

Everything is in metres; x is downwind, z = 0 is the storm's cloud base. Only numpy and Blender's openvdb are used.
"""
import math

import numpy as np

import cloudgen


def field(shape, size, rng):
    """A smooth random field, mean 0 and unit spread, whose features are size[a] voxels across along each axis (so
    it may be drawn out along one of them). It wraps round at the edges."""
    f = np.fft.rfftn(rng.standard_normal(shape).astype(np.float32))
    k2 = 0.0
    for a, n in enumerate(shape):
        k = np.fft.rfftfreq(n) if a == len(shape) - 1 else np.fft.fftfreq(n)
        sl = [None] * len(shape)
        sl[a] = slice(None)
        k2 = k2 + (k[tuple(sl)] * (math.pi * max(size[a], 1.0))) ** 2
    f *= np.exp(-0.5 * k2)
    out = np.fft.irfftn(f, s=shape).astype(np.float32)
    return out / (out.std() + 1e-9)


def fbm(shape, size, rng, octaves=3, gain=0.5):
    out = np.zeros(shape, np.float32)
    a = 1.0
    for o in range(octaves):
        out += a * field(shape, [s / 2.0 ** o for s in size], rng)
        a *= gain
    return out / (out.std() + 1e-9)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def anvil(path, seed, down=18000.0, up=5500.0, half=9000.0, top=10300.0, core=3600.0, sheet=1500.0, dome=800.0,
          flare=4500.0, droop=450.0, torn=1.0, holes=0.0, voxel=100.0, zvoxel=50.0):
    """Writes an anvil. `down`, `up`: how far it reaches downwind and upwind of the updraught (x = y = 0); `half`:
    half its width across the wind; `top`: the height of its top over the cloud base; `core`: how deep it is where
    the towers feed it, over and above `sheet`, its depth as a sheet; `dome`: the height of the overshooting top
    (0: none, an old anvil); `flare`: how wide the bell is that the towers flare out into; `torn`: how strongly the
    ice lies in fibres; `holes`: how much of an old anvil has thinned away in patches (0..1)."""
    rng = np.random.default_rng(seed)
    v, zv = voxel, zvoxel
    x = np.arange(-(1.35 * up + 1500.0), 1.55 * down + 1500.0, v, dtype=np.float32)
    y = np.arange(-(1.45 * half + 1500.0), 1.45 * half + 1500.0, v, dtype=np.float32)
    z = np.arange(top - core - sheet - 900.0, top + dome + 500.0, zv, dtype=np.float32)
    nz, ny, nx = len(z), len(y), len(x)
    X, Y = x[None, :], y[:, None]
    # The streamline a place lies on: the ice fans out from the updraught and then runs side by side downwind, and
    # it wanders a little on its way (ruled straight, the fibres were the ribs of a paper fan).
    g = np.where(X < 0, np.sqrt(np.clip(1.0 - (X / (1.3 * up)) ** 2, 0.05, 1.0)), 1.0)
    wander = cloudgen.fbm2((ny, nx), v, 6500.0, rng, octaves=3)
    s = Y / (half * g) + 0.10 * wander * smooth(-0.3 * up, 0.4 * down, X)
    SR, ns, by = 1.7, 420, 10                     # s is kept between -SR and SR, in ns steps; x is drawn out `by` times
    nu = nx // by + 3
    ds = 2.0 * SR * half / ns                     # metres to a step of s
    sf = np.clip((s + SR) / (2.0 * SR) * (ns - 1), 0.0, ns - 1.001)
    s0 = sf.astype(np.int32)
    fs = (sf - s0).astype(np.float32)

    def fan(n):
        """A pattern laid along the streamlines: n is (.., ns, nu), the result (.., ny, nx)."""
        uf = np.arange(nx, dtype=np.float32) / by
        u0 = uf.astype(np.int32)[None, :]
        fu = (uf - np.floor(uf))[None, :]
        a = n[..., s0, u0] * (1.0 - fs) + n[..., s0 + 1, u0] * fs
        b = n[..., s0, u0 + 1] * (1.0 - fs) + n[..., s0 + 1, u0 + 1] * fs
        return (a * (1.0 - fu) + b * fu).astype(np.float32)

    # Fibres: 900 m across and 9 km long; finer ones 320 m across and 5 km long. They stand through the whole depth of
    # the anvil: seen from the side many lie behind one another and its body is smooth, and only its thin rim and
    # tip show them. (Fibres that changed from level to level made it a pile of plates.)
    f1 = fan(fbm((ns, nu), (900.0 / ds, 9000.0 / (v * by)), rng, octaves=2))[None]
    f2 = fan(field((ns, nu), (320.0 / ds, 5000.0 / (v * by)), rng))[None]
    # Tongues: every streamline reaches its own distance downwind, and every level too (seen from the side the tip
    # of an anvil is a few tongues of different lengths lying over one another, not one blade).
    reach = field((ns, 8), (0.16 * ns / SR, 4.0), rng)[:, :1] + 0.6 * field((ns, 8), (0.05 * ns / SR, 4.0), rng)[:, :1]
    reach = fan(np.repeat(reach, nu, axis=1))
    level = fbm((nz, ns), (750.0 / zv, 0.22 * ns / SR), rng, octaves=2)
    level = level[:, s0] * (1.0 - fs) + level[:, s0 + 1] * fs                         # (nz, ny, nx)
    rag = cloudgen.fbm2((ny, nx), v, 9000.0, rng, octaves=5, gain=0.6)
    swell = cloudgen.fbm2((ny, nx), v, 7000.0, rng, octaves=3)
    belly = cloudgen.fbm2((ny, nx), v, 4200.0, rng, octaves=3)
    a = np.where(X > 0, down * np.clip(1.0 + 0.36 * torn * reach, 0.45, 1.7), up)
    rho0 = np.sqrt((X / a) ** 2 + (Y / half) ** 2)
    rho = rho0 + 0.17 * rag * smooth(0.15, 0.8, rho0)
    far = smooth(0.0, 0.5 * down, X)                                    # 0 round the updraught and upwind, 1 downwind
    soft_edge = 0.09 + 0.26 * far
    edge = np.clip((1.0 - rho) / soft_edge, 0.0, 1.0)
    # 1 downwind; upwind the nose: a rounded prow, not a cliff (a quarter circle stood as a wall from 60 km).
    nose = np.clip(1.0 - np.clip(-X / up, 0.0, 1.0) ** 2, 0.0, 1.0) ** 0.7
    d2 = (X + 300.0) ** 2 + Y * Y
    lumps = np.sqrt(np.clip(1.0 - (cloudgen.cells2((ny, nx), v, 1500.0, rng) / 0.85) ** 2, 0.0, 1.0))
    small = np.sqrt(np.clip(1.0 - (cloudgen.cells2((ny, nx), v, 650.0, rng) / 0.85) ** 2, 0.0, 1.0))
    near = np.exp(-d2 / (2.0 * (1.2 * flare) ** 2))
    T = (top + dome * np.exp(-d2 / (2.0 * 1800.0 ** 2)) * (0.75 + 0.25 * lumps + 0.15 * small) + 120.0 * swell + 60.0 * belly * far
         - droop * np.clip(rho, 0.0, 1.3) ** 2 - 0.42 * (sheet + core) * (1.0 - nose)
         + (170.0 * lumps + 80.0 * small) * near * (1.0 - far))         # knuckles over and upwind of the towers
    # A wedge downwind (deep by the towers, a blade at the tip), swelling and sagging on its way; upwind the nose.
    th = (1.25 * sheet * np.clip(1.0 - rho, 0.0, 1.0) ** 0.7 * (0.70 + 0.30 * np.tanh(1.2 * belly + 0.5 * rag))
          + core * np.exp(-d2 / (2.0 * flare ** 2)))
    th *= edge ** 0.65 * nose
    soft_under = 220.0 + 520.0 * smooth(0.15, 1.0, rho)
    wisp = torn * (0.12 + 0.88 * smooth(0.25, 0.9, rho))                # the ice is nearly solid round the updraught
    Z = z[:, None, None]
    # Billows: by the towers and upwind the anvil is still cloud that boils (rounded heads over its top, round its
    # nose and hanging under it); downwind it calms into smooth, combed ice. Without them it was a glass lens.
    boil = (0.22 + 0.78 * (1.0 - far)) * np.clip(th / (0.5 * sheet), 0.0, 1.0)
    heads = cloudgen.domes((nz, ny, nx), v, 1700.0, rng) - 0.5
    heads += 0.45 * (cloudgen.domes((nz, ny, nx), v, 800.0, rng) - 0.5)
    dens = np.clip((Z - (T - th)[None] - (0.30 * th * wisp)[None] * f1 + (260.0 * boil)[None] * heads) / soft_under[None], 0.0, 1.0)
    dens *= np.clip(((T + 70.0 * wisp * far)[None] + (60.0 * wisp)[None] * f2 + (330.0 * boil)[None] * heads - Z) / (1.6 * zv), 0.0, 1.0)
    rel = 0.30 + 0.70 * np.clip((1.0 - rho) / 0.55, 0.0, 1.0)
    if holes > 0.0:
        patch = cloudgen.fbm2((ny, nx), v, 6000.0, rng, octaves=4)
        rel = rel * (1.0 - holes * smooth(0.1, 1.1, patch + 0.9 * rho0 - 0.3))
    dens *= rel[None]
    rim = np.clip((1.0 - rho[None] - (0.17 * torn * far)[None] * level + (0.05 * (1.0 - far))[None] * heads) / soft_edge[None], 0.0, 1.0)
    del level, heads
    # (Thin at the rim: at a tenth of its density a kilometre of this ice is still opaque, and the rim was a cut edge.)
    dens *= rim ** (0.6 + 1.9 * far)[None]
    del rim
    dens *= np.clip(1.0 + wisp[None] * (0.60 * f1 + 0.45 * f2), 0.12, 2.0)
    cloudgen.write_vdb(path, dens.astype(np.float32), (float(x[0]), float(y[0]), float(z[0])), v, zv)
    return {'x': [float(x[0]), float(x[-1])], 'y': [float(y[0]), float(y[-1])], 'z': [float(z[0]), float(z[-1])], 'shape': [nz, ny, nx]}


def shelf(path, seed, ax=10000.0, ay=7800.0, thick=520.0, voxel=120.0, zvoxel=30.0):
    """The storm's own base: a dark, flat, ragged shelf of dense cloud that the towers stand on (what makes a
    thunderhead's foot heavy and level). ax, ay: half its length along x and its width."""
    rng = np.random.default_rng(seed)
    v, zv = voxel, zvoxel
    x = np.arange(-1.3 * ax, 1.3 * ax, v, dtype=np.float32)[None, :]
    y = np.arange(-1.3 * ay, 1.3 * ay, v, dtype=np.float32)[:, None]
    shape = (y.shape[0], x.shape[1])
    rho = np.sqrt((x / ax) ** 2 + (y / ay) ** 2) + 0.22 * cloudgen.fbm2(shape, v, 5000.0, rng, octaves=5)
    m = np.clip((1.0 - rho) / 0.3, 0.0, 1.0)
    low = cloudgen.fbm2(shape, v, 1800.0, rng, octaves=4)
    dens, z0 = cloudgen.layer(-110.0 * m - 80.0 * low * m, thick * m - 1.0, v, zv, soft=2.0)
    cloudgen.write_vdb(path, dens, (float(x[0, 0]), float(y[0, 0]), z0), v, zv)


def rain(path, seed, ax=4200.0, ay=3600.0, depth=2200.0, lean=0.45, reach=0.75, voxel=110.0, zvoxel=55.0):
    """A veil of rain under the storm's base (z = 0) down towards the ground (z = -depth): shafts drawn out in
    streaks that lean with the wind (`lean` metres along x for every metre fallen), densest under the base and
    thinning as they fall; only some of them reach the ground (`reach`: the share that do)."""
    rng = np.random.default_rng(seed)
    v, zv = voxel, zvoxel
    wide = ax * 1.45 + depth * abs(lean) + 4.0 * v
    x = np.arange(-wide, wide, v, dtype=np.float32)[None, :]
    y = np.arange(-1.45 * ay, 1.45 * ay, v, dtype=np.float32)[:, None]
    shape = (y.shape[0], x.shape[1])
    rag = cloudgen.fbm2(shape, v, 3500.0, rng, octaves=4)
    core = smooth(0.0, 0.75, 1.0 - np.sqrt((x / ax) ** 2 + (y / ay) ** 2) - 0.30 * rag)
    streaks = np.clip(0.55 + 0.55 * cloudgen.fbm2(shape, v, 1100.0, rng, octaves=3) + 0.25 * cloudgen.fbm2(shape, v, 380.0, rng, octaves=2), 0.0, 1.6)
    falls = depth * np.clip(reach + 0.45 + 0.55 * cloudgen.fbm2(shape, v, 1600.0, rng, octaves=3), 0.25, 1.6)      # how far each streak falls
    z = np.arange(-depth, 2.0 * zv, zv, dtype=np.float32)
    dens = np.zeros((len(z),) + shape, np.float32)
    plan = core * streaks
    for k, zz in enumerate(z):
        fallen = max(-float(zz), 0.0)
        shift = lean * fallen / v
        i0 = int(math.floor(shift))
        f = shift - i0

        def moved(a):
            return np.roll(a, i0, axis=1) * (1.0 - f) + np.roll(a, i0 + 1, axis=1) * f

        p, fl = moved(plan), moved(falls)
        d = p * np.clip((fl - fallen) / (0.35 * depth), 0.0, 1.0) * (1.0 - 0.45 * fallen / depth)
        dens[k] = d * np.clip((2.0 * zv - zz) / (2.0 * zv), 0.0, 1.0)
    cloudgen.write_vdb(path, dens, (float(x[0, 0]), float(y[0, 0]), float(z[0])), v, zv)


def cap(path, seed, radius=2600.0, rise=420.0, thick=110.0, voxel=60.0, zvoxel=20.0):
    """A cap cloud (pileus): the thin smooth hood of ice that forms over the head of a fast-rising tower. z = 0 is
    the level of its rim; it arches `rise` metres over its middle."""
    rng = np.random.default_rng(seed)
    v, zv = voxel, zvoxel
    x = np.arange(-1.25 * radius, 1.25 * radius, v, dtype=np.float32)[None, :]
    y = np.arange(-1.25 * radius, 1.25 * radius, v, dtype=np.float32)[:, None]
    shape = (y.shape[0], x.shape[1])
    r = np.sqrt(x * x + y * y) / radius + 0.10 * cloudgen.fbm2(shape, v, 2200.0, rng, octaves=3)
    m = np.clip((1.0 - r) / 0.45, 0.0, 1.0)
    mid = rise * np.cos(0.5 * math.pi * np.clip(r, 0.0, 1.0)) + 25.0 * cloudgen.fbm2(shape, v, 900.0, rng, octaves=3)
    dens, z0 = cloudgen.layer(mid - 0.5 * thick * m, mid + 0.5 * thick * m - 1.0, v, zv, soft=2.0)
    dens *= (m ** 0.7)[None]
    cloudgen.write_vdb(path, dens, (float(x[0, 0]), float(y[0, 0]), z0), v, zv)
