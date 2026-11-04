"""A small Watch Face Format *simulator* used for automated tests and previews.

It parses the generated res/raw/watchface.xml, evaluates WFF arithmetic
expressions (own tokenizer/parser, WFF semantics as documented: round()
returns Integer, floor()/abs()/trig return Float, x/0 = 0, Java-style %),
resolves Condition/Compare/Default branches, user configurations, References
and AMBIENT variants, and returns the list of visible text / image / shape
items.  It can also render that list to HTML (for screenshots).

This is NOT the Wear OS renderer.  It verifies the logic encoded in the XML;
on-device behaviour must still be checked on the watch.
"""
import math, re
import xml.etree.ElementTree as ET


class Int(int):
    pass


# ---------------------------------------------------------------- tokenizer
TOK = re.compile(r"""\s*(?:
    (?P<num>\d+\.\d*|\.\d+|\d+)|
    (?P<str>"[^"]*")|
    (?P<src>\[[A-Za-z0-9_.]+\])|
    (?P<id>[A-Za-z_][A-Za-z0-9_]*)|
    (?P<op>&&|\|\||==|!=|<=|>=|[-+*/%()<>!?:,~&|])
)""", re.X)


def tokenize(s):
    pos, out = 0, []
    s = s.strip()
    while pos < len(s):
        m = TOK.match(s, pos)
        if not m or m.end() == pos:
            raise SyntaxError("bad token at %d in %r" % (pos, s[pos:pos + 30]))
        pos = m.end()
        for k in ("num", "str", "src", "id", "op"):
            if m.group(k) is not None:
                out.append((k, m.group(k)))
                break
    out.append(("end", None))
    return out


PREC = {"||": 1, "&&": 2, "|": 3, "&": 4, "==": 5, "!=": 5, "<": 6, "<=": 6, ">": 6, ">=": 6,
        "+": 7, "-": 7, "*": 8, "/": 8, "%": 8}


class Parser:
    def __init__(self, s):
        self.t = tokenize(s)
        self.i = 0

    def peek(self):
        return self.t[self.i]

    def next(self):
        tok = self.t[self.i]
        self.i += 1
        return tok

    def expect(self, v):
        tok = self.next()
        if tok[1] != v:
            raise SyntaxError("expected %s got %s" % (v, tok))

    def parse(self):
        e = self.ternary()
        if self.peek()[0] != "end":
            raise SyntaxError("trailing tokens: %s" % (self.t[self.i:self.i + 5],))
        return e

    def ternary(self):
        c = self.binary(1)
        if self.peek()[1] == "?":
            self.next()
            a = self.ternary()
            self.expect(":")
            b = self.ternary()
            return ("?", c, a, b)
        return c

    def binary(self, minp):
        left = self.unary()
        while True:
            k, v = self.peek()
            if k == "op" and v in PREC and PREC[v] >= minp:
                self.next()
                right = self.binary(PREC[v] + 1)
                left = ("bin", v, left, right)
            else:
                return left

    def unary(self):
        k, v = self.peek()
        if k == "op" and v in ("-", "+", "!", "~"):
            self.next()
            return ("un", v, self.unary())
        return self.primary()

    def primary(self):
        k, v = self.next()
        if k == "num":
            return ("num", float(v) if "." in v else Int(int(v)))
        if k == "str":
            return ("num", v[1:-1])
        if k == "src":
            return ("src", v[1:-1])
        if k == "id":
            if v == "null":
                return ("num", None)
            if self.peek()[1] == "(":
                self.next()
                args = []
                if self.peek()[1] != ")":
                    args.append(self.ternary())
                    while self.peek()[1] == ",":
                        self.next()
                        args.append(self.ternary())
                self.expect(")")
                return ("fn", v, args)
            raise SyntaxError("bare identifier %s" % v)
        if v == "(":
            e = self.ternary()
            self.expect(")")
            return e
        raise SyntaxError("unexpected %s %s" % (k, v))


_cache = {}


def parse(s):
    if s not in _cache:
        _cache[s] = Parser(s).parse()
    return _cache[s]


FLOAT32 = False
import struct as _struct


def f32(x):
    if FLOAT32 and isinstance(x, float) and not isinstance(x, bool):
        return _struct.unpack("f", _struct.pack("f", x))[0]
    return x


def _num(x):
    if isinstance(x, bool):
        return Int(1 if x else 0)
    return x


def ev(node, env):
    return f32(_ev(node, env))


