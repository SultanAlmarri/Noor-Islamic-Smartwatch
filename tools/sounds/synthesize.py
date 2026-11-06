#!/usr/bin/env python3
"""Generates Noor's alternative alert sounds from scratch (no samples, no third-party audio).
They are original works released by the project under CC0. Output: 44.1 kHz mono WAV, then encoded to Ogg Vorbis
by build_sounds.sh.  usage: synthesize.py <out_dir>"""
import numpy as np, sys, wave, os

SR = 44100
rng = np.random.default_rng(7)


def env(n, attack, release):
    e = np.ones(n)
    a = int(attack * SR); r = int(release * SR)
    if a: e[:a] = np.linspace(0, 1, a) ** 2
    if r: e[-r:] *= np.linspace(1, 0, r) ** 2
    return e


def bell(freq, dur, partials=((1, 1.0, 1.0), (2.76, 0.45, 1.6), (5.40, 0.25, 2.4), (8.93, 0.12, 3.5)), decay=1.4):
    t = np.arange(int(dur * SR)) / SR
    s = sum(a * np.sin(2 * np.pi * freq * m * t) * np.exp(-t * decay * d) for m, a, d in partials)
    return s * env(len(t), 0.004, 0.05)


def tick(dur=0.06, f=1800):
    t = np.arange(int(dur * SR)) / SR
    return (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 1.5 * t)) * np.exp(-t * 70) * env(len(t), 0.001, 0.01)


def chirp(f0, f1, dur, vib=0.0):
    t = np.arange(int(dur * SR)) / SR
    f = np.linspace(f0, f1, len(t)) * (1 + vib * np.sin(2 * np.pi * 30 * t))
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase) * np.sin(np.pi * t / dur) ** 2


def place(buf, sig, at):
    i = int(at * SR); j = min(len(buf), i + len(sig)); buf[i:j] += sig[: j - i]


def normalize(x, peak_db=-3.0):
    x = x - np.mean(x)
    return x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)


def save(path, x):
    x = np.clip(normalize(x), -1, 1)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())


def two_ticks():
    b = np.zeros(int(0.45 * SR)); place(b, tick(), 0.02); place(b, tick(), 0.22); return b


def soft_chime():
    b = np.zeros(int(3.0 * SR)); place(b, bell(880, 2.9, decay=1.6), 0.0); place(b, 0.6 * bell(1318.5, 2.6, decay=1.8), 0.12); return b


def subtle_bell():
    return bell(523.25, 3.5, decay=1.0)


def gentle_tone():
    t = np.arange(int(1.6 * SR)) / SR
    s = 0.7 * np.sin(2 * np.pi * 659.25 * t) + 0.3 * np.sin(2 * np.pi * 987.77 * t)
    return s * env(len(t), 0.08, 0.9)


def melodic_chimes():
    # A gentle phrase in a Hijaz colour (D Eb F# G A).
    notes = [293.66, 311.13, 369.99, 392.00, 440.00, 392.00, 369.99, 293.66]
    b = np.zeros(int(9.0 * SR))
    for i, f in enumerate(notes):
        place(b, 0.8 * bell(f * 2, 2.5, decay=1.2), 0.15 + i * 0.85)
    place(b, 0.5 * bell(293.66, 3.5, decay=0.8), 0.15 + len(notes) * 0.85 - 0.2)
    return b


def morning_birds():
    dur = 22.0
    b = np.zeros(int(dur * SR))
    t = 0.3
    while t < dur - 1.5:
        kind = rng.integers(0, 3)
        if kind == 0:   # quick trill
            for k in range(rng.integers(4, 9)):
                place(b, 0.5 * chirp(3800 + rng.normal(0, 150), 4600, 0.045), t + k * 0.055)
            t += 0.6
        elif kind == 1:  # rising whistle
            place(b, 0.7 * chirp(2200, 3400 + rng.normal(0, 200), 0.22, vib=0.01), t); t += 0.45
        else:            # two-note call
            place(b, 0.6 * chirp(3000, 2600, 0.12), t); place(b, 0.6 * chirp(3300, 2800, 0.12), t + 0.18); t += 0.5
        t += abs(rng.normal(0.9, 0.5))
    # a faint, low, distant air so the silence between calls is not dead
    noise = rng.normal(0, 1, len(b))
    k = np.ones(1500) / 1500
    amb = np.convolve(noise, k, mode='same')
    amb = amb / (np.max(np.abs(amb)) + 1e-9) * 0.03
    return b + amb * env(len(b), 1.5, 2.0)


