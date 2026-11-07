"""Rough estimate of SkSL's ProgramSizeVisitor count: every expression node costs 1, a call costs the callee's whole
size, a for-loop's body is multiplied by its iteration count, other statements cost 1. Tokens stand in for nodes."""
import re, sys

BUILTINS = set("""abs sign floor ceil fract mod min max clamp mix step smoothstep length distance dot cross normalize
sin cos tan asin acos atan pow exp log exp2 log2 sqrt inversesqrt radians degrees float float2 float3 float4 half half2
half3 half4 int float2x2 float3x3 bool lutAt ridgeTex""".split())

def strip_comments(s):
    s = re.sub(r'//[^\n]*', '', s)
    return re.sub(r'/\*.*?\*/', '', s, flags=re.S)

TOKEN = re.compile(r'[A-Za-z_][A-Za-z0-9_]*|\d+\.\d*|\.\d+|\d+|==|<=|>=|!=|&&|\|\||\+\+|--|\+=|-=|\*=|/=|[-+*/<>=!?:(){}\[\];,.]')

def functions(src):
    out = {}
    for m in re.finditer(r'\b(?:float|float2|float3|float4|half4|int|void)\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*\{', src):
        name = m.group(1); i = m.end(); depth = 1
        while depth:
            if src[i] == '{': depth += 1
            elif src[i] == '}': depth -= 1
            i += 1
        out[name] = src[m.end():i - 1]
    return out

def size_of(body, funcs, memo, consts):
    toks = TOKEN.findall(body)
    return count(toks, 0, len(toks), funcs, memo, consts)

def count(toks, a, b, funcs, memo, consts):
    n = 0; i = a
    while i < b:
        t = toks[i]
        if t == 'for':
            # for ( int v = A ; v < B ; v ++ ) { body } or single statement
            j = i + 2; header = []
            depth = 1
            while depth:
                if toks[j] == '(': depth += 1
                elif toks[j] == ')': depth -= 1
                if depth: header.append(toks[j])
                j += 1
            h = ' '.join(header)
            m = re.match(r'int (\w+) = (\w+) ; \1 (<=|<|>=|>) (\S+) ; \1 (\+\+|--)', h)
            def val(x): return int(consts.get(x, x))
            if m:
                lo, op, hi = val(m.group(2)), m.group(3), val(m.group(4))
                iters = {'<': hi - lo, '<=': hi - lo + 1, '>=': lo - hi + 1, '>': lo - hi}[op]
            else:
                iters = 1; print('?? loop', h, file=sys.stderr)
            if toks[j] == '{':
                k = j + 1; depth = 1
                while depth:
                    if toks[k] == '{': depth += 1
                    elif toks[k] == '}': depth -= 1
                    k += 1
                body = count(toks, j + 1, k - 1, funcs, memo, consts)
                n += body * iters + 3
                i = k
            else:
                k = j
                while toks[k] != ';': k += 1
                n += count(toks, j, k + 1, funcs, memo, consts) * iters + 3
                i = k + 1
            continue
        if re.match(r'[A-Za-z_]', t) and i + 1 < b and toks[i + 1] == '(' and t in funcs:
            if t not in memo:
                memo[t] = 0
                memo[t] = size_of(funcs[t], funcs, memo, consts)
            n += memo[t]
        elif t not in ('{', '}', ';', ',', '(', ')', 'float', 'float2', 'float3', 'float4', 'int', 'const', 'return', 'if', 'else'):
            n += 1
        i += 1
    return n

if __name__ == '__main__':
    src = ''.join(strip_comments(open(f).read()) for f in sys.argv[1:])
    consts = {m.group(1): m.group(2) for m in re.finditer(r'const int (\w+) = (\d+);', src)}
    funcs = functions(src)
    memo = {}
    total = size_of(funcs['render'], funcs, memo, consts)
    top = sorted(((v, k) for k, v in memo.items()), reverse=True)[:8]
    print(f'render ≈ {total} nodes;', ', '.join(f'{k}={v}' for v, k in top))