def _ev(node, env):
    t = node[0]
    if t == "num":
        return node[1]
    if t == "src":
        name = node[1]
        if name not in env:
            raise KeyError("data source not provided: " + name)
        return env[name]
    if t == "un":
        v = ev(node[2], env)
        if node[1] == "-":
            return Int(-v) if isinstance(v, Int) else -v
        if node[1] == "+":
            return v
        if node[1] == "!":
            return not v
        if node[1] == "~":
            return Int(~int(v))
    if t == "?":
        return ev(node[2], env) if ev(node[1], env) else ev(node[3], env)
    if t == "bin":
        op = node[1]
        if op == "&&":
            return bool(ev(node[2], env)) and bool(ev(node[3], env))
        if op == "||":
            return bool(ev(node[2], env)) or bool(ev(node[3], env))
        a, b = _num(ev(node[2], env)), _num(ev(node[3], env))
        if op in ("==", "!="):
            r = (a == b)
            return r if op == "==" else not r
        both_int = isinstance(a, Int) and isinstance(b, Int)
        if op == "+":
            return Int(a + b) if both_int else float(a + b)
        if op == "-":
            return Int(a - b) if both_int else float(a - b)
        if op == "*":
            return Int(a * b) if both_int else float(a * b)
        if op == "/":
            if b == 0:
                return Int(0)
            r = a / b
            return Int(r) if both_int and float(r).is_integer() else float(r)
        if op == "%":
            r = math.fmod(a, b)
            return Int(r) if both_int else float(r)
        if op == "<": return a < b
        if op == "<=": return a <= b
        if op == ">": return a > b
        if op == ">=": return a >= b
        if op == "&": return Int(int(a) & int(b))
        if op == "|": return Int(int(a) | int(b))
    if t == "fn":
        name = node[1]
        args = [_num(ev(a, env)) for a in node[2]]
        if name == "round":
            return Int(math.floor(float(args[0]) + 0.5))
        if name == "floor":
            return float(math.floor(args[0]))
        if name == "ceil":
            return float(math.ceil(args[0]))
        if name == "abs":
            return float(abs(args[0]))
        if name == "clamp":
            return float(min(max(args[0], args[1]), args[2]))
        if name in ("sin", "cos", "tan", "asin", "acos", "atan", "sqrt"):
            return float(getattr(math, name)(args[0]))
        if name == "deg":
            return float(math.degrees(args[0]))
        if name == "rad":
            return float(math.radians(args[0]))
        if name == "subText":
            return str(args[0])[int(args[1]):int(args[2])]
        if name == "textLength":
            return Int(len(str(args[0])))
        raise NameError(name)
    raise ValueError(node)


def evaluate(expr, env):
    return ev(parse(expr), env)


def fmt(v):
    """How Template %s renders a value (Java String.valueOf)."""
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, Int):
        return str(int(v))
    if isinstance(v, float):
        return repr(v) if not v.is_integer() else "%.1f" % v
    return str(v)


