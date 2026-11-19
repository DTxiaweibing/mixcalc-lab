"""vivo 传包脚本的单元测试（纯逻辑，不联网）。

运行：python scripts/test_vivo_publish.py -v
"""
import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import vivo_publish as vp  # noqa: E402


class SignBaseTest(unittest.TestCase):
    def test_sorts_keys_ascii_and_joins_with_ampersand(self):
        params = {"b": "2", "a": "1", "C": "3"}
        self.assertEqual(vp.build_sign_base(params), "C=3&a=1&b=2")

    def test_drops_empty_values_but_keeps_zero(self):
        params = {"a": "0", "b": "", "c": None}
        self.assertEqual(vp.build_sign_base(params), "a=0")

    def test_excludes_file_and_sign_by_default(self):
        params = {"a": "1", "file": b"binary", "sign": "deadbeef"}
        self.assertEqual(vp.build_sign_base(params), "a=1")

    def test_never_emits_bytes_values(self):
        params = {"a": "1", "blob": b"\xff\xfe"}
        self.assertEqual(vp.build_sign_base(params), "a=1")


class SignTest(unittest.TestCase):
    def test_matches_known_hmac_sha256_vector(self):
        params = {"a": "1", "b": "2"}
        self.assertEqual(
            vp.sign_params(params, "secret"),
            "604fe97c66c6393ff22e3cae366eee1131e351ebc736bf12f5d62e1755b7a233",
        )

    def test_upper_case_option(self):
        params = {"a": "1", "b": "2"}
        self.assertEqual(
            vp.sign_params(params, "secret", upper=True),
            "604FE97C66C6393FF22E3CAE366EEE1131E351EBC736BF12F5D62E1755B7A233",
        )

    def test_does_not_mutate_input(self):
        params = {"b": "2", "a": "1"}
        vp.sign_params(params, "secret")
        self.assertEqual(params, {"b": "2", "a": "1"})


class PublicParamsTest(unittest.TestCase):
    def test_legacy_style_uses_version_and_developer_key(self):
        p = vp.public_params("legacy", "app.upload.apk", "AK", "1700000000")
        self.assertEqual(p["target_app_key"], "developer")
        self.assertEqual(p["version"], "1.0")
        self.assertEqual(p["method"], "app.upload.apk")
        self.assertEqual(p["access_key"], "AK")
        self.assertEqual(p["sign_method"], "hmac-sha256")
        self.assertEqual(p["format"], "json")

    def test_openapi_style_uses_v_and_app_developer_key(self):
        p = vp.public_params("openapi", "app.upload.apk", "AK", "1700000000")
        self.assertEqual(p["target_app_key"], "app.developer")
        self.assertEqual(p["v"], "1.0")
        self.assertNotIn("version", p)

    def test_unknown_style_raises(self):
        with self.assertRaises(ValueError):
            vp.public_params("nope", "app.upload.apk", "AK", "1")


class EndpointTest(unittest.TestCase):
    def test_legacy_gateway(self):
        self.assertEqual(
            vp.endpoint("legacy"), "https://developer-api.vivo.com.cn/router/rest"
        )

    def test_openapi_gateway(self):
        self.assertEqual(
            vp.endpoint("openapi"),
            "https://developer-openapi.vivo.com.cn/router/rest",
        )

    def test_sandbox_overrides(self):
        self.assertEqual(
            vp.endpoint("legacy", sandbox=True),
            "https://sandbox-developer-api.vivo.com.cn/router/rest",
        )


