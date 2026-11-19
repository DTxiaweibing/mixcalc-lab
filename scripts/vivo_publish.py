#!/usr/bin/env python3
# standards-exempt: 一次性运维脚本（命令行工具），无框架、无领域层，纯 stdlib 手写 HTTP 客户端
"""vivo 应用市场传包 CLI（纯标准库，无第三方依赖）。

用法：
    python scripts/vivo_publish.py doctor
    python scripts/vivo_publish.py upload --apk dist/秒算虾料-0.3.7-release.apk --dry-run
    python scripts/vivo_publish.py upload --apk dist/秒算虾料-0.3.7-release.apk
    python scripts/vivo_publish.py submit --package com.shrimpfarm.app.calclab --online-type 1
    python scripts/vivo_publish.py call --method app.query.version --param packageName=...

凭据来源（按优先级）：
    1. 环境变量 VIVO_ACCESS_KEY / VIVO_ACCESS_SECRET
    2. scripts/vivo_credentials.json
    3. ~/.vivo/vivo_credentials.json
    内容形如 {"access_key": "...", "access_secret": "..."}
"""
import argparse
import hashlib
import hmac
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

DEFAULT_PACKAGE = "com.shrimpfarm.app.calclab"
SCRIPT_DIR = Path(__file__).resolve().parent
CRED_FILENAME = "vivo_credentials.json"
SIGN_EXCLUDE = ("file", "sign")
SIGN_ERROR_CODES = {"23"}

GATEWAYS = {
    "legacy": "https://developer-api.vivo.com.cn/router/rest",
    "openapi": "https://developer-openapi.vivo.com.cn/router/rest",
}
SANDBOX_GATEWAY = "https://sandbox-developer-api.vivo.com.cn/router/rest"


class CredentialError(Exception):
    """凭据文件格式错误。"""


class VivoApiError(Exception):
    """网关返回非 0 状态码。"""

    def __init__(self, code: Any, message: str, raw: dict | None = None):
        super().__init__(f"vivo 返回 code={code}: {message}")
        self.code = str(code)
        self.message = message
        self.raw = raw or {}


@dataclass
class Credentials:
    access_key: str
    access_secret: str


@dataclass
class UploadRequest:
    url: str
    body: bytes
    headers: dict
    fields: dict = field(default_factory=dict)


def build_sign_base(params: dict, exclude=SIGN_EXCLUDE) -> str:
    """按 ASCII 排序拼接 k=v，跳过 file/sign、空值与二进制值。"""
    items = []
    for key, value in params.items():
        if key in exclude or value is None or isinstance(value, (bytes, bytearray)):
            continue
        text = str(value)
        if text == "":
            continue
        items.append((str(key), text))
    items.sort(key=lambda kv: kv[0])
    return "&".join(f"{k}={v}" for k, v in items)


