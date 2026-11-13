"""Serves the repository root for the sky bench (tools/sky3d/index.html), with caching off, and accepts PNG frames the
bench posts back (POST /save/<name>.png) into tools/sky3d/out/. Usage: python tools/sky3d/serve.py [port]"""
import http.server, os, sys, base64

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "tools", "sky3d", "out")

class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=ROOT, **k)
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()
    def do_POST(self):
        if not self.path.startswith("/save/"):
            self.send_error(404); return
        name = os.path.basename(self.path[6:])
        n = int(self.headers.get("Content-Length", "0"))
        data = self.rfile.read(n)
        if data.startswith(b"data:"):
            data = base64.b64decode(data.split(b",", 1)[1])
        os.makedirs(OUT, exist_ok=True)
        with open(os.path.join(OUT, name), "wb") as f:
            f.write(data)
        self.send_response(200); self.end_headers(); self.wfile.write(b"ok")
    def log_message(self, *a):
        pass

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    http.server.ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