class FileHashTest(unittest.TestCase):
    def test_md5_of_abc(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "abc.bin"
            p.write_bytes(b"abc")
            self.assertEqual(vp.file_md5(p), "900150983cd24fb0d6963f7d28e17f72")

    def test_missing_file_raises_friendly_error(self):
        with self.assertRaises(FileNotFoundError):
            vp.file_md5(Path("definitely/not/here.apk"))


class MultipartTest(unittest.TestCase):
    def test_includes_fields_and_file_part(self):
        body = vp.build_multipart(
            {"packageName": "com.x.y"},
            file_field="file",
            filename="app.apk",
            payload=b"APKDATA",
            boundary="BOUND",
        )
        text = body.decode("utf-8", "surrogateescape")
        self.assertIn('name="packageName"', text)
        self.assertIn("com.x.y", text)
        self.assertIn('name="file"; filename="app.apk"', text)
        self.assertIn("APKDATA", text)
        self.assertIn("--BOUND\r\n", text)
        self.assertTrue(body.endswith(b"--BOUND--\r\n"))

    def test_non_ascii_filename_gets_rfc5987_form(self):
        body = vp.build_multipart(
            {},
            file_field="file",
            filename="秒算虾料.apk",
            payload=b"x",
            boundary="B",
        )
        head = body.split(b"\r\n\r\n")[0].decode("utf-8", "surrogateescape")
        self.assertIn("filename*=UTF-8''", head)
        self.assertIn("%E7%A7%92%E7%AE%97", head)

    def test_content_type_header_carries_boundary(self):
        headers = vp.multipart_headers("BOUND")
        self.assertEqual(
            headers["Content-Type"], "multipart/form-data; boundary=BOUND"
        )


class UploadRequestTest(unittest.TestCase):
    def _apk(self, tmp: str) -> Path:
        p = Path(tmp) / "app.apk"
        p.write_bytes(b"PK\x03\x04payload")
        return p

    def test_signs_everything_except_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            req = vp.build_upload_request(
                apk_path=self._apk(tmp),
                package_name="com.x.y",
                access_key="AK",
                access_secret="secret",
                style="legacy",
                sandbox=True,
                timestamp="1700000000",
                boundary="BOUND",
            )
        self.assertIn("fileMd5", req.fields)
        self.assertEqual(req.fields["packageName"], "com.x.y")
        self.assertNotIn("file", req.fields)
        signed_keys = [kv.split("=", 1)[0] for kv in
                       vp.build_sign_base(req.fields).split("&")]
        self.assertNotIn("file", signed_keys)
        self.assertIn("fileMd5", signed_keys)
        self.assertEqual(
            req.fields["sign"],
            vp.sign_params(req.fields, "secret", upper=False),
        )

    def test_points_at_sandbox_gateway(self):
        with tempfile.TemporaryDirectory() as tmp:
            req = vp.build_upload_request(
                apk_path=self._apk(tmp),
                package_name="com.x.y",
                access_key="AK",
                access_secret="secret",
                style="openapi",
                sandbox=True,
                timestamp="1700000000",
                boundary="BOUND",
            )
        self.assertEqual(
            req.url, "https://sandbox-developer-api.vivo.com.cn/router/rest"
        )
        self.assertIn("fileMd5", req.body.decode("utf-8", "surrogateescape"))


class CredentialsTest(unittest.TestCase):
    def test_reads_env_vars(self):
        creds = vp.load_credentials(
            env={"VIVO_ACCESS_KEY": "AK", "VIVO_ACCESS_SECRET": "SK"}
        )
        self.assertEqual((creds.access_key, creds.access_secret), ("AK", "SK"))

    def test_reads_json_file_when_env_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "vivo_credentials.json"
            p.write_text(
                json.dumps({"access_key": "FAK", "access_secret": "FSK"}),
                encoding="utf-8",
            )
            creds = vp.load_credentials(env={}, candidates=[p])
        self.assertEqual((creds.access_key, creds.access_secret), ("FAK", "FSK"))

    def test_returns_none_when_nothing_configured(self):
        with tempfile.TemporaryDirectory() as tmp:
            missing = Path(tmp) / "nope.json"
            self.assertIsNone(vp.load_credentials(env={}, candidates=[missing]))

    def test_malformed_json_file_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "bad.json"
            p.write_text("{not json", encoding="utf-8")
            with self.assertRaises(vp.CredentialError):
                vp.load_credentials(env={}, candidates=[p])


class DryRunTest(unittest.TestCase):
    def test_upload_dry_run_works_without_credentials(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "app.apk"
            apk.write_bytes(b"PK\x03\x04")
            patcher = mock.patch.object(vp, "load_credentials", return_value=None)
            with patcher, contextlib.redirect_stdout(io.StringIO()) as out:
                rc = vp.main(
                    ["upload", "--apk", str(apk), "--dry-run",
                     "--package", "com.x.y"]
                )
        self.assertEqual(rc, 0)
        self.assertIn("URL", out.getvalue())
        self.assertIn("com.x.y", out.getvalue())

    def test_upload_without_credentials_and_no_dry_run_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "app.apk"
            apk.write_bytes(b"PK")
            patcher = mock.patch.object(vp, "load_credentials", return_value=None)
            with patcher, contextlib.redirect_stderr(io.StringIO()) as err:
                rc = vp.main(["upload", "--apk", str(apk)])
        self.assertEqual(rc, 2)
        self.assertIn("缺少凭据", err.getvalue())


class MaskTest(unittest.TestCase):
    def test_masks_all_but_last_four(self):
        self.assertEqual(vp.mask("abcdefgh1234"), "********1234")

    def test_short_secret_fully_masked(self):
        self.assertEqual(vp.mask("abc"), "***")


if __name__ == "__main__":
    unittest.main(verbosity=2)