def sign_params(params: dict, secret: str, upper: bool = False) -> str:
    """HMAC-SHA256 签名，默认小写十六进制。"""
    base = build_sign_base(params)
    digest = hmac.new(
        secret.encode("utf-8"), base.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    return digest.upper() if upper else digest


def toggle_sign_case(sign: str) -> str:
    return sign.upper() if sign.islower() else sign.lower()


def public_params(
    style: str, method: str, access_key: str, timestamp: str
) -> dict:
    """公共参数：legacy 用 version=1.0，openapi 用 v=1.0。"""
    if style == "legacy":
        version_key, target = "version", "developer"
    elif style == "openapi":
        version_key, target = "v", "app.developer"
    else:
        raise ValueError(f"未知网关风格: {style}（可选 legacy / openapi）")
    return {
        "method": method,
        "access_key": access_key,
        "timestamp": str(timestamp),
        "format": "json",
        version_key: "1.0",
        "sign_method": "hmac-sha256",
        "target_app_key": target,
    }


def endpoint(style: str, sandbox: bool = False) -> str:
    if sandbox:
        return SANDBOX_GATEWAY
    return GATEWAYS.get(style, GATEWAYS["legacy"])


def file_md5(path) -> str:
    """分块计算 MD5，供 fileMd5 参数使用。"""
    digest = hashlib.md5()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _quote_header_value(text: str) -> str:
    """把非 ASCII 字符替换为下划线，得到可放进 filename="..." 的兜底名。"""
    return "".join(ch if 32 <= ord(ch) < 127 and ch not in '"\\' else "_" for ch in text)


def _disposition(name: str, filename: str | None) -> str:
    if filename is None:
        return f'Content-Disposition: form-data; name="{name}"'
    fallback = _quote_header_value(filename)
    encoded = urllib.parse.quote(filename, safe="")
    return (
        f'Content-Disposition: form-data; name="{name}"; '
        f'filename="{fallback}"; filename*=UTF-8\'\'{encoded}'
    )


def build_multipart(
    fields: dict, file_field: str, filename: str, payload: bytes, boundary: str
) -> bytes:
    """手工拼 multipart/form-data，文本字段在前、文件字段在后。"""
    parts: list[bytes] = []
    for key, value in fields.items():
        if value is None:
            continue
        text = value if isinstance(value, str) else str(value)
        parts.append(
            f"--{boundary}\r\n{_disposition(str(key), None)}\r\n\r\n".encode()
            + text.encode("utf-8")
            + b"\r\n"
        )
    parts.append(
        f"--{boundary}\r\n{_disposition(file_field, filename)}\r\n"
        f"Content-Type: application/octet-stream\r\n\r\n".encode()
    )
    parts.append(payload)
    parts.append(b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts)


def multipart_headers(boundary: str) -> dict:
    return {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Accept": "application/json",
        "User-Agent": "vivo-publish-cli/1.0",
    }


def build_upload_request(
    apk_path,
    package_name: str,
    access_key: str,
    access_secret: str,
    style: str = "legacy",
    sandbox: bool = False,
    timestamp: str | None = None,
    boundary: str | None = None,
    upper: bool = False,
) -> UploadRequest:
    """组装 app.upload.apk 的完整请求（不含网络 IO）。"""
    apk = Path(apk_path)
    stamp = str(timestamp or int(time.time() * 1000))
    boundary = boundary or secrets.token_hex(16)
    fields = public_params(style, "app.upload.apk", access_key, stamp)
    fields["packageName"] = package_name
    fields["fileMd5"] = file_md5(apk)
    fields["sign"] = sign_params(fields, access_secret, upper=upper)
    body = build_multipart(
        fields,
        file_field="file",
        filename=apk.name,
        payload=apk.read_bytes(),
        boundary=boundary,
    )
    return UploadRequest(
        url=endpoint(style, sandbox),
        body=body,
        headers=multipart_headers(boundary),
        fields=fields,
    )


def post_payload(url: str, body: bytes, headers: dict, timeout: int = 600) -> dict:
    """POST 并用 JSON 解析；HTTP 错误也尽量读出网关返回体。"""
    request = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            text = response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        text = exc.read().decode("utf-8", "replace")
    try:
        return json.loads(text) if text.strip() else {}
    except json.JSONDecodeError:
        raise VivoApiError("http", f"响应不是 JSON: {text[:300]}") from None


def check_response(payload: dict) -> dict:
    """校验 code==0，失败抛 VivoApiError。"""
    code = payload.get("code", payload.get("status", 0))
    if str(code) not in ("0", "200"):
        message = payload.get("msg") or payload.get("message") or "未知错误"
        raise VivoApiError(code, str(message), payload)
    return payload


def credential_candidates() -> list[Path]:
    home = Path(os.path.expanduser("~"))
    return [SCRIPT_DIR / CRED_FILENAME, home / ".vivo" / CRED_FILENAME]


def load_credentials(env=None, candidates=None) -> Credentials | None:
    """环境变量优先，其次 JSON 文件；都没有返回 None。"""
    env = os.environ if env is None else env
    key, secret = env.get("VIVO_ACCESS_KEY"), env.get("VIVO_ACCESS_SECRET")
    if key and secret:
        return Credentials(key.strip(), secret.strip())
    for path in candidates if candidates is not None else credential_candidates():
        if not path.is_file():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise CredentialError(f"{path} 不是合法 JSON: {exc}") from None
        key = str(data.get("access_key") or "").strip()
        secret = str(data.get("access_secret") or "").strip()
        if not key or not secret:
            raise CredentialError(f"{path} 缺少 access_key / access_secret")
        return Credentials(key, secret)
    return None


def mask(text: str) -> str:
    if len(text) <= 4:
        return "*" * len(text)
    return "*" * (len(text) - 4) + text[-4:]


def _print_json(payload: dict) -> None:
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def cmd_doctor(args) -> int:
    print(f"python      : {sys.version.split()[0]}")
    print(f"包名        : {args.package}")
    print(f"网关风格    : {args.style} → {endpoint(args.style, args.sandbox)}")
    try:
        creds = load_credentials()
    except CredentialError as exc:
        print(f"凭据        : 文件有问题 → {exc}")
        return 2
    if creds is None:
        print("凭据        : 未配置（设 VIVO_ACCESS_KEY/SECRET 或写 "
              f"{SCRIPT_DIR / CRED_FILENAME}）")
    else:
        print(f"凭据        : access_key={mask(creds.access_key)} "
              f"access_secret={mask(creds.access_secret)}")
    if args.apk:
        apk = Path(args.apk)
        if apk.is_file():
            print(f"APK         : {apk} ({apk.stat().st_size:,} B)")
            print(f"MD5         : {file_md5(apk)}")
        else:
            print(f"APK         : 不存在 → {apk}")
    return 0


def _resolve(args, dry_run: bool = False):
    """取凭据；dry-run 时允许用占位密钥。返回 Credentials 或退出码 int。"""
    try:
        creds = load_credentials()
    except CredentialError as exc:
        if not dry_run:
            print(f"凭据错误: {exc}", file=sys.stderr)
            return 2
        print(f"(dry-run) 忽略凭据文件问题: {exc}")
        creds = None
    if creds is None:
        if dry_run:
            print("(dry-run) 未配置凭据，使用占位密钥，请求不会真的发出")
            return Credentials("DRYRUN_ACCESS_KEY", "DRYRUN_ACCESS_SECRET")
        print("缺少凭据：请设置 VIVO_ACCESS_KEY / VIVO_ACCESS_SECRET，"
              f"或写入 {SCRIPT_DIR / CRED_FILENAME}", file=sys.stderr)
        return 2
    return creds


def _upload_once(args, creds, upper: bool) -> UploadRequest:
    return build_upload_request(
        apk_path=args.apk,
        package_name=args.package,
        access_key=creds.access_key,
        access_secret=creds.access_secret,
        style=args.style,
        sandbox=args.sandbox,
        upper=upper,
    )


def cmd_upload(args) -> int:
    apk = Path(args.apk)
    if not apk.is_file():
        print(f"APK 不存在: {apk}", file=sys.stderr)
        return 2
    creds = _resolve(args, dry_run=args.dry_run)
    if isinstance(creds, int):
        return creds
    request = _upload_once(args, creds, upper=(args.sign_case == "upper"))
    if args.dry_run:
        print(f"URL  : {request.url}")
        print(f"包名 : {request.fields['packageName']}")
        print(f"MD5  : {request.fields['fileMd5']}")
        print(f"体积 : {len(request.body):,} B（multipart）")
        print(f"签名 : {request.fields['sign'][:16]}…（{args.sign_case}）")
        return 0
    return _send_upload(request, creds, args)


def _send_upload(request: UploadRequest, creds, args) -> int:
    payload = post_payload(request.url, request.body, request.headers)
    if str(payload.get("code", 0)) in SIGN_ERROR_CODES:
        print("签名被拒（code=23），换大小写重试一次…", file=sys.stderr)
        alt = _upload_once(args, creds, upper=not request.fields["sign"].islower())
        payload = post_payload(alt.url, alt.body, alt.headers)
    _print_json(payload)
    try:
        data = check_response(payload).get("data") or {}
    except VivoApiError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    serial = data.get("serialNumber") if isinstance(data, dict) else None
    if serial:
        print(f"\n上传成功，serialNumber = {serial}")
        print("下一步：python scripts/vivo_publish.py submit "
              f"--package {args.package} --param serialNumber={serial} --online-type 1")
    return 0


def cmd_submit(args) -> int:
    creds = _resolve(args, dry_run=args.dry_run)
    if isinstance(creds, int):
        return creds
    fields = public_params(args.style, "app.update.submit", creds.access_key,
                           str(int(time.time() * 1000)))
    fields["packageName"] = args.package
    if args.online_type:
        fields["onlineType"] = str(args.online_type)
    fields.update(parse_params(args.param))
    fields["sign"] = sign_params(fields, creds.access_secret,
                                 upper=(args.sign_case == "upper"))
    body = urllib.parse.urlencode(fields).encode("utf-8")
    headers = {"Content-Type": "application/x-www-form-urlencoded",
               "Accept": "application/json"}
    if args.dry_run:
        print(f"URL: {endpoint(args.style, args.sandbox)}")
        print(urllib.parse.urlencode(fields, quote_via=urllib.parse.quote))
        return 0
    payload = post_payload(endpoint(args.style, args.sandbox), body, headers)
    _print_json(payload)
    try:
        check_response(payload)
    except VivoApiError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 0


def cmd_call(args) -> int:
    """通用调用：任意 method + 任意参数，便于临时排查。"""
    creds = _resolve(args, dry_run=args.dry_run)
    if isinstance(creds, int):
        return creds
    fields = public_params(args.style, args.method, creds.access_key,
                           str(int(time.time() * 1000)))
    fields.update(parse_params(args.param))
    fields["sign"] = sign_params(fields, creds.access_secret,
                                 upper=(args.sign_case == "upper"))
    body = urllib.parse.urlencode(fields).encode("utf-8")
    headers = {"Content-Type": "application/x-www-form-urlencoded",
               "Accept": "application/json"}
    if args.dry_run:
        print(f"URL: {endpoint(args.style, args.sandbox)}")
        print(urllib.parse.urlencode(fields, quote_via=urllib.parse.quote))
        return 0
    payload = post_payload(endpoint(args.style, args.sandbox), body, headers)
    _print_json(payload)
    return 0 if str(payload.get("code", 0)) == "0" else 1


def parse_params(pairs) -> dict:
    """把 ["k=v", ...] 解析成字典；非法项报错退出。"""
    result = {}
    for item in pairs or []:
        if "=" not in item:
            raise SystemExit(f"参数格式应为 k=v: {item}")
        key, value = item.split("=", 1)
        result[key] = value
    return result


def build_parser() -> argparse.ArgumentParser:
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--package", default=DEFAULT_PACKAGE, help="应用包名")
    common.add_argument("--style", default="legacy", choices=sorted(GATEWAYS),
                        help="网关风格，默认 legacy")
    common.add_argument("--sandbox", action="store_true", help="走沙箱网关")
    common.add_argument("--sign-case", default="lower", choices=["lower", "upper"],
                        help="签名十六进制大小写，默认 lower")

    parser = argparse.ArgumentParser(description="vivo 应用市场传包 CLI")
    subs = parser.add_subparsers(dest="command", required=True)

    doctor = subs.add_parser("doctor", parents=[common], help="检查环境与凭据")
    doctor.add_argument("--apk", help="顺便算 MD5")
    doctor.set_defaults(func=cmd_doctor)

    upload = subs.add_parser("upload", parents=[common], help="上传 APK 拿 serialNumber")
    upload.add_argument("--apk", required=True, help="APK 路径")
    upload.add_argument("--dry-run", action="store_true", help="只打印请求，不发网络")
    upload.set_defaults(func=cmd_upload)

    submit = subs.add_parser("submit", parents=[common], help="提交审核")
    submit.add_argument("--online-type", type=int, default=1, help="上线类型，默认 1")
    submit.add_argument("--param", action="append", help="附加参数 k=v，可重复")
    submit.add_argument("--dry-run", action="store_true")
    submit.set_defaults(func=cmd_submit)

    call = subs.add_parser("call", parents=[common], help="调用任意 method")
    call.add_argument("--method", required=True, help="如 app.query.version")
    call.add_argument("--param", action="append", help="参数 k=v，可重复")
    call.add_argument("--dry-run", action="store_true")
    call.set_defaults(func=cmd_call)
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except VivoApiError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
