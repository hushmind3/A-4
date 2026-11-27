import http.client
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from engine_bridge import Bridge, HOST, allowed, assert_engine

TOKEN = "x" * 43
class Origin(BaseHTTPRequestHandler):
    calls = []
    def log_message(self, *_): pass
    def do_GET(self):
        self.calls.append((self.path, self.headers.get("Host"), self.headers.get("X-Dominion-Token")))
        if self.headers.get("X-Dominion-Token") != TOKEN:
            self.send_response(403); self.end_headers(); return
        self.send_response(200); self.send_header("Content-Type", "application/json")
        self.end_headers(); self.wfile.write(b'{"data":{"id":"owner"}}')

class BridgeTest(unittest.TestCase):
    def setUp(self):
        Origin.calls.clear()
        self.native = ThreadingHTTPServer(("127.0.0.1",0),Origin)
        self.proxy = Bridge(0,self.native.server_port,TOKEN)
        for server in (self.native,self.proxy):
            threading.Thread(target=server.serve_forever,daemon=True).start()
    def tearDown(self):
        for server in (self.proxy,self.native):
            server.shutdown();server.server_close()
    def get(self,path,token=None,host=HOST):
        c=http.client.HTTPConnection("127.0.0.1",self.proxy.server_port,timeout=3)
        try:
            headers={"Host":host}
            if token is not None: headers["X-Dominion-Token"]=token
            c.request("GET",path,headers=headers)
            r=c.getresponse()
            return r.status,r.read()
        finally:c.close()
    def test_native_authentication_probe(self):
        assert_engine(self.native.server_port,TOKEN)
        with self.assertRaises(RuntimeError):assert_engine(self.native.server_port,"y"*43)
    def test_auth_and_route_before_forwarding(self):
        self.assertEqual(401,self.get("/v1/me")[0])
        self.assertEqual(401,self.get("/v1/me","wrong")[0])
        self.assertEqual(403,self.get("/v1/me",TOKEN,"localhost")[0])
        self.assertEqual(404,self.get("/api/remote/workers",TOKEN)[0])
        self.assertEqual(404,self.get("/api/rooms/../settings",TOKEN)[0])
        self.assertEqual([],Origin.calls)
        self.assertEqual((200,b'{"data":{"id":"owner"}}'),self.get("/v1/me",TOKEN))
        self.assertEqual([("/v1/me",HOST,TOKEN)],Origin.calls)
    def test_route_scope(self):
        self.assertTrue(allowed("GET","/api/events?after=2"))
        self.assertTrue(allowed("POST","/api/rooms/assistant/messages"))
        self.assertFalse(allowed("GET","//other.example/v1/me"))
        self.assertFalse(allowed("GET","/api/rooms/%2e%2e"))
        self.assertFalse(allowed("GET","/api/cloud-auth/google/status"))
        self.assertFalse(allowed("POST","/v1/auth/google"))

if __name__=="__main__":unittest.main()
