import unittest
from unittest.mock import patch
from cloud_engine_connector import invoke

class ConnectorTest(unittest.TestCase):
    def request(self,method,path,data="",ctype=""):
        return {"route":"native","body":{"method":method,"path":path,"base64":data,"contentType":ctype}}
    def test_rejects_unapproved_routes(self):
        for p in ["/api/events","/api/cloud-auth/google/status","/api/rooms/../settings","/api/remote/workers","//bad/v1/me"]:
            self.assertEqual(403,invoke(self.request("GET",p),4317)[0])
    def test_forwards_only_loopback_native(self):
        with patch("cloud_engine_connector.local",return_value=(200,b'{"data":{"id":"owner"}}',"application/json")) as native:
            code,data=invoke(self.request("GET","/v1/me"),4317)
            self.assertEqual(200,code)
            self.assertEqual("application/json",data["contentType"])
            native.assert_called_once_with("GET","/v1/me",None,"",4317)
    def test_body_and_size(self):
        self.assertEqual(400,invoke(self.request("GET","/v1/me","e30="),4317)[0])
        self.assertEqual(413,invoke(self.request("POST","/api/rooms","a"*780001),4317)[0])
if __name__=="__main__":unittest.main()