def calm_ambience():
    dur = 30.0
    t = np.arange(int(dur * SR)) / SR
    chord = [146.83, 220.0, 293.66, 369.99]
    s = sum(np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 0.07 * t * (i + 1))) * (0.6 if i == 0 else 0.35)
            for i, f in enumerate(chord))
    s *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.05 * t)
    return s * env(len(t), 4.0, 6.0)


# 0.9.5: seven more. Each has its own random generator, so the seven sounds above are generated exactly as before.

def dawn_bells():
    # Three bells falling through a pentatonic phrase, the last left to ring.
    b = np.zeros(int(6.0 * SR))
    for i, f in enumerate([783.99, 659.25, 523.25]):
        place(b, (0.9 - 0.15 * i) * bell(f, 4.5 - i * 0.4, decay=0.9 + 0.2 * i), 0.05 + i * 0.7)
    return b


def wooden_knock():
    # A soft mallet on wood: a short, damped resonance with a hollow body, twice.
    def knock():
        t = np.arange(int(0.25 * SR)) / SR
        s = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, a, d in ((420, 1.0, 38), (1130, 0.45, 60), (2210, 0.2, 90)))
        return s * env(len(t), 0.0015, 0.02)
    b = np.zeros(int(0.9 * SR)); place(b, knock(), 0.02); place(b, 0.8 * knock(), 0.30); return b


def rising_tones():
    # Three soft tones rising (G, B, D), each blooming and fading.
    b = np.zeros(int(3.6 * SR))
    for i, f in enumerate([392.00, 493.88, 587.33]):
        t = np.arange(int(1.6 * SR)) / SR
        tone = (0.75 * np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t)) * env(len(t), 0.12, 1.1)
        place(b, tone, 0.1 + i * 0.75)
    return b


def water_drops():
    # Drops falling into still water: quick falling resonances, unevenly spaced.
    r = np.random.default_rng(11)
    b = np.zeros(int(4.0 * SR)); t0 = 0.1
    while t0 < 3.4:
        f = 900 + r.random() * 900
        t = np.arange(int(0.18 * SR)) / SR
        drop = np.sin(2 * np.pi * (f * t + 1800 * t * t)) * np.exp(-t * 32) * env(len(t), 0.001, 0.02)
        place(b, (0.5 + 0.5 * r.random()) * drop, t0)
        t0 += 0.18 + r.random() * 0.45
    return b


def wind_chimes():
    # Chimes stirred by a light wind: tubes in a pentatonic scale, struck now and then, ringing over each other.
    r = np.random.default_rng(23)
    scale = [1046.50, 1174.66, 1318.51, 1567.98, 1760.00, 2093.00]
    b = np.zeros(int(12.0 * SR)); t0 = 0.1
    while t0 < 10.5:
        f = scale[r.integers(0, len(scale))]
        place(b, (0.35 + 0.4 * r.random()) * bell(f, 2.6, partials=((1, 1.0, 1.0), (2.76, 0.30, 1.8), (5.40, 0.12, 2.6)), decay=1.1), t0)
        t0 += 0.15 + r.random() * 0.7
    return b * env(len(b), 0.5, 1.5)


def singing_bowl():
    # A struck bowl: low, inharmonic partials that beat slowly as they fade.
    t = np.arange(int(8.0 * SR)) / SR
    parts = ((220.0, 1.0, 0.35), (221.2, 0.6, 0.38), (603.0, 0.45, 0.6), (1157.0, 0.22, 0.9), (1866.0, 0.10, 1.3))
    s = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, a, d in parts)
    return s * env(len(t), 0.01, 1.0)


def soft_pulse():
    # Two gentle pulses, a pause, two more: noticeable without being sharp.
    def pulse():
        t = np.arange(int(0.22 * SR)) / SR
        return (np.sin(2 * np.pi * 740 * t) + 0.3 * np.sin(2 * np.pi * 1480 * t)) * env(len(t), 0.03, 0.12)
    b = np.zeros(int(1.9 * SR))
    for at in (0.05, 0.35, 1.05, 1.35):
        place(b, pulse(), at)
    return b


SOUNDS = {
    'alt_two_ticks': two_ticks, 'alt_soft_chime': soft_chime, 'alt_subtle_bell': subtle_bell, 'alt_gentle_tone': gentle_tone,
    'alt_melodic_chimes': melodic_chimes, 'alt_morning_birds': morning_birds, 'alt_calm_ambience': calm_ambience,
    'alt_dawn_bells': dawn_bells, 'alt_wooden_knock': wooden_knock, 'alt_rising_tones': rising_tones, 'alt_water_drops': water_drops,
    'alt_wind_chimes': wind_chimes, 'alt_singing_bowl': singing_bowl, 'alt_soft_pulse': soft_pulse,
}

if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    for name, fn in SOUNDS.items():
        save(os.path.join(out, name + '.wav'), fn())
        print(name)