# ------------------------------------------------------------ the simulator
class Sim:
    # Observed on the Galaxy Watch (v1.2.1 field report): a <Condition> whose
    # expression reads [REFERENCE.*] never follows the live value - the prayer
    # name stayed on its Fajr branch all day, while Template parameters reading
    # the same References updated every second.  The simulator reproduces that:
    # such a Compare is never taken.  (Set False to simulate the ideal platform.)
    DEVICE_CONDITIONS = True

    def __init__(self, xml_path):
        self.root = ET.parse(xml_path).getroot()
        self.scene = self.root.find("Scene")
        self.defaults = {}
        self.colors = {}
        uc = self.root.find("UserConfigurations")
        for c in uc:
            self.defaults[c.get("id")] = c.get("defaultValue")
            if c.tag == "ColorConfiguration":
                self.colors[c.get("id")] = {o.get("id"): o.get("colors").split() for o in c}

    def env(self, base, config=None, complications=None):
        e = dict(base)
        cfg = dict(self.defaults)
        cfg.update(config or {})
        for k, v in cfg.items():
            if k in self.colors:
                e["CONFIGURATION." + k] = self.colors[k][v][0]
            else:
                e["CONFIGURATION." + k] = v
        self.complications = complications or {}
        self.refresh(e)
        return e

    def refresh(self, e, only=None):
        """(Re)evaluate Reference carriers in document order."""
        for g in self.scene.iter():
            ref = g.find("Reference")
            if ref is None or (only and ref.get("name") not in only):
                continue
            tr = [t for t in g.findall("Transform") if t.get("target") == ref.get("source")]
            val = evaluate(tr[0].get("value"), e) if tr else float(g.get(ref.get("source")))
            e["REFERENCE." + ref.get("name")] = float(val)

    # -- traversal -------------------------------------------------------
    def visible(self, env, ambient=False):
        items = []
        self._walk(self.scene, env, 0.0, 0.0, 255, ambient, items, path="Scene")
        return items

    def _attr(self, node, name, env, ambient, default=None):
        v = node.get(name, default)
        for t in node.findall("Transform"):
            if t.get("target") == name:
                v = evaluate(t.get("value"), env)
        if ambient:
            for var in node.findall("Variant"):
                if var.get("mode") == "AMBIENT" and var.get("target") == name:
                    v = evaluate(var.get("value"), env)
        return v

    def _color(self, c, env):
        if c is None:
            return None
        if c.startswith("["):
            return evaluate(c, env)
        return c

    def _walk(self, node, env, ox, oy, alpha, ambient, items, path, sx=1.0, sy=1.0):
        """(ox, oy, sx, sy): affine map from this node's local coordinates to
        screen pixels (Group scaleX/scaleY about its pivot are applied)."""
        for ch in node:
            tag = ch.tag
            if tag in ("Transform", "Variant", "Reference", "Expressions", "BoundingBox",
                       "DefaultProviderPolicy", "Text", "Image", "Localization"):
                continue
            if tag == "Condition":
                names = {e.get("name"): e.text for e in ch.find("Expressions")}
                chosen = None
                for cmp_ in ch.findall("Compare"):
                    expr = names[cmp_.get("expression")]
                    if self.DEVICE_CONDITIONS and "[REFERENCE." in expr:
                        continue
                    if evaluate(expr, env):
                        chosen = cmp_
                        break
                if chosen is None:
                    chosen = ch.find("Default")
                if chosen is not None:
                    self._walk(chosen, env, ox, oy, alpha, ambient, items, path + "/Condition", sx, sy)
                continue
            if tag in ("Group", "PartText", "PartImage", "PartDraw", "ComplicationSlot"):
                a = float(self._attr(ch, "alpha", env, ambient, 255))
                a = alpha * a / 255.0
                lx = float(self._attr(ch, "x", env, ambient, 0))
                ly = float(self._attr(ch, "y", env, ambient, 0))
                w = float(ch.get("width", 0))
                h = float(ch.get("height", 0))
                x, y = ox + sx * lx, oy + sy * ly
                name = ch.get("name") or ch.get("slotId") or ""
                if tag == "Group":
                    gsx = float(self._attr(ch, "scaleX", env, ambient, 1.0))
                    gsy = float(self._attr(ch, "scaleY", env, ambient, 1.0))
                    px = float(ch.get("pivotX", 0.5)) * w
                    py = float(ch.get("pivotY", 0.5)) * h
                    nox = ox + sx * (lx + px * (1 - gsx))
                    noy = oy + sy * (ly + py * (1 - gsy))
                    self._walk(ch, env, nox, noy, a, ambient, items, path + "/" + name, sx * gsx, sy * gsy)
                elif tag == "ComplicationSlot":
                    comp = self.complications.get(ch.get("slotId"), {"type": "EMPTY"})
                    cenv = dict(env)
                    for k, v in comp.items():
                        cenv["COMPLICATION." + k] = v
                    for cx in ch.findall("Complication"):
                        if cx.get("type") == comp["type"]:
                            self._walk(cx, cenv, x, y, a, ambient, items, path + "/slot" + name, sx, sy)
                elif tag == "PartText":
                    text_el = ch.find("Text")
                    font = text_el.find("Font")
                    tmpl = font.find("Template")
                    if tmpl is not None:
                        params = [fmt(evaluate(p.get("expression"), env)) for p in tmpl.findall("Parameter")]
                        # the renderer uses the Template's text verbatim - whitespace included
                        s = (tmpl.text or "") + "".join((p.tail or "") for p in tmpl.findall("Parameter"))
                        for p in params:
                            s = s.replace("%s", p, 1)
                        s = s.replace("%%", "%")
                        text = s
                    else:
                        text = font.text or ""
                    items.append(dict(kind="text", x=x, y=y, w=w * sx, h=h * sy, alpha=a, text=text,
                                      family=font.get("family"), size=float(font.get("size")) * sy,
                                      color=self._color(font.get("color", "#FFFFFFFF"), env),
                                      align=text_el.get("align", "CENTER"),
                                      letterSpacing=float(font.get("letterSpacing") or 0),
                                      path=path, name=ch.get("name") or ""))
                elif tag == "PartImage":
                    items.append(dict(kind="image", x=x, y=y, w=w * sx, h=h * sy, alpha=a,
                                      resource=ch.find("Image").get("resource"),
                                      tint=self._color(ch.get("tintColor"), env), path=path))
                elif tag == "PartDraw":
                    for shape in ch:
                        if shape.tag in ("Rectangle", "RoundRectangle"):
                            sw = float(self._attr(shape, "width", env, ambient, shape.get("width")))
                            fill = shape.find("Fill")
                            stroke = shape.find("Stroke")
                            items.append(dict(kind="rect", x=x + sx * float(shape.get("x")), y=y + sy * float(shape.get("y")),
                                              w=sw * sx, h=float(shape.get("height")) * sy, alpha=a,
                                              radius=float(shape.get("cornerRadiusX", 0)) * sx,
                                              fill=self._color(fill.get("color"), env) if fill is not None else None,
                                              stroke=self._color(stroke.get("color"), env) if stroke is not None else None,
                                              thickness=float(stroke.get("thickness")) * sx if stroke is not None else 0,
                                              path=path))
                continue

    def texts(self, env, ambient=False):
        return [i["text"] for i in self.visible(env, ambient) if i["kind"] == "text" and i["alpha"] > 0]
