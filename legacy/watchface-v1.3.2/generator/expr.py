"""Tiny expression DSL.

The same expression tree is used for two things:
  * .wff()  -> emits Watch Face Format (WFF) arithmetic-expression text
  * .eval() -> evaluates numerically in Python (reference / tests)

Every binary operation is emitted fully parenthesised so WFF operator
precedence can never change the meaning.
"""
import math


def _wrap(v):
    if isinstance(v, Node):
        return v
    if isinstance(v, bool):
        return Num(1 if v else 0)
    if isinstance(v, (int, float)):
        return Num(v)
    if isinstance(v, str):
        return Str(v)
    raise TypeError(v)


def fmt_num(v):
    if isinstance(v, int):
        return str(v)
    if float(v).is_integer() and abs(v) < 1e9:
        return "%.1f" % v
    s = ("%.9f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    return s


class Node:
    # arithmetic
    def __add__(self, o): return Bin("+", self, _wrap(o))
    def __radd__(self, o): return Bin("+", _wrap(o), self)
    def __sub__(self, o): return Bin("-", self, _wrap(o))
    def __rsub__(self, o): return Bin("-", _wrap(o), self)
    def __mul__(self, o): return Bin("*", self, _wrap(o))
    def __rmul__(self, o): return Bin("*", _wrap(o), self)
    def __truediv__(self, o): return Bin("/", self, _wrap(o))
    def __rtruediv__(self, o): return Bin("/", _wrap(o), self)
    def __mod__(self, o): return Bin("%", self, _wrap(o))
    def __neg__(self): return Neg(self)
    # comparisons (named, to keep Python's == usable)
    def lt(self, o): return Bin("<", self, _wrap(o))
    def le(self, o): return Bin("<=", self, _wrap(o))
    def gt(self, o): return Bin(">", self, _wrap(o))
    def ge(self, o): return Bin(">=", self, _wrap(o))
    def eq(self, o): return Bin("==", self, _wrap(o))
    def ne(self, o): return Bin("!=", self, _wrap(o))
    def and_(self, o): return Bin("&&", self, _wrap(o))
    def or_(self, o): return Bin("||", self, _wrap(o))


class Num(Node):
    def __init__(self, v): self.v = v
    def wff(self):
        s = fmt_num(self.v)
        return "(" + s + ")" if s.startswith("-") else s
    def eval(self, env): return self.v


class Str(Node):
    def __init__(self, v): self.v = v
    def wff(self): return '"' + self.v + '"'
    def eval(self, env): return self.v


class Src(Node):
    """A WFF data source such as [YEAR] or [CONFIGURATION.city]."""
    def __init__(self, name): self.name = name
    def wff(self): return "[" + self.name + "]"
    def eval(self, env): return env[self.name]


class Neg(Node):
    def __init__(self, a): self.a = a
    def wff(self): return "(-" + self.a.wff() + ")"
    def eval(self, env): return -self.a.eval(env)


class Bin(Node):
    def __init__(self, op, a, b): self.op, self.a, self.b = op, a, b
    def wff(self): return "(" + self.a.wff() + " " + self.op + " " + self.b.wff() + ")"
    def eval(self, env):
        a = self.a.eval(env)
        b = self.b.eval(env)
        op = self.op
        if op == "+": return a + b
        if op == "-": return a - b
        if op == "*": return a * b
        if op == "/": return 0 if b == 0 else a / b
        if op == "%": return math.fmod(a, b)
        if op == "<": return a < b
        if op == "<=": return a <= b
        if op == ">": return a > b
        if op == ">=": return a >= b
        if op == "==": return a == b
        if op == "!=": return a != b
        if op == "&&": return bool(a) and bool(b)
        if op == "||": return bool(a) or bool(b)
        raise ValueError(op)


class Tern(Node):
    def __init__(self, c, a, b): self.c, self.a, self.b = _wrap(c), _wrap(a), _wrap(b)
    def wff(self): return "(" + self.c.wff() + " ? " + self.a.wff() + " : " + self.b.wff() + ")"
    def eval(self, env): return self.a.eval(env) if self.c.eval(env) else self.b.eval(env)


_FUN = {
    "sin": math.sin, "cos": math.cos, "tan": math.tan, "asin": math.asin,
    "acos": math.acos, "atan": math.atan, "sqrt": math.sqrt, "abs": abs,
    "floor": math.floor, "ceil": math.ceil, "deg": math.degrees, "rad": math.radians,
}


def _java_round(x):
    # Java Math.round: floor(x + 0.5)
    return int(math.floor(x + 0.5))


class Fn(Node):
    def __init__(self, name, *args): self.name, self.args = name, [_wrap(a) for a in args]
    def wff(self): return self.name + "(" + ", ".join(a.wff() for a in self.args) + ")"
    def eval(self, env):
        vals = [a.eval(env) for a in self.args]
        if self.name == "clamp":
            return min(max(vals[0], vals[1]), vals[2])
        if self.name == "round":
            return _java_round(vals[0])
        if self.name == "floor":
            return float(math.floor(vals[0]))
        return float(_FUN[self.name](*vals))


def sin(x): return Fn("sin", x)
def cos(x): return Fn("cos", x)
def acos(x): return Fn("acos", x)
def atan(x): return Fn("atan", x)
def sqrt(x): return Fn("sqrt", x)
def fabs(x): return Fn("abs", x)
def floor(x): return Fn("floor", x)
def jround(x): return Fn("round", x)
def clamp(x, lo, hi): return Fn("clamp", x, lo, hi)
def deg(x): return Fn("deg", x)
def rad(x): return Fn("rad", x)
def tern(c, a, b): return Tern(c, a, b)
def fmin(a, b): return clamp(a, -1.0e7, b)       # WFF has no min(); clamp does it
def fmax(a, b): return clamp(a, b, 1.0e7)        # WFF has no max()


def chain(src, mapping, default):
    """Nested ternary: src == key1 ? v1 : (src == key2 ? v2 : ... default)."""
    node = _wrap(default)
    for key, val in reversed(list(mapping.items())):
        node = Tern(Src(src).eq(Str(key)), _wrap(val), node)
    return node
