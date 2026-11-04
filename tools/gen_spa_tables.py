"""Regenerates core/prayer/.../astro/SpaTables.kt from pvlib.spa (pip install pvlib)."""
import pvlib.spa as s, os
out = ['// GENERATED from the NREL Solar Position Algorithm tables (Reda & Andreas, NREL/TP-560-34302),',
       '// as distributed in pvlib-python (pvlib.spa). Do not edit by hand; regenerate with tools/gen_spa_tables.py.',
       'package com.noor.prayer.astro', '', 'internal object SpaTables {']
def arr(name, a):
    rows = ',\n        '.join('doubleArrayOf(%s)' % ', '.join(repr(float(x)) for x in r) for r in a)
    out.append('    val %s: Array<DoubleArray> = arrayOf(\n        %s\n    )' % (name, rows))
for n in ['L0', 'L1', 'L2', 'L3', 'L4', 'L5', 'B0', 'B1', 'R0', 'R1', 'R2', 'R3', 'R4']:
    arr(n, getattr(s, n))
arr('NUT_Y', s.NUTATION_YTERM_ARRAY); arr('NUT_ABCD', s.NUTATION_ABCD_ARRAY)
out += ['    val L: Array<Array<DoubleArray>> = arrayOf(L0, L1, L2, L3, L4, L5)',
        '    val B: Array<Array<DoubleArray>> = arrayOf(B0, B1)',
        '    val R: Array<Array<DoubleArray>> = arrayOf(R0, R1, R2, R3, R4)', '}']
p = os.path.join(os.path.dirname(__file__), '..', 'core', 'prayer', 'src', 'main', 'kotlin', 'com', 'noor', 'prayer', 'astro', 'SpaTables.kt')
open(p, 'w').write('\n'.join(out) + '\n')
