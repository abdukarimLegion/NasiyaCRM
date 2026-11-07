#!/usr/bin/env python3
"""
Soxta MIB serveri — faqat `APP_MIB_PROVIDER=http` adapterini sinash uchun (haqiqiy MIB emas).

  python3 deploy/mib-mock/mock_mib.py            # 127.0.0.1:9090
  python3 deploy/mib-mock/mock_mib.py 9091 tok   # port va token (token bo'lsa Bearer talab qilinadi)

GET /api/debts/{pinfl}:
  JShShIR oxirgi raqami 9 -> qarz bor (2 ta ijro ishi)
  oxirgi raqami 4         -> 404 (ijro ishi yo'q)
  oxirgi raqami 5         -> 500 (xizmat xatosi)
  oxirgi raqami 7         -> sekin javob (15 soniya, timeout sinovi)
  boshqalari              -> qarz yo'q
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9090
TOKEN = sys.argv[2] if len(sys.argv) > 2 else None


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if TOKEN and self.headers.get("Authorization") != f"Bearer {TOKEN}":
            return self._send(401, {"error": "unauthorized"})
        if not self.path.startswith("/api/debts/"):
            return self._send(404, {"error": "not found"})
        pinfl = self.path.split("/api/debts/", 1)[1].split("?", 1)[0]
        last = pinfl[-1:]
        print(f"[mock-mib] pinfl={pinfl}", flush=True)
        if last == "9":
            return self._send(200, {"data": {"hasDebt": True, "totalDebt": 12500000, "cases": [
                {"caseNo": "MIB-2026/1041", "amount": 8000000, "basis": "Sud buyrug'i"},
                {"caseNo": "MIB-2026/2210", "amount": 4500000, "basis": "Soliq qarzi"}]}, "message": "mock"})
        if last == "4":
            return self._send(404, {"error": "no enforcement proceedings"})
        if last == "5":
            return self._send(500, {"error": "internal error"})
        if last == "7":
            time.sleep(15)
        return self._send(200, {"data": {"hasDebt": False, "totalDebt": 0, "cases": []}, "message": "mock"})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"Soxta MIB: http://127.0.0.1:{PORT}/api/debts/{{pinfl}}" + (f"  (token: {TOKEN})" if TOKEN else ""), flush=True)
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
