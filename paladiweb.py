import os
import sys
import json
import time
import sqlite3
import random
import string
import urllib.request
import urllib.error
from datetime import datetime, timedelta
from flask import Flask, request, render_template_string, redirect, url_for, session, jsonify, Response, send_from_directory, send_file

app = Flask(__name__)
app.secret_key = os.environ.get("PALADI_SECRET_KEY", "paladi-development-secret")
ADMIN_PASSWORD = os.environ.get("PALADI_ADMIN_PASSWORD", "paladi123")
BASE_CSS = ""

DB_FILE = os.environ.get("PALADI_DB_FILE", "licenses.db")

@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS, PUT, DELETE"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization, X-Requested-With"
    return response

# ----------------------------------------------------------------------
# IUNO SHOP - 24/7 CLOUD CONTROLLER & ELDORADO PAYOUT TRACKER
# ----------------------------------------------------------------------
CLIENT_ID = os.environ.get("ELDORADO_CLIENT_ID", "9dcffa15-088b-4eca-b8b1-6509abb25e3a")
CLIENT_SECRET = os.environ.get("ELDORADO_CLIENT_SECRET", "PWAgwlExYqo_4WqG0gw007SAM5PsDwKUm8JgCdASWkM")

ELDORADO_AUTH = {"token": None, "expires_at": 0}
ELDORADO_EVENTS = []
ELDORADO_CACHE = {
    "configured": True,
    "last_sync": None,
    "payouts": [],
    "summary": {
        "total_orders": 0,
        "total_amount": "0.00 USD",
        "overdue_count": 0,
        "next_estimate_time": "—",
        "next_estimate_countdown": "—"
    },
    "error": None
}

def parse_iso_datetime(date_str):
    if not date_str:
        return None
    try:
        clean = date_str.replace("Z", "+00:00")
        dt = datetime.fromisoformat(clean)
        return (dt + timedelta(hours=7)).replace(tzinfo=None)
    except Exception:
        pass
    for fmt in ("%Y-%m-%dT%H:%M:%S.%f", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(date_str[:19], fmt) + timedelta(hours=7)
        except Exception:
            pass
    return None

def get_eldorado_access_token():
    global ELDORADO_AUTH
    if not CLIENT_ID or not CLIENT_SECRET:
        return None, "Chưa cấu hình Client ID và Secret của Eldorado"

    now_ts = time.time()
    if ELDORADO_AUTH["token"] and now_ts < (ELDORADO_AUTH["expires_at"] - 60):
        return ELDORADO_AUTH["token"], None

    try:
        url = "https://www.eldorado.gg/api/authentication/seller/token"
        payload = json.dumps({"clientId": CLIENT_ID, "clientSecret": CLIENT_SECRET}).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json, text/plain, */*"
        }
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        with urllib.request.urlopen(req, timeout=12) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            token = data.get("accessToken")
            expires_in = int(data.get("expiresIn", 899))
            ELDORADO_AUTH["token"] = token
            ELDORADO_AUTH["expires_at"] = now_ts + expires_in
            return token, None
    except Exception as e:
        return None, f"Lỗi lấy token xác thực Eldorado: {e}"

def sync_eldorado_orders():
    global ELDORADO_CACHE
    token, err = get_eldorado_access_token()
    if err or not token:
        ELDORADO_CACHE["configured"] = False
        ELDORADO_CACHE["error"] = err or "Không lấy được token Eldorado"
        return ELDORADO_CACHE

    try:
        url = "https://www.eldorado.gg/api/orders/me/seller/orders?pageIndex=1&pageSize=50"
        headers = {
            "Authorization": f"Bearer {token}",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json, text/plain, */*"
        }
        req = urllib.request.Request(url, headers=headers, method="GET")
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))

        orders = data.get("results") or []
        processed_payouts = []
        now = datetime.now()

        for o in orders:
            state_obj = o.get("state") or {}
            curr_state = state_obj.get("state")
            if curr_state not in ("Delivered", "Received"):
                continue

            state_logs = o.get("stateLogs") or []
            def get_latest_log(target_state):
                latest = None
                for l in state_logs:
                    if l.get("state") == target_state:
                        d = parse_iso_datetime(l.get("createdDate"))
                        if d and (latest is None or d > latest):
                            latest = d
                return latest

            base_state = None
            base_date = None
            estimate_date = None

            rec_date = get_latest_log("Received")
            if not rec_date and curr_state == "Received":
                rec_date = parse_iso_datetime(state_obj.get("createdDate"))

            if rec_date:
                base_state = "Received"
                base_date = rec_date
                estimate_date = rec_date + timedelta(days=5)
            else:
                del_date = get_latest_log("Delivered")
                if not del_date and curr_state == "Delivered":
                    del_date = parse_iso_datetime(state_obj.get("createdDate"))
                if del_date:
                    base_state = "Delivered"
                    base_date = del_date
                    estimate_date = del_date + timedelta(days=8)

            if not estimate_date:
                continue

            order_id = o.get("id") or "N/A"
            buyer = o.get("buyerUsername") or "Buyer"
            price_obj = o.get("totalPrice") or {}
            amount = price_obj.get("amount", 0.0)
            currency = price_obj.get("currency", "USD")

            diff_sec = (estimate_date - now).total_seconds()
            is_overdue = diff_sec <= 0

            if is_overdue:
                countdown_str = "Đã đến hạn"
            else:
                total_min = int(diff_sec // 60)
                days = total_min // (24 * 60)
                hours = (total_min % (24 * 60)) // 60
                mins = total_min % 60
                if days > 0:
                    countdown_str = f"còn {days} ngày {hours} giờ"
                elif hours > 0:
                    countdown_str = f"còn {hours} giờ {mins} phút"
                else:
                    countdown_str = f"còn {mins} phút"

            details = o.get("orderOfferDetails") or {}
            title = "Money"
            if details.get("gameName"):
                title = details.get("gameName")
            elif details.get("title"):
                title = details.get("title")

            qty_val = o.get("purchaseQuantity") or details.get("orderPricing", {}).get("quantity") or 1
            qty_str = f"x{qty_val}"

            processed_payouts.append({
                "orderId": order_id,
                "order_id": order_id,
                "buyer": buyer,
                "product": title,
                "quantity": qty_val,
                "qty": qty_str,
                "amount": amount,
                "currency": currency,
                "basis": base_state.lower() if base_state else "received",
                "base_state": base_state,
                "base_date": base_date.strftime("%H:%M %d/%m/%y") if base_date else "",
                "basisText": f"{base_state} +{'8' if base_state == 'Delivered' else '5'} ngày",
                "basisDate": f"từ {base_date.strftime('%H:%M %d/%m/%y')}" if base_date else "",
                "estimate_iso": estimate_date.isoformat(),
                "estimate_time": estimate_date.strftime("%H:%M"),
                "estTime": estimate_date.strftime("%H:%M"),
                "estimate_full": estimate_date.strftime("%H:%M %d/%m/%Y"),
                "startTime": base_date.strftime("%H:%M %d/%m/%y") if base_date else "",
                "countdown_text": countdown_str,
                "is_overdue": is_overdue,
                "remainingSec": max(0, int(diff_sec)),
                "seconds_remaining": max(0, int(diff_sec))
            })

        processed_payouts.sort(key=lambda x: x["seconds_remaining"])

        total_pending = len(processed_payouts)
        overdue_list = [p for p in processed_payouts if p["is_overdue"]]
        future_list = [p for p in processed_payouts if not p["is_overdue"]]

        currencies = {}
        for p in processed_payouts:
            c = p["currency"]
            currencies[c] = round(currencies.get(c, 0.0) + p["amount"], 2)

        total_amount_str = " · ".join([f"{v:,.2f} {k}" for k, v in currencies.items()]) or "0.00 USD"
        next_payout = future_list[0] if future_list else None

        ELDORADO_CACHE = {
            "configured": True,
            "last_sync": now.strftime("%H:%M:%S %d/%m/%Y"),
            "error": None,
            "summary": {
                "total_orders": total_pending,
                "total_amount": total_amount_str,
                "overdue_count": len(overdue_list),
                "next_estimate_time": next_payout["estimate_time"] if next_payout else "—",
                "next_estimate_countdown": next_payout["countdown_text"] if next_payout else "—"
            },
            "payouts": processed_payouts
        }
        return ELDORADO_CACHE
    except Exception as e:
        ELDORADO_CACHE["error"] = f"Lỗi đồng bộ Eldorado: {str(e)}"
        return ELDORADO_CACHE

@app.route("/")
@app.route("/payouts")
@app.route("/mt-store")
def iuno_home():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    return send_from_directory(os.path.join(base_dir, "mobile_app"), "index.html")

@app.route("/sw.js")
def service_worker():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    resp = send_from_directory(os.path.join(base_dir, "mobile_app"), "sw.js")
    resp.headers["Content-Type"] = "application/javascript"
    resp.headers["Service-Worker-Allowed"] = "/"
    return resp

@app.route("/manifest.json")
def manifest():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    resp = send_from_directory(os.path.join(base_dir, "mobile_app"), "manifest.json")
    resp.headers["Content-Type"] = "application/manifest+json"
    return resp

@app.route("/download-apk")
@app.route("/PaladiPay.apk")
def download_apk():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    apk_path = os.path.join(base_dir, "mobile_app", "PaladiPay.apk")
    if not os.path.exists(apk_path):
        import subprocess
        subprocess.run(["python", "build_apk.py"], check=False)
    return send_file(
        apk_path,
        mimetype="application/vnd.android.package-archive",
        as_attachment=True,
        download_name="PaladiPay.apk"
    )

@app.route("/iuno_bg.png")
@app.route("/icon-192.png")
@app.route("/icon-512.png")
@app.route("/server_config.js")
@app.route("/style.css")
@app.route("/app.js")
def iuno_static_asset():
    path = request.path.lstrip("/")
    base_dir = os.path.dirname(os.path.abspath(__file__))
    return send_from_directory(os.path.join(base_dir, "mobile_app"), path)

@app.route("/api/eldorado/payouts", methods=["GET"])
def api_eldorado_payouts():
    force = request.args.get("refresh") == "1"
    if force or not ELDORADO_CACHE.get("last_sync"):
        sync_eldorado_orders()
    return jsonify(ELDORADO_CACHE)

@app.route("/api/eldorado/settings", methods=["GET", "POST"])
def api_eldorado_settings():
    return jsonify({
        "client_id": CLIENT_ID[:6] + "..." if CLIENT_ID else "",
        "has_creds": bool(CLIENT_ID and CLIENT_SECRET),
        "sound_enabled": True,
        "desktop_notify": True
    })

@app.route("/api/eldorado/events", methods=["GET"])
def api_eldorado_events():
    return jsonify({"events": ELDORADO_EVENTS, "total": len(ELDORADO_EVENTS)})

# ----------------------------------------------------------------------
# Multi-Account In-Game Tracking & Disconnect Log Inspector
# ----------------------------------------------------------------------
SAMPLE_BALANCE_HISTORY_12QC = [
    {"time": "22:28:44", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:28:38", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:28:32", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:28:26", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:28:20", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:28:14", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:28:08", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:28:02", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:56", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:50", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:44", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:38", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:32", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:26", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:20", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:14", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"},
    {"time": "22:27:08", "amount": "+$18.3K", "type": "Cộng tiền (AutoSell / Farm)", "note": "Ghi nhận biến động số dư từ server"}
]

SAMPLE_DISCONNECT_LOG_CAPTAIN = """[22:15:20] [Render thread/INFO]: Connecting to paladi.net, 25565
[22:15:22] [Netty Client IO #3/INFO]: [CHAT] Welcome back, Captainsnake!
[22:15:23] [Netty Client IO #3/INFO]: [CHAT] Balance: $18,124.50
[22:15:30] [Netty Client IO #3/ERROR]: Internal Exception: java.net.SocketException: Connection reset
	at java.base/sun.nio.ch.SocketDispatcher.read0(Native Method)
	at java.base/sun.nio.ch.SocketDispatcher.read(SocketDispatcher.java:46)
	at java.base/sun.nio.ch.NioSocketImpl.tryRead(NioSocketImpl.java:256)
	at java.base/sun.nio.ch.NioSocketImpl.implRead(NioSocketImpl.java:307)
	at java.base/sun.nio.ch.NioSocketImpl.read(NioSocketImpl.java:346)
	at java.base/sun.nio.ch.NioSocketImpl$1.read(NioSocketImpl.java:796)
	at java.base/java.io.InputStream.read(InputStream.java:217)
	at io.netty.buffer.ByteBufInputStream.read(ByteBufInputStream.java:146)
	at net.minecraft.network.PacketByteBuf.readBytes(PacketByteBuf.java:230)
[22:15:30] [Render thread/INFO]: Client disconnected from server: Connection reset
[22:15:30] [Render thread/WARN]: Game session terminated abruptly by remote host."""

CLOUD_ACCOUNTS_DATA = [
    {
        "id": "29388",
        "port": 29388,
        "name": "12qc",
        "inGame": True,
        "balance": "$18.1K",
        "perHour": "$24.03M/h",
        "totalEarned": "$54.46M",
        "uptime": "02h 16m 00s",
        "shards": "1.2K ★",
        "shardsPerHour": "+3.4K/h",
        "launcher": "PrismLauncher 1.21.11",
        "server": "paladi.net",
        "health": 20.0,
        "maxHealth": 20.0,
        "food": 20,
        "disconnectInfo": {
            "isDisconnected": False,
            "reason": None,
            "logFile": "C:\\Users\\Admin\\AppData\\Roaming\\PrismLauncher\\instances\\1.21.11\\.minecraft\\logs\\latest.log",
            "logUrl": "/api/account/29388/log"
        },
        "balanceHistory": SAMPLE_BALANCE_HISTORY_12QC
    },
    {
        "id": "29389",
        "port": 29389,
        "name": "Captainsnake",
        "inGame": False,
        "balance": "$18.1K",
        "perHour": "$24.03M/h",
        "totalEarned": "$54.46M",
        "uptime": "02h 16m 00s",
        "shards": "1.2K ★",
        "shardsPerHour": "+3.4K/h",
        "launcher": "Dawn Launcher",
        "server": "paladi.net (Đã văng)",
        "health": 20.0,
        "maxHealth": 20.0,
        "food": 20,
        "disconnectInfo": {
            "isDisconnected": True,
            "reason": "Mất kết nối: Internal Exception: java.net.SocketException: Connection reset (Server ngắt kết nối đột ngột)",
            "logFile": "C:\\Users\\Admin\\AppData\\Roaming\\.dawn\\profiles\\aha\\.minecraft\\logs\\latest.log",
            "disconnectTime": "22:15:30 18/10/2026",
            "logUrl": "/api/account/29389/log"
        },
        "balanceHistory": SAMPLE_BALANCE_HISTORY_12QC[:8]
    },
    {
        "id": "29390",
        "port": 29390,
        "name": "Toby7554",
        "inGame": False,
        "balance": "0$",
        "perHour": "$0/h",
        "totalEarned": "$0",
        "uptime": "00h 00m 00s",
        "shards": "0 ★",
        "shardsPerHour": "+0/h",
        "launcher": "TLauncher",
        "server": "Offline (Sảnh chờ)",
        "health": 20.0,
        "maxHealth": 20.0,
        "food": 20,
        "disconnectInfo": {
            "isDisconnected": True,
            "reason": "Chưa kết nối vào server (Đang ở sảnh chờ/Offline)",
            "logFile": "C:\\Users\\Admin\\AppData\\Roaming\\.tlauncher\\legacy\\Minecraft\\game\\logs\\latest.log",
            "disconnectTime": "22:00:10 18/10/2026",
            "logUrl": "/api/account/29390/log"
        },
        "balanceHistory": []
    }
]

@app.route("/api/account/<acc_id>/log", methods=["GET"])
def api_account_log_route(acc_id):
    """Chi tiết nhật ký log và nguyên nhân bot bị văng khỏi game"""
    if str(acc_id) == "29389" or "captain" in str(acc_id).lower():
        return jsonify({
            "account_id": acc_id,
            "port": 29389,
            "launcher": "Dawn Launcher",
            "log_file": "C:\\Users\\Admin\\AppData\\Roaming\\.dawn\\profiles\\aha\\.minecraft\\logs\\latest.log",
            "disconnect_reason": "Mất kết nối: Internal Exception: java.net.SocketException: Connection reset (Server ngắt kết nối đột ngột)",
            "log_snippet": SAMPLE_DISCONNECT_LOG_CAPTAIN
        })
    elif str(acc_id) == "29390" or "toby" in str(acc_id).lower():
        return jsonify({
            "account_id": acc_id,
            "port": 29390,
            "launcher": "TLauncher",
            "log_file": "C:\\Users\\Admin\\AppData\\Roaming\\.tlauncher\\legacy\\Minecraft\\game\\logs\\latest.log",
            "disconnect_reason": "Chưa kết nối vào server (Đang ở sảnh chờ/Offline)",
            "log_snippet": "[22:00:10] [Render thread/INFO]: Game started in Singleplayer / Standby Mode.\n[22:00:12] [Render thread/INFO]: Waiting for user to connect to multiplayer server."
        })
    else:
        return jsonify({
            "account_id": acc_id,
            "port": 29388,
            "launcher": "PrismLauncher 1.21.11",
            "log_file": "C:\\Users\\Admin\\AppData\\Roaming\\PrismLauncher\\instances\\1.21.11\\.minecraft\\logs\\latest.log",
            "disconnect_reason": "Tài khoản đang ONLINE ổn định, không có lỗi văng game.",
            "log_snippet": "\n".join([f"[{h['time']}] [Render thread/INFO]: [CHAT] Balance +$18.3K (AutoSell / Farm)" for h in SAMPLE_BALANCE_HISTORY_12QC[:10]])
        })

@app.route("/api/status", methods=["GET"])
def api_bot_status():
    acc_param = request.args.get("acc")
    active_acc = CLOUD_ACCOUNTS_DATA[0]
    if acc_param:
        for a in CLOUD_ACCOUNTS_DATA:
            if str(a["id"]) == str(acc_param):
                active_acc = a
                break

    return jsonify({
        "status": "online",
        "cloud_mode": True,
        "gameConnected": active_acc.get("inGame", True),
        "accounts": CLOUD_ACCOUNTS_DATA,
        "activeAccountId": active_acc["id"],
        "totalAccounts": len(CLOUD_ACCOUNTS_DATA),
        "player": {
            "name": active_acc["name"],
            "health": active_acc.get("health", 20.0),
            "maxHealth": 20.0,
            "food": active_acc.get("food", 20),
            "server": active_acc.get("server", "paladi.net"),
            "balance": active_acc.get("balance", "0$")
        },
        "nearbyEntities": [],
        "history": []
    })

@app.route("/api/pay", methods=["POST", "GET"])
def api_pay_handler():
    target = request.args.get("target") or (request.json or {}).get("target", "")
    amount = request.args.get("amount") or (request.json or {}).get("amount", "")
    return jsonify({
        "success": True,
        "message": f"Đã ghi nhận lệnh /pay {target} {amount} từ điện thoại!",
        "target": target,
        "amount": amount
    })

@app.route("/api/server-info", methods=["GET"])
def api_server_info():
    return jsonify({
        "server": "Iuno Shop Cloud",
        "payouts_count": len(ELDORADO_CACHE.get("payouts", [])),
        "total_amount": ELDORADO_CACHE.get("summary", {}).get("total_amount", "0.00 USD"),
        "status": "online"
    })


def encrypt_obfuscated(raw: str) -> str:
    if not raw:
        return ""
    import hashlib
    key = hashlib.md5("paladi_provip_secret_salt_2026".encode('utf-8')).digest()
    raw_bytes = raw.encode('utf-8')
    xor_bytes = bytearray()
    for i, b in enumerate(raw_bytes):
        xor_bytes.append(b ^ key[i % len(key)])
    return xor_bytes.hex().upper()

def decrypt_obfuscated(encrypted: str) -> str:
    if not encrypted:
        return ""
    try:
        import hashlib
        xor_bytes = bytes.fromhex(encrypted)
        key = hashlib.md5("paladi_provip_secret_salt_2026".encode('utf-8')).digest()
        raw_bytes = bytearray()
        for i, b in enumerate(xor_bytes):
            raw_bytes.append(b ^ key[i % len(key)])
        return raw_bytes.decode('utf-8')
    except Exception:
        return ""

# --- Hệ thống mã hóa bảo vệ CSDL khỏi AI (Sử dụng khóa ADMIN_PASSWORD do admin tự đổi) ---
def get_db_seed() -> int:
    import hashlib
    # Sinh seed 64-bit từ ADMIN_PASSWORD
    h = hashlib.sha256(ADMIN_PASSWORD.encode('utf-8')).digest()
    return int.from_bytes(h[:8], byteorder='big') & 0xFFFFFFFFFFFFFFFF

def encrypt_db(raw: str) -> str:
    if not raw:
        return ""
    state = get_db_seed()
    multiplier = 2862933555777941757
    increment = 3037000493

    raw_bytes = raw.encode('utf-8')
    xor_bytes = bytearray()
    for b in raw_bytes:
        state = (state * multiplier + increment) & 0xFFFFFFFFFFFFFFFF
        key_byte = (state >> 56) & 0xFF
        xor_bytes.append(b ^ key_byte)
    return xor_bytes.hex().upper()

def decrypt_db(encrypted: str) -> str:
    if not encrypted:
        return ""
    try:
        xor_bytes = bytes.fromhex(encrypted)
        state = get_db_seed()
        multiplier = 2862933555777941757
        increment = 3037000493

        raw_bytes = bytearray()
        for b in xor_bytes:
            state = (state * multiplier + increment) & 0xFFFFFFFFFFFFFFFF
            key_byte = (state >> 56) & 0xFF
            raw_bytes.append(b ^ key_byte)
        return raw_bytes.decode('utf-8')
    except Exception:
        return ""

def decrypt_coord(val: str) -> str:
    if not val:
        return ""
    dec = decrypt_db(val)
    if dec:
        try:
            float(dec)
            return dec
        except ValueError:
            pass
    return val

def decrypt_text(val: str) -> str:
    if not val:
        return ""
    if not all(c in "0123456789ABCDEFabcdef" for c in val):
        return val
    dec = decrypt_db(val)
    if dec:
        return dec
    return val


def init_db():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute('''
        CREATE TABLE IF NOT EXISTS licenses (
            key TEXT PRIMARY KEY,
            hwid TEXT,
            duration_days INTEGER,
            created_at TEXT,
            activated_at TEXT,
            expires_at TEXT,
            flags TEXT,
            status TEXT
        )
    ''')
    c.execute('''
        CREATE TABLE IF NOT EXISTS orders (
            order_id TEXT PRIMARY KEY,
            plan_id TEXT,
            amount INTEGER,
            status TEXT,
            created_key TEXT,
            created_at TEXT
        )
    ''')
    c.execute('''
        CREATE TABLE IF NOT EXISTS coordinates (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            hwid TEXT,
            x TEXT,
            y TEXT,
            z TEXT,
            world TEXT,
            server TEXT,
            details TEXT,
            created_at TEXT,
            license_key TEXT
        )
    ''')
    # Thêm cột license_key nếu bảng coordinates đã tồn tại nhưng chưa có cột này
    try:
        c.execute("ALTER TABLE coordinates ADD COLUMN license_key TEXT")
        conn.commit()
    except sqlite3.OperationalError:
        pass

    # Tự động cập nhật cột license_key cho các tọa độ cũ dựa trên HWID hiện tại
    try:
        c.execute('''
            UPDATE coordinates
            SET license_key = (
                SELECT key FROM licenses
                WHERE licenses.hwid = coordinates.hwid
                LIMIT 1
            )
            WHERE license_key IS NULL OR license_key = ''
        ''')
        conn.commit()
    except Exception as e:
        print("Migration error:", e)

    conn.commit()
    conn.close()

def get_db():
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    return conn

init_db()

# Clean up expired licenses
def check_expirations():
    conn = get_db()
    c = conn.cursor()
    now = datetime.now()
    c.execute("SELECT key, expires_at FROM licenses WHERE status = 'ACTIVE'")
    rows = c.fetchall()
    for row in rows:
        exp_str = row['expires_at']
        if exp_str:
            try:
                exp_date = datetime.strptime(exp_str, "%Y-%m-%d %H:%M:%S")
                if now > exp_date:
                    c.execute("UPDATE licenses SET status = 'EXPIRED' WHERE key = ?", (row['key'],))
            except Exception:
                pass
    conn.commit()
    conn.close()

# Generate random keys
def generate_key_string(prefix="PLD"):
    chars = string.ascii_uppercase + string.digits
    rand_str = ''.join(random.choices(chars, k=16))
    return f"{prefix}-{rand_str[:4]}-{rand_str[4:8]}-{rand_str[8:12]}-{rand_str[12:]}"

# Modern Dark Violet/Glassmorphism Base HTML Layout with landing page sections
LANDING_CSS = """
:root {
    --bg-dark: #05020c;
    --bg-card: rgba(14, 6, 29, 0.7);
    --bg-card-hover: rgba(22, 10, 45, 0.85);
    --accent: #9d4edd;
    --accent-glow: rgba(157, 78, 221, 0.4);
    --accent-hover: #c77dff;
    --text-main: #f3e8ff;
    --text-sub: #b18cfc;
    --border: rgba(157, 78, 221, 0.25);
    --border-hover: rgba(157, 78, 221, 0.6);
    --shadow: 0 15px 35px rgba(0, 0, 0, 0.6);
    --neon-glow: 0 0 25px rgba(157, 78, 221, 0.65);
    --success: #2ecc71;
    --success-border: rgba(46, 204, 113, 0.3);
    --success-bg: rgba(46, 204, 113, 0.1);
    --error: #e74c3c;
    --error-border: rgba(231, 76, 60, 0.3);
    --error-bg: rgba(231, 76, 60, 0.1);
}

* {
    box-sizing: border-box;
    scroll-behavior: smooth;
}

body {
    background-color: var(--bg-dark);
    color: var(--text-main);
    font-family: 'Outfit', 'Inter', sans-serif;
    margin: 0;
    padding: 0;
    min-height: 100vh;
    background-attachment: fixed;
    position: relative;
    overflow-x: hidden;
}

/* Animations */
@keyframes float {
    0% { transform: translateY(0px); }
    50% { transform: translateY(-8px); }
    100% { transform: translateY(0px); }
}

@keyframes pulse {
    0% { transform: scale(1); box-shadow: 0 0 15px var(--accent-glow); }
    50% { transform: scale(1.02); box-shadow: 0 0 28px rgba(157, 78, 221, 0.85); }
    100% { transform: scale(1); box-shadow: 0 0 15px var(--accent-glow); }
}

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(15px); }
    to { opacity: 1; transform: translateY(0); }
}

.fade-in {
    animation: fadeIn 0.8s ease forwards;
}

/* Starfield Background */
.stars-container {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: -10;
    pointer-events: none;
    overflow: hidden;
}

.star {
    position: absolute;
    background-color: #fff;
    border-radius: 50%;
    animation: twinkle 3s infinite ease-in-out;
}

@keyframes twinkle {
    0%, 100% { opacity: 0.2; transform: scale(0.8); }
    50% { opacity: 1; transform: scale(1.2); }
}

.nebula-purple {
    position: fixed;
    top: -20%;
    left: -20%;
    width: 80%;
    height: 80%;
    background: radial-gradient(circle, rgba(138, 43, 226, 0.12) 0%, transparent 70%);
    z-index: -9;
    pointer-events: none;
    animation: rotateCloud 120s linear infinite;
}

.nebula-blue {
    position: fixed;
    bottom: -20%;
    right: -20%;
    width: 80%;
    height: 80%;
    background: radial-gradient(circle, rgba(99, 102, 241, 0.15) 0%, transparent 70%);
    z-index: -9;
    pointer-events: none;
    animation: rotateCloud 180s linear infinite;
}

@keyframes rotateCloud {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
}

/* Navbar */
.navbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 15px 8%;
    background: rgba(8, 3, 21, 0.85);
    backdrop-filter: blur(12px);
    border-bottom: 1px solid var(--border);
    position: sticky;
    top: 0;
    z-index: 100;
    transition: all 0.3s ease;
}

.logo-container {
    display: flex;
    align-items: center;
    gap: 10px;
}

.logo-icon {
    font-size: 1.8rem;
    animation: float 4s ease-in-out infinite;
}

.logo {
    font-size: 1.6rem;
    font-weight: 800;
    background: linear-gradient(135deg, #c084fc, #818cf8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: 1px;
}

.nav-links {
    display: flex;
    gap: 25px;
}

.nav-links a {
    color: var(--text-main);
    text-decoration: none;
    font-size: 0.95rem;
    font-weight: 600;
    position: relative;
    padding: 5px 0;
    transition: color 0.3s ease;
}

.nav-links a::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 0;
    height: 2px;
    background: linear-gradient(90deg, #c084fc, #818cf8);
    transition: width 0.3s ease;
}

.nav-links a:hover {
    color: #c084fc;
}

.nav-links a:hover::after {
    width: 100%;
}

/* Hero Section */
.hero-section-wrapper {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 50px;
    padding: 140px 8% 80px 8%;
    max-width: 1200px;
    margin: 0 auto;
}

.hero-section-content {
    flex: 1.2;
    text-align: left;
}

.badge-premium {
    display: inline-flex;
    align-items: center;
    background: rgba(168, 85, 247, 0.15);
    border: 1px solid rgba(168, 85, 247, 0.4);
    color: #c084fc;
    padding: 6px 14px;
    border-radius: 9999px;
    font-size: 0.85rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    margin-bottom: 20px;
    text-transform: uppercase;
}

h1.hero-title {
    font-size: 4rem;
    line-height: 1.15;
    margin-bottom: 20px;
    font-weight: 900;
    background: linear-gradient(135deg, #f3e8ff 30%, #c084fc 70%, #6366f1 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    text-shadow: 0 0 50px rgba(168, 85, 247, 0.25);
}

.hero-desc {
    font-size: 1.25rem;
    color: rgba(243, 232, 255, 0.75);
    margin-bottom: 45px;
    line-height: 1.65;
    max-width: 800px;
}

.hero-buttons {
    display: flex;
    gap: 20px;
    flex-wrap: wrap;
}

/* Waifu Card 3D Showcase */
.waifu-showcase-container {
    flex: 0.8;
    display: flex;
    justify-content: center;
    align-items: center;
    perspective: 1000px;
    width: 100%;
}

.waifu-card-3d {
    width: 320px;
    height: 400px;
    background: rgba(18, 9, 39, 0.45);
    border: 1px solid rgba(168, 85, 247, 0.35);
    border-radius: 24px;
    position: relative;
    transform-style: preserve-3d;
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5), 0 0 30px rgba(138, 43, 226, 0.2);
    transition: transform 0.1s ease, box-shadow 0.3s ease;
    overflow: hidden;
    cursor: pointer;
    display: flex;
    justify-content: center;
    align-items: center;
}

.waifu-card-3d::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(135deg, rgba(168, 85, 247, 0.1) 0%, transparent 60%, rgba(99, 102, 241, 0.15) 100%);
    pointer-events: none;
    border-radius: 24px;
}

.waifu-image-wrapper {
    width: 100%;
    height: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
    transform: translateZ(50px) scale(0.95);
    transition: transform 0.3s ease;
    animation: waifu-float 4s ease-in-out infinite;
}

.waifu-image {
    max-width: 90%;
    max-height: 90%;
    object-fit: contain;
    border-radius: 16px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
}

.waifu-badge {
    position: absolute;
    bottom: 20px;
    left: 50%;
    transform: translateX(-50%) translateZ(40px);
    background: rgba(8, 3, 21, 0.85);
    border: 1px solid var(--border);
    padding: 6px 16px;
    border-radius: 20px;
    color: var(--text-main);
    font-size: 0.85rem;
    font-weight: 700;
    white-space: nowrap;
    box-shadow: 0 5px 15px rgba(0, 0, 0, 0.4);
    letter-spacing: 1px;
}

@keyframes waifu-float {
    0%, 100% { transform: translateZ(50px) translateY(0) scale(0.95); }
    50% { transform: translateZ(50px) translateY(-12px) scale(0.98); }
}

@media (max-width: 900px) {
    .hero-section-wrapper {
        flex-direction: column-reverse;
        padding-top: 100px;
        gap: 40px;
        text-align: center;
    }
    .hero-section-content {
        text-align: center;
    }
    .hero-title {
        text-align: center !important;
    }
    .hero-desc {
        text-align: center !important;
        margin: 0 auto 35px auto !important;
    }
    .hero-buttons {
        justify-content: center !important;
    }
}

/* Buttons */
.btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 15px 30px;
    background: linear-gradient(135deg, #7b2cbf, #8a2be2);
    border: none;
    border-radius: 12px;
    color: #fff;
    font-size: 1rem;
    font-weight: 700;
    text-decoration: none;
    cursor: pointer;
    box-shadow: var(--shadow);
    transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn:hover {
    background: linear-gradient(135deg, #8a2be2, #a14dff);
    box-shadow: var(--neon-glow);
    transform: translateY(-3px);
}

.btn-secondary {
    background: rgba(18, 9, 39, 0.6);
    border: 1px solid var(--border);
}

.btn-secondary:hover {
    background: rgba(138, 43, 226, 0.15);
    border-color: var(--accent);
}

.btn-success {
    background: linear-gradient(135deg, #2ecc71, #27ae60);
    box-shadow: 0 10px 20px rgba(46, 204, 113, 0.2);
}

.btn-success:hover {
    background: linear-gradient(135deg, #27ae60, #2ecc71);
    box-shadow: 0 12px 25px rgba(46, 204, 113, 0.4);
    transform: translateY(-2px);
}

/* Client Showcase Grid (Tính Năng) */
.section-title {
    text-align: center;
    margin: 80px auto 40px auto;
    max-width: 700px;
}

.section-title h2 {
    font-size: 2.5rem;
    font-weight: 800;
    margin-bottom: 12px;
    background: linear-gradient(135deg, #b18cfc, #e0aaff);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}

.section-title p {
    color: var(--text-sub);
    font-size: 1.05rem;
}

.features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 30px;
    padding: 0 8%;
    max-width: 1200px;
    margin: 0 auto 60px auto;
}

.feature-card {
    background: var(--bg-card);
    backdrop-filter: blur(12px);
    border: 1px solid var(--border);
    border-radius: 18px;
    padding: 35px 30px;
    box-shadow: var(--shadow);
    transition: all 0.3s ease;
    position: relative;
    overflow: hidden;
}

.feature-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: linear-gradient(180deg, rgba(138, 43, 226, 0.05) 0%, transparent 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
}

.feature-card:hover {
    transform: translateY(-6px);
    border-color: var(--border-hover);
    box-shadow: var(--neon-glow);
}

.feature-card:hover::before {
    opacity: 1;
}

.feature-icon-wrapper {
    width: 60px;
    height: 60px;
    background: rgba(138, 43, 226, 0.15);
    border: 1px solid rgba(138, 43, 226, 0.3);
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 2.2rem;
    margin-bottom: 22px;
    color: var(--text-sub);
}

.feature-title {
    font-size: 1.35rem;
    font-weight: 700;
    margin-bottom: 12px;
    color: var(--text-main);
}

.feature-desc {
    font-size: 0.95rem;
    color: rgba(243, 232, 255, 0.7);
    line-height: 1.6;
}

/* Pricing Grid */
.pricing-section {
    padding: 40px 8% 60px 8%;
    max-width: 1200px;
    margin: 0 auto;
}

.pricing-grid {
    display: flex;
    justify-content: center;
    gap: 30px;
    flex-wrap: wrap;
    margin-top: 40px;
}

.pricing-card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: 24px;
    padding: 45px 35px;
    width: 320px;
    box-shadow: var(--shadow);
    transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    position: relative;
}

.pricing-card:hover {
    transform: translateY(-8px);
    border-color: var(--border-hover);
    box-shadow: var(--neon-glow);
}

.pricing-card.popular {
    border-color: var(--accent);
    background: linear-gradient(180deg, rgba(28, 12, 59, 0.8) 0%, rgba(18, 9, 39, 0.8) 100%);
    box-shadow: 0 15px 35px rgba(138, 43, 226, 0.2), 0 0 15px rgba(138, 43, 226, 0.3);
}

.popular-badge {
    position: absolute;
    top: -15px;
    left: 50%;
    transform: translateX(-50%);
    background: linear-gradient(90deg, #c084fc, #6366f1);
    padding: 6px 16px;
    border-radius: 9999px;
    font-size: 0.8rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    color: white;
    text-transform: uppercase;
    box-shadow: 0 5px 15px rgba(138, 43, 226, 0.4);
}

.plan-name {
    font-size: 1.3rem;
    font-weight: 700;
    color: var(--text-sub);
    margin-bottom: 12px;
}

.plan-price-wrapper {
    margin-bottom: 25px;
}

.plan-price {
    font-size: 2.6rem;
    font-weight: 800;
    color: #fff;
}

.plan-period {
    font-size: 0.9rem;
    color: rgba(243, 232, 255, 0.6);
    margin-left: 4px;
}

.plan-features {
    list-style: none;
    padding: 0;
    margin: 0 0 35px 0;
    text-align: left;
}

.plan-features li {
    margin-bottom: 14px;
    font-size: 0.95rem;
    color: rgba(243, 232, 255, 0.85);
    display: flex;
    align-items: center;
    gap: 10px;
}

.plan-features li::before {
    content: "✓";
    color: var(--success);
    font-weight: 900;
    font-size: 1.1rem;
}

.plan-features li.disabled {
    color: rgba(243, 232, 255, 0.4);
}

.plan-features li.disabled::before {
    content: "✗";
    color: var(--error);
}

/* Purchase Section ("Chỗ Mua & Hướng Dẫn") */
.purchase-section {
    padding: 60px 8%;
    max-width: 1200px;
    margin: 0 auto;
}

.purchase-grid {
    display: grid;
    grid-template-columns: 1.2fr 1fr;
    gap: 40px;
    margin-top: 40px;
}

@media (max-width: 850px) {
    .purchase-grid {
        grid-template-columns: 1fr;
    }
}

.instruction-timeline {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: 20px;
    padding: 40px;
    box-shadow: var(--shadow);
}

.timeline-step {
    display: flex;
    gap: 20px;
    margin-bottom: 30px;
    position: relative;
}

.timeline-step:last-child {
    margin-bottom: 0;
}

.timeline-step:not(:last-child)::after {
    content: '';
    position: absolute;
    left: 20px;
    top: 40px;
    bottom: -30px;
    width: 2px;
    background: rgba(138, 43, 226, 0.15);
}

.step-num {
    width: 40px;
    height: 40px;
    background: linear-gradient(135deg, #7b2cbf, #8a2be2);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    color: white;
    font-size: 1.1rem;
    flex-shrink: 0;
    box-shadow: 0 0 10px rgba(138, 43, 226, 0.4);
}

.step-content h3 {
    margin: 0 0 8px 0;
    font-size: 1.15rem;
    font-weight: 700;
    color: var(--text-main);
}

.step-content p {
    margin: 0;
    font-size: 0.95rem;
    color: rgba(243, 232, 255, 0.7);
    line-height: 1.5;
}

.payment-card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: 20px;
    padding: 40px;
    box-shadow: var(--shadow);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
}

.payment-method-box {
    background: rgba(7, 2, 17, 0.4);
    border: 1px solid rgba(138, 43, 226, 0.15);
    border-radius: 12px;
    padding: 20px;
    margin-bottom: 20px;
    position: relative;
    overflow: hidden;
}

.payment-method-box::before {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 80px;
    height: 80px;
    background: radial-gradient(circle, rgba(138, 43, 226, 0.08) 0%, transparent 70%);
}

.payment-title {
    font-size: 1.05rem;
    font-weight: 700;
    color: #e0aaff;
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.payment-detail {
    font-size: 0.95rem;
    margin-bottom: 8px;
    color: rgba(243, 232, 255, 0.85);
}

.payment-detail span.label {
    color: var(--text-sub);
    font-size: 0.85rem;
    text-transform: uppercase;
}

.payment-detail span.val {
    font-family: monospace;
    font-weight: 700;
    color: #fff;
    background: rgba(138, 43, 226, 0.1);
    padding: 2px 8px;
    border-radius: 4px;
    border: 1px solid rgba(138, 43, 226, 0.15);
}

.copy-btn {
    background: transparent;
    border: none;
    color: var(--text-sub);
    cursor: pointer;
    margin-left: 6px;
    font-size: 0.8rem;
    transition: color 0.2s;
}

.copy-btn:hover {
    color: #fff;
}

/* Activation Portal */
.portal-section {
    padding: 60px 8% 80px 8%;
    display: flex;
    justify-content: center;
    align-items: center;
}

.portal-container {
    background: var(--bg-card);
    backdrop-filter: blur(16px);
    border: 1px solid var(--border);
    border-radius: 24px;
    padding: 50px 40px;
    width: 100%;
    max-width: 540px;
    box-shadow: var(--shadow);
    text-align: center;
    position: relative;
}

.portal-container::after {
    content: '';
    position: absolute;
    top: -2px;
    left: -2px;
    right: -2px;
    bottom: -2px;
    border-radius: 26px;
    background: linear-gradient(135deg, rgba(138, 43, 226, 0.4), transparent, rgba(99, 102, 241, 0.3));
    z-index: -1;
    pointer-events: none;
}

/* Alerts */
.alert {
    padding: 14px 20px;
    border-radius: 12px;
    font-size: 0.95rem;
    margin-bottom: 25px;
    text-align: left;
    display: flex;
    align-items: flex-start;
    gap: 12px;
    line-height: 1.5;
}

.alert-error {
    background: var(--error-bg);
    border: 1px solid var(--error-border);
    color: var(--error);
}

.alert-success {
    background: var(--success-bg);
    border: 1px solid var(--success-border);
    color: var(--success);
}

/* Form Styles */
.form-group {
    text-align: left;
    margin-bottom: 24px;
}

label {
    display: block;
    font-size: 0.85rem;
    color: var(--text-sub);
    margin-bottom: 8px;
    text-transform: uppercase;
    letter-spacing: 1.5px;
    font-weight: 700;
}

input[type="text"], input[type="password"], select {
    width: 100%;
    padding: 14px 18px;
    background: rgba(7, 2, 20, 0.7);
    border: 1px solid var(--border);
    border-radius: 12px;
    color: var(--text-main);
    font-size: 1rem;
    box-sizing: border-box;
    outline: none;
    transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

input[type="text"]:focus, input[type="password"]:focus, select:focus {
    border-color: var(--accent-hover);
    box-shadow: var(--neon-glow);
    background: rgba(7, 2, 20, 0.9);
}

.download-card {
    background: rgba(46, 204, 113, 0.08);
    border: 1px solid rgba(46, 204, 113, 0.25);
    border-radius: 16px;
    padding: 30px;
    margin-top: 25px;
    animation: fadeIn 0.5s ease forwards;
}

/* Folder Tree Showcase */
.folder-tree {
    background: rgba(7, 2, 17, 0.6);
    border: 1px solid rgba(138, 43, 226, 0.15);
    border-radius: 10px;
    padding: 15px 20px;
    text-align: left;
    font-family: monospace;
    font-size: 0.85rem;
    color: #e0aaff;
    margin: 15px 0;
    line-height: 1.4;
}

/* Toast popup */
.toast {
    position: fixed;
    bottom: 30px;
    right: 30px;
    background: rgba(18, 9, 39, 0.9);
    border: 1px solid var(--accent);
    color: #fff;
    padding: 12px 24px;
    border-radius: 8px;
    font-size: 0.9rem;
    box-shadow: var(--shadow);
    z-index: 1000;
    opacity: 0;
    transform: translateY(20px);
    transition: all 0.3s ease;
    pointer-events: none;
}

.toast.show {
    opacity: 1;
    transform: translateY(0);
}

/* Admin / Login Container overrides */
.container {
    background: var(--bg-card);
    backdrop-filter: blur(16px);
    border: 1px solid var(--border);
    border-radius: 20px;
    padding: 40px;
    width: 100%;
    max-width: 450px;
    box-shadow: var(--shadow);
    text-align: center;
    margin: 15vh auto;
}

.subtitle {
    font-size: 0.9rem;
    color: var(--text-sub);
    margin-bottom: 35px;
}

.footer {
    border-top: 1px solid rgba(138, 43, 226, 0.15);
    padding: 40px 0;
    text-align: center;
    font-size: 0.85rem;
    color: rgba(243, 232, 255, 0.4);
    margin-top: 60px;
    background: rgba(5, 2, 12, 0.4);
}
"""
BASE_CSS = LANDING_CSS


@app.route("/license", methods=["GET", "POST"])
def license_page():
    message = None
    msg_type = "success"
    activated_key = None

    if request.method == "POST":
        key = request.form.get("key", "").strip()

        if not key:
            message = "Vui lòng nhập Key kích hoạt!"
            msg_type = "error"
        else:
            check_expirations()
            conn = get_db()
            c = conn.cursor()

            # Find key
            c.execute("SELECT * FROM licenses WHERE key = ?", (key,))
            row = c.fetchone()

            if not row:
                message = "Mã Key không tồn tại trên hệ thống!"
                msg_type = "error"
            elif row["status"] == "EXPIRED":
                message = "Mã Key này đã hết hạn sử dụng!"
                msg_type = "error"
            else:
                # Key is valid (either PENDING or ACTIVE).
                # The customer can download the license file containing the key.
                # Auto-binding will happen on the client.
                message = "Xác nhận Key hợp lệ! Ngài có thể tải tệp tin bản quyền ở bên dưới."
                msg_type = "success"
                activated_key = key

            conn.close()

    return render_template_string("""
    <!DOCTYPE html>
    <html>
    <head>
        <title>Paladi Client - Trợ Lý Minecraft Đẳng Cấp</title>
        <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800;900&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
        <style>
            """ + LANDING_CSS + """
            .download-btn {
                display: inline-flex;
                align-items: center;
                gap: 8px;
                background: #2ecc71;
                color: white;
                padding: 14px 28px;
                text-decoration: none;
                font-weight: 700;
                border-radius: 12px;
                margin-top: 15px;
                transition: all 0.3s ease;
                box-shadow: 0 10px 20px rgba(46, 204, 113, 0.2);
            }
            .download-btn:hover {
                background: #27ae60;
                box-shadow: 0 12px 25px rgba(46, 204, 113, 0.4);
                transform: translateY(-2px);
            }
        </style>
    </head>
    <body class="fade-in">
        <div class="nebula-purple"></div>
        <div class="nebula-blue"></div>

        <!-- Navbar -->
        <div class="navbar">
            <div class="logo-container">
                <span class="logo-icon">⭐</span>
                <div class="logo">PALADI CLIENT</div>
            </div>
            <div class="nav-links">
                <a href="#features">Giới Thiệu</a>
                <a href="#pricing">Bảng Giá</a>
                <a href="#buy-guide">Chỗ Mua</a>
                <a href="#portal">Kích Hoạt Portal</a>
            </div>
        </div>

        <!-- Hero Section -->
        <div class="hero-section-wrapper">
            <div class="hero-section-content">
                <div class="badge-premium">Phiên Bản Premium V2.5</div>
                <h1 class="hero-title">PALADI PREMIUM CLIENT</h1>
                <div class="hero-desc">
                    Chào mừng ngài đến với Vũ trụ Paladi Client - Sự kết hợp hoàn hảo giữa các công cụ hỗ trợ chiến đấu tối tân và Trợ Lý Trí Tuệ Nhân Tạo (AI) Chibi đột phá trong không gian Minecraft.
                </div>
                <div class="hero-buttons">
                    <a href="#portal" class="btn">Kích Hoạt Ngay</a>
                    <a href="#buy-guide" class="btn btn-secondary">Xem Hướng Dẫn Mua</a>
                </div>
            </div>

            <div class="waifu-showcase-container">
                <div class="waifu-card-3d">
                    <div class="waifu-image-wrapper">
                        <img src="/static/waifu_space.png" alt="AI Chibi Space Waifu" class="waifu-image">
                    </div>
                    <div class="waifu-badge">🤖 TRỢ LÝ AI CHIBI</div>
                </div>
            </div>
        </div>

        <!-- Features Section -->
        <div class="section-title" id="features">
            <h2>Hệ Thống Tính Năng Vượt Trội</h2>
            <p>Trải nghiệm những công nghệ độc quyền được phát triển riêng cho Paladi Client</p>
        </div>

        <div class="features-grid">
            <!-- Feature 1: Chibi AI -->
            <div class="feature-card">
                <div class="feature-icon-wrapper">🤖</div>
                <div class="feature-title">Trợ Lý Ảo AI Chibi</div>
                <div class="feature-desc">
                    Trò chuyện trực tiếp qua giọng nói hoặc văn bản tiếng Việt. AI Chibi có khả năng tự động chạy lệnh trong game, phân tích code và chẩn đoán lỗi stack trace trong tích tắc.
                </div>
            </div>

            <!-- Feature 2: Combat PvP -->
            <div class="feature-card">
                <div class="feature-icon-wrapper">⚔️</div>
                <div class="feature-title">Combat & PvP Arsenal</div>
                <div class="feature-desc">
                    Hỗ trợ tối đa cho sinh tồn và thi đấu với AutoTotem (phản ứng siêu tốc), KillAura, Flight, ESP, và các macro chiến đấu mượt mà, hạn chế giật lag.
                </div>
            </div>

            <!-- Feature 3: ClickGUI -->
            <div class="feature-card">
                <div class="feature-icon-wrapper">🎨</div>
                <div class="feature-title">ClickGUI Độc Quyền</div>
                <div class="feature-desc">
                    Bảng điều khiển trực quan mang phong cách tương lai, cho phép chỉnh sửa cấu hình, bật tắt các mod và tùy chọn phím tắt tiện lợi ngay trong game.
                </div>
            </div>

            <!-- Feature 4: HWID Auto-Bind -->
            <div class="feature-card">
                <div class="feature-icon-wrapper">🔒</div>
                <div class="feature-title">Tự Động Khóa HWID</div>
                <div class="feature-desc">
                    Hệ thống xác thực thông minh tự động liên kết Key với mã phần cứng (HWID) thiết bị vào lần chạy đầu tiên. Bảo mật tối đa, không cần sao chép HWID thủ công.
                </div>
            </div>
        </div>

        <!-- Pricing Section -->
        <div class="pricing-section" id="pricing">
            <div class="section-title">
                <h2>Bảng Giá Kích Hoạt</h2>
                <p>Chọn lựa gói thời gian phù hợp để mở khóa toàn bộ sức mạnh của Paladi Client</p>
            </div>

            <div class="pricing-grid">
                <!-- Plan 1 -->
                <div class="pricing-card">
                    <div>
                        <div class="plan-name">GÓI TRẠI NGHIỆM</div>
                        <div class="plan-price-wrapper">
                            <span class="plan-price">1 Ngày</span>
                        </div>
                        <ul class="plan-features">
                            <li>Toàn bộ tính năng PvP/Combat</li>
                            <li>Bảng điều khiển ClickGUI cao cấp</li>
                            <li>Tự động nhận diện HWID</li>
                            <li class="disabled">Trợ lý ảo AI Chibi (Khóa)</li>
                        </ul>
                    </div>
                    <a href="/buy/1day" class="btn btn-secondary">Mua Key 1 Ngày</a>
                </div>

                <!-- Plan 2 -->
                <div class="pricing-card popular">
                    <div class="popular-badge">Phổ Biến Nhất</div>
                    <div>
                        <div class="plan-name" style="color: #c084fc;">GÓI PHỔ THÔNG</div>
                        <div class="plan-price-wrapper">
                            <span class="plan-price">3 Ngày</span>
                        </div>
                        <ul class="plan-features">
                            <li>Toàn bộ tính năng PvP/Combat</li>
                            <li>Bảng điều khiển ClickGUI cao cấp</li>
                            <li>Tự động nhận diện HWID</li>
                            <li>Bao gồm Trợ lý ảo AI Chibi</li>
                            <li>Hỗ trợ nâng cấp cấu hình</li>
                        </ul>
                    </div>
                    <a href="/buy/3day" class="btn">Mua Ngay 3 Ngày</a>
                </div>

                <!-- Plan 3 -->
                <div class="pricing-card">
                    <div>
                        <div class="plan-name">GÓI PRO (LIFETIME)</div>
                        <div class="plan-price-wrapper">
                            <span class="plan-price">Vĩnh Viễn</span>
                        </div>
                        <ul class="plan-features">
                            <li>Toàn bộ tính năng PvP/Combat</li>
                            <li>Bảng điều khiển ClickGUI cao cấp</li>
                            <li>Tự động nhận diện HWID</li>
                            <li>Bao gồm Trợ lý ảo AI Chibi</li>
                            <li>Cập nhật tính năng trọn đời</li>
                            <li>Hỗ trợ Reset HWID miễn phí</li>
                        </ul>
                    </div>
                    <a href="/buy/lifetime" class="btn btn-secondary">Mua Bản Trọn Đời</a>
                </div>
            </div>
        </div>

        <!-- Buy Guide Section -->
        <div class="purchase-section" id="buy-guide">
            <div class="section-title">
                <h2>Địa Chỉ Mua Key & Hướng Dẫn</h2>
                <p>Nơi phân phối Key chính hãng và quy trình kích hoạt tệp bản quyền chi tiết</p>
            </div>

            <div class="purchase-grid">
                <!-- Timeline -->
                <div class="instruction-timeline">
                    <div class="timeline-step">
                        <div class="step-num">1</div>
                        <div class="step-content">
                            <h3>Lựa chọn gói Key</h3>
                            <p>Chọn gói trải nghiệm phù hợp với nhu cầu của ngài (1 Ngày, 3 Ngày hoặc Vĩnh Viễn).</p>
                        </div>
                    </div>

                    <div class="timeline-step">
                        <div class="step-num">2</div>
                        <div class="step-content">
                            <h3>Thanh toán & Nhận Key</h3>
                            <p>Thực hiện chuyển khoản theo thông tin thanh toán trực tiếp ở bên cạnh hoặc liên hệ kênh hỗ trợ Discord chính thức để mua. Sau khi thanh toán, ngài sẽ nhận được mã Key có dạng <code>PLD-XXXX-XXXX-...</code>.</p>
                        </div>
                    </div>

                    <div class="timeline-step">
                        <div class="step-num">3</div>
                        <div class="step-content">
                            <h3>Xác thực Key trên Website</h3>
                            <p>Kéo xuống phần <b>Kích Hoạt Portal</b> bên dưới, nhập mã Key đã nhận được và nhấn nút xác thực để kiểm tra.</p>
                        </div>
                    </div>

                    <div class="timeline-step">
                        <div class="step-num">4</div>
                        <div class="step-content">
                            <h3>Tải tệp license.txt</h3>
                            <p>Sau khi xác thực thành công, nhấn nút <b>Tải license.txt</b> để tải tệp cấu hình bản quyền xuống máy tính.</p>
                        </div>
                    </div>

                    <div class="timeline-step">
                        <div class="step-num">5</div>
                        <div class="step-content">
                            <h3>Đặt tệp cấu hình vào game</h3>
                            <p>Di chuyển tệp <code>license.txt</code> vừa tải vào thư mục game Minecraft theo đường dẫn: <code>.minecraft/paladi-client/license.txt</code>. Khởi động game và tận hưởng!</p>
                        </div>
                    </div>
                </div>

                <!-- Payment Details Card -->
                <div class="payment-card">
                    <div>
                        <h2 style="font-size: 1.5rem; margin-top: 0; margin-bottom: 25px; color: #fff;">Thông Tin Mua Hàng & Thanh Toán</h2>

                        <!-- MB Bank -->
                        <div class="payment-method-box">
                            <div class="payment-title">🏦 NGÂN HÀNG (MB BANK)</div>
                            <div class="payment-detail">
                                <span class="label">Số tài khoản:</span>
                                <span class="val" id="bank-acc">1234567890</span>
                                <button class="copy-btn" onclick="copyToClipboard('1234567890', 'Số tài khoản Ngân hàng')">📋 Copy</button>
                            </div>
                            <div class="payment-detail">
                                <span class="label">Chủ tài khoản:</span>
                                <strong style="color: #fff;">NGUYỄN VĂN A</strong>
                            </div>
                            <div class="payment-detail">
                                <span class="label">Nội dung CK:</span>
                                <span class="val" id="bank-desc">Paladi [Tên của bạn]</span>
                                <button class="copy-btn" onclick="copyToClipboard('Paladi ', 'Nội dung chuyển khoản')">📋 Copy mẫu</button>
                            </div>
                        </div>

                        <!-- MoMo -->
                        <div class="payment-method-box">
                            <div class="payment-title">💸 VÍ ĐIỆN TỬ MOMO</div>
                            <div class="payment-detail">
                                <span class="label">Số điện thoại:</span>
                                <span class="val" id="momo-phone">0987654321</span>
                                <button class="copy-btn" onclick="copyToClipboard('0987654321', 'Số điện thoại MoMo')">📋 Copy</button>
                            </div>
                            <div class="payment-detail">
                                <span class="label">Chủ ví MoMo:</span>
                                <strong style="color: #fff;">NGUYỄN VĂN A</strong>
                            </div>
                        </div>

                        <!-- Discord -->
                        <div style="margin-top: 25px;">
                            <div style="font-size: 0.9rem; color: var(--text-sub); margin-bottom: 12px; text-align: center;">Hoặc liên hệ trực tiếp Admin để được hỗ trợ mua nhanh nhất:</div>
                            <a href="https://discord.gg/paladi-client" target="_blank" class="btn" style="width: 100%;">💬 THAM GIA DISCORD ĐỂ MUA KEY</a>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Portal Section -->
        <div class="portal-section" id="portal">
            <div class="portal-container">
                <h1 style="font-size: 2rem; margin-top: 0; margin-bottom: 8px; color: #fff;">CỔNG KÍCH HOẠT BẢN QUYỀN</h1>
                <div style="font-size: 0.95rem; color: var(--text-sub); margin-bottom: 30px;">Dán Key kích hoạt của ngài để tạo và tải tệp cấu hình bản quyền</div>

                {% if message %}
                    <div class="alert alert-{{ msg_type }}">
                        <div>
                            {% if msg_type == 'success' %}
                                <strong>✅ Thành công:</strong>
                            {% else %}
                                <strong>❌ Lỗi:</strong>
                            {% endif %}
                            {{ message }}
                        </div>
                    </div>
                {% endif %}

                {% if activated_key %}
                    <div class="download-card">
                        <div style="font-weight: 800; color: #2ecc71; margin-bottom: 10px; font-size: 1.1rem;">KÍCH HOẠT THÀNH CÔNG!</div>
                        <div style="font-size: 0.9rem; color: var(--text-sub); margin-bottom: 20px; line-height: 1.5;">
                            Mã Key đã được chấp thuận. Ngài hãy tải tệp <b>license.txt</b> bằng nút bên dưới và đặt nó vào đúng vị trí của trò chơi:
                        </div>

                        <div class="folder-tree">
                            📂 .minecraft<br>
                            └── 📂 paladi-client<br>
                            &nbsp;&nbsp;&nbsp;&nbsp;└── 📄 license.txt (Đặt tệp vào đây)
                        </div>

                        <a href="/download/{{ activated_key }}" class="download-btn">📥 Tải tệp license.txt</a>
                    </div>
                {% else %}
                    <form method="POST">
                        <div class="form-group">
                            <label for="key">Mã kích hoạt (Key)</label>
                            <input type="text" id="key" name="key" placeholder="Nhập Key (Ví dụ: PLD-ABCD-EFGH-...)" required autocomplete="off">
                        </div>
                        <button type="submit" class="btn btn-success" style="width: 100%; margin-top: 10px;">🔓 TẠO FILE BẢN QUYỀN</button>
                    </form>
                {% endif %}
            </div>
        </div>

        <!-- Footer -->
        <div class="footer">
            <p style="margin: 0 0 10px 0; font-size: 0.95rem; font-weight: 600; color: var(--text-sub);">&copy; 2026 Paladi Client Development. All Rights Reserved.</p>
            <p style="margin: 0; font-size: 0.8rem; color: rgba(243, 232, 255, 0.3);">Hệ thống quản lý giấy phép bản quyền. Thiết kế và vận hành bởi DragonGod655.</p>
        </div>

        <!-- JS for Toast, Copy, Starspawner, and 3D Waifu tilt -->
        <script>
            // Twinkling stars generator
            document.addEventListener("DOMContentLoaded", function() {
                const container = document.createElement("div");
                container.className = "stars-container";
                document.body.appendChild(container);

                for (let i = 0; i < 120; i++) {
                    const star = document.createElement("div");
                    star.className = "star";
                    const size = Math.random() * 2 + 1; // 1px to 3px
                    star.style.width = size + "px";
                    star.style.height = size + "px";
                    star.style.left = Math.random() * 100 + "vw";
                    star.style.top = Math.random() * 100 + "vh";
                    star.style.animationDelay = Math.random() * 3 + "s";
                    star.style.animationDuration = (Math.random() * 3 + 2) + "s";
                    star.style.opacity = Math.random();
                    container.appendChild(star);
                }
            });

            // 3D Waifu Card Tilt Effect
            document.addEventListener("DOMContentLoaded", function() {
                const card = document.querySelector(".waifu-card-3d");
                if (!card) return;

                const wrapper = document.querySelector(".waifu-image-wrapper");

                card.addEventListener("mousemove", function(e) {
                    const rect = card.getBoundingClientRect();
                    const x = e.clientX - rect.left;
                    const y = e.clientY - rect.top;

                    const width = rect.width;
                    const height = rect.height;

                    // Max tilt angle: 20 deg
                    const tiltX = ((y / height) - 0.5) * -30;
                    const tiltY = ((x / width) - 0.5) * 30;

                    card.style.transform = `rotateX(${tiltX}deg) rotateY(${tiltY}deg) scale(1.03)`;
                    card.style.boxShadow = `0 35px 70px rgba(0,0,0,0.6), 0 0 45px rgba(157, 78, 221, 0.4)`;

                    if (wrapper) {
                        const moveX = ((x / width) - 0.5) * 20;
                        const moveY = ((y / height) - 0.5) * 20;
                        wrapper.style.transform = `translateZ(80px) translateX(${moveX}px) translateY(${moveY}px) scale(1.02)`;
                    }
                });

                card.addEventListener("mouseleave", function() {
                    card.style.transform = "rotateX(0deg) rotateY(0deg) scale(1)";
                    card.style.boxShadow = "0 20px 50px rgba(0, 0, 0, 0.5), 0 0 30px rgba(138, 43, 226, 0.2)";
                    if (wrapper) {
                        wrapper.style.transform = "translateZ(50px) translateY(0) scale(0.95)";
                    }
                });
            });

            function copyToClipboard(text, label) {
                navigator.clipboard.writeText(text).then(function() {
                    showToast("Đã sao chép " + label + " vào bộ nhớ tạm!");
                }, function(err) {
                    const el = document.createElement('textarea');
                    el.value = text;
                    document.body.appendChild(el);
                    el.select();
                    document.execCommand('copy');
                    document.body.removeChild(el);
                    showToast("Đã sao chép " + label + " vào bộ nhớ tạm!");
                });
            }

            function showToast(message) {
                let toast = document.getElementById("toast");
                if (!toast) {
                    toast = document.createElement("div");
                    toast.id = "toast";
                    toast.className = "toast";
                    document.body.appendChild(toast);
                }
                toast.textContent = message;
                toast.className = "toast show";
                setTimeout(function() {
                    toast.className = "toast";
                }, 2500);
            }
        </script>
    </body>
    </html>
    """, message=message, msg_type=msg_type, activated_key=activated_key)


@app.route("/download/<key>")
def download_license(key):
    conn = get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM licenses WHERE key = ?", (key,))
    row = c.fetchone()
    conn.close()

    if not row:
        return "Yêu cầu không hợp lệ hoặc Key không tồn tại!", 400

    content = f"""# Paladi Client License File
# Expiry: {row['expires_at'] if row['expires_at'] else 'Not Activated Yet'}
{row['key']}
"""
    return Response(
        content,
        mimetype="text/plain",
        headers={"Content-disposition": "attachment; filename=license.txt"}
    )

@app.route("/sync", methods=["POST", "GET"])
def submit_coordinate():
    hwid = request.args.get("hwid", "").strip()
    session = request.args.get("session", "").strip()

    if not hwid:
        return "ERROR_NO_HWID"

    x, y, z, world, server, details = "0", "0", "0", "", "", ""

    if session:
        decrypted = decrypt_obfuscated(session)
        if decrypted:
            parts = decrypted.split("|")
            if len(parts) >= 6:
                x, y, z, world, server, details = parts[0], parts[1], parts[2], parts[3], parts[4], "|".join(parts[5:])
            elif len(parts) >= 5:
                x, y, z, world, server = parts[0], parts[1], parts[2], parts[3], parts[4]
    else:
        x = request.args.get("x", "0").strip()
        y = request.args.get("y", "0").strip()
        z = request.args.get("z", "0").strip()
        world = request.args.get("world", "").strip()
        server = request.args.get("server", "").strip()
        details = request.args.get("details", "").strip()

    enc_x = encrypt_db(x)
    enc_y = encrypt_db(y)
    enc_z = encrypt_db(z)
    enc_world = encrypt_db(world)
    enc_server = encrypt_db(server)
    enc_details = encrypt_db(details)

    conn = get_db()
    c = conn.cursor()

    # Tìm license_key tương ứng với HWID này
    c.execute("SELECT key FROM licenses WHERE hwid = ? ORDER BY activated_at DESC LIMIT 1", (hwid,))
    row = c.fetchone()
    license_key = row[0] if row else ""

    c.execute('''
        INSERT INTO coordinates (hwid, x, y, z, world, server, details, created_at, license_key)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', (hwid, enc_x, enc_y, enc_z, enc_world, enc_server, enc_details, datetime.now().strftime("%Y-%m-%d %H:%M:%S"), license_key))
    conn.commit()
    conn.close()
    return "SUCCESS"


# Automated HWID verification & binding API for the Java client
@app.route("/verify", methods=["GET"])
def verify_license():
    check_expirations()

    key = request.args.get("key", "").strip()
    hwid = request.args.get("hwid", "").strip()

    if not key or not hwid:
        return "INVALID"

    conn = get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM licenses WHERE key = ?", (key,))
    row = c.fetchone()

    if not row:
        conn.close()
        return "INVALID"

    status = row["status"]

    if status == "EXPIRED":
        conn.close()
        return "EXPIRED"

    if status == "PENDING":
        # First-run launch: Auto-bind the HWID and calculate expiration date
        now = datetime.now()
        expires_at = now + timedelta(days=row["duration_days"])
        expires_str = expires_at.strftime("%Y-%m-%d %H:%M:%S")
        activated_str = now.strftime("%Y-%m-%d %H:%M:%S")

        c.execute('''
            UPDATE licenses
            SET hwid = ?, activated_at = ?, expires_at = ?, status = 'ACTIVE'
            WHERE key = ?
        ''', (hwid, activated_str, expires_str, key))
        conn.commit()

        flags = row["flags"]
        conn.close()
        return "NO_CHIBI" if flags and "NO_CHIBI" in flags else "ALLOWED"

    if status == "ACTIVE":
        # Check if the hardware ID matches the bound ID
        if row["hwid"] == hwid:
            flags = row["flags"]
            conn.close()
            return "NO_CHIBI" if flags and "NO_CHIBI" in flags else "ALLOWED"
        else:
            conn.close()
            return "BLOCKED"

    conn.close()
    return "INVALID"

# Admin Login
@app.route("/admin/login", methods=["GET", "POST"])
def admin_login():
    error = None
    if request.method == "POST":
        password = request.form.get("password")
        if password == ADMIN_PASSWORD:
            session["admin"] = True
            return redirect(url_for("admin_dashboard"))
        else:
            error = "Mật khẩu không chính xác!"

    return render_template_string("""
    <!DOCTYPE html>
    <html>
    <head>
        <title>Đăng Nhập Admin</title>
        <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
        <style>
            """ + BASE_CSS + """
        </style>
    </head>
    <body>
        <div class="container">
            <h1>ADMIN LOGIN</h1>
            <div class="subtitle">Nhập mật khẩu quản trị để tiếp tục</div>

            {% if error %}
                <div class="alert alert-error">{{ error }}</div>
            {% endif %}

            <form method="POST">
                <div class="form-group">
                    <label for="password">Mật khẩu quản trị</label>
                    <input type="password" id="password" name="password" required>
                </div>
                <button type="submit" class="btn">ĐĂNG NHẬP</button>
            </form>
        </div>
    </body>
    </html>
    """, error=error)

@app.route("/admin/logout")
def admin_logout():
    session.pop("admin", None)
    return redirect(url_for("admin_login"))

# Admin Dashboard
@app.route("/admin", methods=["GET", "POST"])
def admin_dashboard():
    if not session.get("admin"):
        return redirect(url_for("admin_login"))

    check_expirations()
    conn = get_db()
    c = conn.cursor()

    if request.method == "POST":
        action = request.form.get("action")
        if action == "generate":
            duration = int(request.form.get("duration", 1))
            chibi_disabled = request.form.get("chibi_disabled") == "yes"
            flags = "NO_CHIBI" if chibi_disabled else ""

            prefix = request.form.get("prefix", "PLD").upper()
            if prefix not in ["PLD", "ASV"]:
                prefix = "PLD"

            new_key = generate_key_string(prefix)
            c.execute('''
                INSERT INTO licenses (key, hwid, duration_days, created_at, status, flags)
                VALUES (?, NULL, ?, ?, 'PENDING', ?)
            ''', (new_key, duration, datetime.now().strftime("%Y-%m-%d %H:%M:%S"), flags))
            conn.commit()

        elif action == "delete":
            key = request.form.get("key")
            c.execute("DELETE FROM licenses WHERE key = ?", (key,))
            conn.commit()

        elif action == "revoke":
            key = request.form.get("key")
            c.execute("UPDATE licenses SET status = 'EXPIRED' WHERE key = ?", (key,))
            conn.commit()

        elif action == "bind_hwid":
            key = request.form.get("key")
            hwid_to_bind = request.form.get("hwid", "").strip()
            if key and hwid_to_bind:
                now = datetime.now()
                c.execute("SELECT duration_days FROM licenses WHERE key = ?", (key,))
                key_row = c.fetchone()
                duration = key_row["duration_days"] if key_row else 1
                expires_at = now + timedelta(days=duration)
                expires_str = expires_at.strftime("%Y-%m-%d %H:%M:%S")
                activated_str = now.strftime("%Y-%m-%d %H:%M:%S")

                c.execute('''
                    UPDATE licenses
                    SET hwid = ?, activated_at = ?, expires_at = ?, status = 'ACTIVE'
                    WHERE key = ?
                ''', (hwid_to_bind, activated_str, expires_str, key))
                conn.commit()

        elif action == "reset_hwid":
            key = request.form.get("key")
            if key:
                c.execute('''
                    UPDATE licenses
                    SET hwid = NULL, activated_at = NULL, expires_at = NULL, status = 'PENDING'
                    WHERE key = ?
                ''', (key,))
                conn.commit()

    # Lấy tham số view_key từ URL
    view_key = request.args.get("view_key", "").strip()

    # Lấy toàn bộ licenses
    c.execute("SELECT * FROM licenses ORDER BY created_at DESC")
    raw_licenses = c.fetchall()
    licenses = [dict(row) for row in raw_licenses]

    coords = []
    if view_key:
        # Lấy tọa độ dựa trên cột license_key cố định vĩnh viễn
        c.execute('''
            SELECT * FROM coordinates
            WHERE license_key = ?
            ORDER BY created_at DESC
        ''', (view_key,))
        raw_coords = c.fetchall()
        for row in raw_coords:
            c_dict = dict(row)
            c_dict["license_key"] = view_key
            c_dict["x"] = decrypt_coord(c_dict.get("x", "0"))
            c_dict["y"] = decrypt_coord(c_dict.get("y", "0"))
            c_dict["z"] = decrypt_coord(c_dict.get("z", "0"))
            c_dict["world"] = decrypt_text(c_dict.get("world", ""))
            c_dict["server"] = decrypt_text(c_dict.get("server", ""))
            c_dict["details"] = decrypt_text(c_dict.get("details", ""))
            coords.append(c_dict)

    # --- Tính toán thống kê doanh thu và tỷ lệ bán / giờ cho Key được chọn ---
    stats = {}
    hourly_data = []
    if view_key and coords:
        import re
        from collections import defaultdict
        total_earned = 0.0
        sell_count = 0
        first_time = None
        last_time = None
        hourly_earnings = defaultdict(float)
        hourly_counts = defaultdict(int)

        def parse_money(money_str):
            if not money_str: return 0.0
            m = re.search(r'([\d\.]+)\s*([KMB]?)', money_str.upper())
            if not m: return 0.0
            val = float(m.group(1))
            suffix = m.group(2)
            if suffix == 'K': val *= 1000
            elif suffix == 'M': val *= 1000000
            elif suffix == 'B': val *= 1000000000
            return val

        for c in coords:
            det = c.get("details", "")
            earned_this = 0.0
            if "Bán thành công:" in det:
                m = re.search(r'Bán thành công:\s*([^\s\|]+)', det)
                if m:
                    earned_this = parse_money(m.group(1))
                    total_earned += earned_this
                    sell_count += 1
            try:
                t = datetime.strptime(c["created_at"], "%Y-%m-%d %H:%M:%S")
                if first_time is None or t < first_time: first_time = t
                if last_time is None or t > last_time: last_time = t
                hour_key = t.strftime("%Y-%m-%d %H:00")
                hourly_earnings[hour_key] += earned_this
                hourly_counts[hour_key] += 1
            except Exception:
                pass

        def fmt_money(amount):
            if amount >= 1000000000: return f"${amount / 1000000000.0:.2f}B"
            if amount >= 1000000: return f"${amount / 1000000.0:.2f}M"
            if amount >= 1000: return f"${amount / 1000.0:.2f}K"
            return f"${amount:.0f}"

        elapsed_str = "00 giờ 00 phút"
        rate_str = "$0 / giờ"
        last_hour_str = "$0"
        if first_time and last_time:
            diff = last_time - first_time
            seconds = diff.total_seconds()
            hours = seconds / 3600.0

            h_int = int(seconds // 3600)
            m_int = int((seconds % 3600) // 60)
            elapsed_str = f"{h_int:02d} giờ {m_int:02d} phút"

            rate_val = total_earned / max(hours, 0.0166)
            rate_str = f"{fmt_money(rate_val)} / giờ"

            # Doanh thu 1 giờ gần nhất
            now = datetime.now()
            one_hour_ago = now - timedelta(hours=1)
            last_hour_earned = 0.0
            for c in coords:
                try:
                    t = datetime.strptime(c["created_at"], "%Y-%m-%d %H:%M:%S")
                    if t >= one_hour_ago:
                        det = c.get("details", "")
                        if "Bán thành công:" in det:
                            m = re.search(r'Bán thành công:\s*([^\s\|]+)', det)
                            if m:
                                last_hour_earned += parse_money(m.group(1))
                except Exception:
                    pass
            last_hour_str = fmt_money(last_hour_earned)

        # Tạo dữ liệu biểu đồ theo giờ (sắp xếp theo thời gian)
        for hk in sorted(hourly_earnings.keys()):
            hourly_data.append({
                "hour": hk,
                "earned": hourly_earnings[hk],
                "earned_fmt": fmt_money(hourly_earnings[hk]),
                "count": hourly_counts[hk]
            })

        stats = {
            "total_earned": fmt_money(total_earned),
            "elapsed_time": elapsed_str,
            "rate_per_hour": rate_str,
            "sell_count": sell_count,
            "last_hour": last_hour_str,
            "total_logs": len(coords)
        }

    conn.close()

    # Reuse dashboard CSS styles from LANDING_CSS
    ADMIN_CSS = LANDING_CSS + """
    body {
        align-items: flex-start;
        padding: 40px 20px;
    }
    .admin-container {
        background: var(--bg-card);
        backdrop-filter: blur(16px);
        border: 1px solid var(--border);
        border-radius: 20px;
        padding: 40px;
        width: 100%;
        max-width: 1100px;
        box-shadow: var(--shadow);
        margin: 0 auto;
        box-sizing: border-box;
    }
    .header-bar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        border-bottom: 1px solid var(--border);
        padding-bottom: 20px;
        margin-bottom: 30px;
    }
    .logout-btn {
        background: transparent;
        border: 1px solid rgba(220, 53, 69, 0.5);
        color: #e74c3c;
        padding: 8px 16px;
        border-radius: 8px;
        text-decoration: none;
        font-weight: 600;
        font-size: 0.9rem;
        transition: all 0.3s ease;
    }
    .logout-btn:hover {
        background: #e74c3c;
        color: white;
    }
    .dashboard-layout {
        display: grid;
        grid-template-columns: 1fr 2.5fr;
        gap: 40px;
    }
    @media (max-width: 850px) {
        .dashboard-layout {
            grid-template-columns: 1fr;
        }
    }
    .card {
        background: rgba(7, 2, 17, 0.4);
        border: 1px solid var(--border);
        border-radius: 12px;
        padding: 24px;
    }
    .stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 20px;
        margin-bottom: 30px;
    }
    .stat-box {
        background: rgba(157, 78, 221, 0.08);
        border: 1px solid rgba(157, 78, 221, 0.2);
        border-radius: 8px;
        padding: 16px;
        text-align: center;
        transition: all 0.3s ease;
    }
    .stat-box:hover {
        border-color: var(--accent);
        background: rgba(157, 78, 221, 0.12);
        transform: translateY(-2px);
    }
    .stat-label {
        font-size: 0.8rem;
        color: var(--text-sub);
        text-transform: uppercase;
        margin-bottom: 6px;
        letter-spacing: 0.5px;
    }
    .stat-val {
        font-size: 1.4rem;
        font-weight: 700;
        color: var(--accent-hover);
    }
    .card-title {
        font-size: 1.2rem;
        font-weight: 600;
        margin-bottom: 20px;
        color: var(--text-sub);
        border-bottom: 1px solid rgba(138, 43, 226, 0.15);
        padding-bottom: 8px;
    }
    .table-container {
        overflow-x: auto;
    }
    .blurred-text {
        filter: blur(5px);
        transition: filter 0.2s ease;
        cursor: pointer;
        user-select: none;
    }
    .blurred-text:hover, .blurred-text.revealed {
        filter: blur(0);
        user-select: auto;
        background: rgba(157, 78, 221, 0.1);
        border-radius: 4px;
        padding: 2px 4px;
    }
    table {
        width: 100%;
        border-collapse: collapse;
        text-align: left;
        font-size: 0.9rem;
    }
    th, td {
        padding: 12px;
        border-bottom: 1px solid rgba(138, 43, 226, 0.15);
    }
    th {
        color: var(--text-sub);
        font-weight: 600;
        text-transform: uppercase;
        font-size: 0.8rem;
        letter-spacing: 1px;
    }
    .status-badge {
        padding: 4px 8px;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
    }
    .status-pending {
        background: rgba(241, 196, 15, 0.2);
        color: #f1c40f;
        border: 1px solid rgba(241, 196, 15, 0.4);
    }
    .status-active {
        background: rgba(46, 204, 113, 0.2);
        color: #2ecc71;
        border: 1px solid rgba(46, 204, 113, 0.4);
    }
    .status-expired {
        background: rgba(220, 53, 69, 0.2);
        color: #e74c3c;
        border: 1px solid rgba(220, 53, 69, 0.4);
    }
    .action-btn {
        background: transparent;
        border: 1px solid var(--border);
        color: var(--text-main);
        padding: 4px 8px;
        border-radius: 4px;
        cursor: pointer;
        font-size: 0.8rem;
        transition: all 0.3s ease;
    }
    .action-btn-danger {
        border-color: rgba(220, 53, 69, 0.5);
        color: #e74c3c;
    }
    .action-btn-danger:hover {
        background: #e74c3c;
        color: white;
    }
    code {
        background: rgba(7, 2, 17, 0.8);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: monospace;
        color: #e0aaff;
    }
    """

    # We reuse LANDING_CSS for form elements in rendering
    # Set BASE_CSS placeholder to satisfy admin templating
    global BASE_CSS
    BASE_CSS = ADMIN_CSS

    return render_template_string("""
    <!DOCTYPE html>
    <html>
    <head>
        <title>Quản Trị Bản Quyền Paladi Client</title>
        <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
        <style>
            """ + ADMIN_CSS + """
        </style>
    </head>
    <body>
        <div class="admin-container">
            <div class="header-bar">
                <div>
                    <h1 style="margin: 0; font-size: 1.8rem; background: linear-gradient(135deg, #a14dff, #e0aaff); -webkit-background-clip: text; -webkit-text-fill-color: transparent;">ADMIN DASHBOARD</h1>
                    <div style="font-size: 0.85rem; color: var(--text-sub);">Hệ thống tạo và quản lý key bản quyền Paladi Client</div>
                </div>
                <a href="/admin/logout" class="logout-btn">ĐĂNG XUẤT</a>
            </div>

            <div class="dashboard-layout">
                <!-- Generate Section -->
                <div class="card">
                    <div class="card-title">Tạo mã kích hoạt mới (Generate Key)</div>
                    <form method="POST">
                        <input type="hidden" name="action" value="generate">
                        <div class="form-group">
                            <label for="prefix">Loại Key (Sản phẩm)</label>
                            <select id="prefix" name="prefix">
                                <option value="PLD">Paladi Client (PLD-)</option>
                                <option value="ASV">AutoSell VIP (ASV-)</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="duration">Thời hạn Key</label>
                            <select id="duration" name="duration">
                                <option value="1">1 ngày (24 Giờ)</option>
                                <option value="3">3 ngày (72 Giờ)</option>
                                <option value="7">7 ngày</option>
                                <option value="30">30 ngày</option>
                                <option value="365">1 năm</option>
                                <option value="9999">Vĩnh viễn (Lifetime)</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="chibi_disabled">Quyền hạn tính năng</label>
                            <select id="chibi_disabled" name="chibi_disabled">
                                <option value="no">Bình thường (Đầy đủ tính năng)</option>
                                <option value="yes">KHÓA tính năng Chibi (NO_CHIBI)</option>
                            </select>
                        </div>
                        <button type="submit" class="btn">TẠO KEY MỚI</button>
                    </form>
                </div>

                <!-- Keys Table -->
                <div class="card">
                    <div class="card-title">Danh sách mã kích hoạt (Total: {{ licenses|length }})</div>
                    <div class="table-container">
                        <table>
                            <thead>
                                <tr>
                                    <th>Mã kích hoạt (Key)</th>
                                    <th>Thời hạn</th>
                                    <th>Quyền hạn</th>
                                    <th>Trạng thái</th>
                                    <th>Thiết bị (HWID)</th>
                                    <th>Hạn dùng</th>
                                    <th>Tra cứu tọa độ</th>
                                    <th>Hành động</th>
                                </tr>
                            </thead>
                            <tbody>
                                {% for row in licenses %}
                                <tr>
                                    <td><code>{{ row['key'] }}</code></td>
                                    <td>{{ row['duration_days'] }} ngày</td>
                                    <td>
                                        {% if row['flags'] == 'NO_CHIBI' %}
                                            <span style="color: #e74c3c;">Khóa Chibi</span>
                                        {% else %}
                                            <span style="color: #2ecc71;">Đầy đủ</span>
                                        {% endif %}
                                    </td>
                                    <td>
                                        {% if row['status'] == 'PENDING' %}
                                            <span class="status-badge status-pending">Chưa dùng</span>
                                        {% elif row['status'] == 'ACTIVE' %}
                                            <span class="status-badge status-active">Hoạt động</span>
                                        {% else %}
                                            <span class="status-badge status-expired">Hết hạn</span>
                                        {% endif %}
                                    </td>
                                    <td>
                                        {% if row['hwid'] %}
                                            <div style="display: flex; flex-direction: column; gap: 4px;">
                                                <span style="font-size: 0.8rem; font-family: monospace; color: #a8dadc;" title="{{ row['hwid'] }}">{{ row['hwid'][:10] }}...</span>
                                                <form method="POST" style="display: inline; margin: 0;" onsubmit="return confirm('Gỡ HWID này khỏi Key? Người dùng sẽ phải kích hoạt lại.');">
                                                    <input type="hidden" name="action" value="reset_hwid">
                                                    <input type="hidden" name="key" value="{{ row['key'] }}">
                                                    <button type="submit" style="background: rgba(231, 76, 60, 0.15); border: 1px solid #e74c3c; color: #e74c3c; padding: 2px 8px; border-radius: 4px; font-size: 0.7rem; cursor: pointer; transition: all 0.2s;">Gỡ HWID</button>
                                                </form>
                                            </div>
                                        {% else %}
                                            <div style="display: flex; flex-direction: column; gap: 6px;">
                                                <form method="POST" style="display: flex; align-items: center; gap: 4px; margin: 0;">
                                                    <input type="hidden" name="action" value="bind_hwid">
                                                    <input type="hidden" name="key" value="{{ row['key'] }}">
                                                    <input type="text" name="hwid" placeholder="HWID..." required style="background: rgba(0,0,0,0.5); border: 1px solid var(--border); color: #fff; padding: 4px 6px; border-radius: 4px; font-size: 0.75rem; width: 100px;">
                                                    <button type="submit" style="background: #2ecc71; border: none; color: #fff; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem; cursor: pointer; transition: all 0.2s;">Lưu</button>
                                                </form>
                                                <form method="POST" style="display: inline; margin: 0;">
                                                    <input type="hidden" name="action" value="bind_hwid">
                                                    <input type="hidden" name="key" value="{{ row['key'] }}">
                                                    <input type="hidden" name="hwid" value="222323328D124FF27ED399FDFF80BB44">
                                                    <button type="submit" style="background: rgba(46, 204, 113, 0.15); border: 1px solid #2ecc71; color: #2ecc71; padding: 2px 6px; border-radius: 4px; font-size: 0.7rem; cursor: pointer; display: block; text-align: center; transition: all 0.2s;">Bind máy test</button>
                                                </form>
                                            </div>
                                        {% endif %}
                                    </td>
                                    <td>
                                        {% if row['expires_at'] %}
                                            <span style="font-size: 0.8rem;">{{ row['expires_at'] }}</span>
                                        {% else %}
                                            -
                                        {% endif %}
                                    </td>
                                    <td>
                                        {% if row['status'] == 'ACTIVE' and row['hwid'] %}
                                            <a href="?view_key={{ row['key'] }}#coordinates-section" class="action-btn" style="background: rgba(157, 78, 221, 0.25); border: 1px solid var(--accent); color: var(--text-main); text-decoration: none; padding: 4px 8px; border-radius: 6px; font-size: 0.8rem; display: inline-block; transition: all 0.2s;">Xem vị trí</a>
                                        {% else %}
                                            <span style="color: var(--text-sub); font-size: 0.8rem;">Chưa kích hoạt</span>
                                        {% endif %}
                                    </td>
                                    <td>
                                        <div style="display: flex; gap: 5px;">
                                            {% if row['status'] == 'ACTIVE' %}
                                            <form method="POST" style="display: inline;" onsubmit="return confirm('Ngài có chắc muốn khóa key này?');">
                                                <input type="hidden" name="action" value="revoke">
                                                <input type="hidden" name="key" value="{{ row['key'] }}">
                                                <button type="submit" class="action-btn action-btn-danger" title="Khóa ngay lập tức">Khóa</button>
                                            </form>
                                            {% endif %}
                                            <form method="POST" style="display: inline;" onsubmit="return confirm('Ngài có chắc muốn xóa vĩnh viễn key này?');">
                                                <input type="hidden" name="action" value="delete">
                                                <input type="hidden" name="key" value="{{ row['key'] }}">
                                                <button type="submit" class="action-btn action-btn-danger" title="Xóa khỏi hệ thống">Xóa</button>
                                            </form>
                                        </div>
                                    </td>
                                </tr>
                                {% endfor %}
                            </tbody>
                        </table>
                </div>

                <div class="card" id="coordinates-section" style="grid-column: span 2; margin-top: 30px;">
                    <div class="card-title" style="display: flex; justify-content: space-between; align-items: center; border-bottom: none; margin-bottom: 15px;">
                        <span>Lịch sử tọa độ hoạt động (Logged Coordinates)</span>
                        {% if request.args.get('view_key') %}
                            <span style="font-size: 0.85rem; background: rgba(157, 78, 221, 0.2); border: 1px solid var(--accent); padding: 4px 10px; border-radius: 6px; color: var(--accent-hover);">
                                Đang xem Key: <code>{{ request.args.get('view_key') }}</code>
                            </span>
                        {% endif %}
                    </div>

                    <!-- Dropdown Key Selection -->
                    <div style="margin-bottom: 25px; display: flex; align-items: center; gap: 12px; background: rgba(157, 78, 221, 0.05); padding: 12px 18px; border: 1px solid rgba(157, 78, 221, 0.15); border-radius: 10px;">
                        <label for="select-key" style="color: var(--text-sub); font-size: 0.9rem; font-weight: 500;">Chọn nhanh Key cần xem vị trí:</label>
                        <form method="GET" action="#coordinates-section" style="display: inline-flex; align-items: center; gap: 10px; margin: 0;">
                            <select name="view_key" id="select-key" style="background: rgba(7, 2, 17, 0.8); border: 1px solid var(--border); color: var(--text-main); padding: 8px 14px; border-radius: 8px; font-size: 0.85rem; outline: none; cursor: pointer; min-width: 280px; transition: all 0.3s;" onchange="this.form.submit()">
                                <option value="">-- Bấm vào đây chọn Key để xem --</option>
                                {% for row in licenses %}
                                    <option value="{{ row['key'] }}" {% if request.args.get('view_key') == row['key'] %}selected{% endif %}>
                                        {{ row['key'] }} ({% if row['status'] == 'ACTIVE' %}Đang dùng{% else %}Chưa dùng/Hết hạn{% endif %})
                                    </option>
                                {% endfor %}
                            </select>
                            {% if request.args.get('view_key') %}
                                <a href="?" style="color: #e74c3c; font-size: 0.85rem; text-decoration: none; border-bottom: 1px dashed #e74c3c; margin-left: 8px; transition: all 0.2s;">Xóa bộ lọc</a>
                            {% endif %}
                        </form>
                    </div>

                    {% if request.args.get('view_key') %}
                        {% if coords %}
                            <!-- Stats Cards Grid -->
                            {% if stats %}
                            <div class="stats-grid" style="grid-template-columns: repeat(3, 1fr); margin-bottom: 20px;">
                                <div class="stat-box">
                                    <div class="stat-label">💰 Tổng doanh thu</div>
                                    <div class="stat-val" style="color: #2ecc71;">{{ stats.total_earned }}</div>
                                </div>
                                <div class="stat-box">
                                    <div class="stat-label">⏱️ Thời gian treo máy</div>
                                    <div class="stat-val" style="color: #3498db;">{{ stats.elapsed_time }}</div>
                                </div>
                                <div class="stat-box" style="background: rgba(157, 78, 221, 0.15); border-color: var(--accent);">
                                    <div class="stat-label" style="color: var(--accent-hover);">📊 TB mỗi giờ</div>
                                    <div class="stat-val" style="color: #e0aaff;">{{ stats.rate_per_hour }}</div>
                                </div>
                            </div>
                            <div class="stats-grid" style="grid-template-columns: repeat(3, 1fr); margin-bottom: 25px;">
                                <div class="stat-box" style="background: rgba(241, 196, 15, 0.1); border-color: rgba(241, 196, 15, 0.3);">
                                    <div class="stat-label" style="color: #f1c40f;">⚡ 1 giờ gần nhất</div>
                                    <div class="stat-val" style="color: #f39c12;">{{ stats.last_hour }}</div>
                                </div>
                                <div class="stat-box">
                                    <div class="stat-label">🛒 Số lần bán thành công</div>
                                    <div class="stat-val" style="color: #e74c3c;">{{ stats.sell_count }}</div>
                                </div>
                                <div class="stat-box">
                                    <div class="stat-label">📡 Tổng check-in</div>
                                    <div class="stat-val" style="color: #9b59b6;">{{ stats.total_logs }}</div>
                                </div>
                            </div>

                            {% if hourly_data %}
                            <div style="background: rgba(7, 2, 17, 0.5); border: 1px solid var(--border); border-radius: 12px; padding: 20px; margin-bottom: 25px;">
                                <div style="font-weight: 600; color: var(--text-main); margin-bottom: 15px; font-size: 0.95rem;">📈 Biểu đồ doanh thu theo giờ</div>
                                <div style="display: flex; align-items: flex-end; gap: 4px; height: 150px; padding: 0 5px;">
                                    {% set max_val = hourly_data|map(attribute='earned')|max %}
                                    {% for h in hourly_data %}
                                    <div style="flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%;" title="{{ h.hour }} — {{ h.earned_fmt }} ({{ h.count }} lần)">
                                        <div style="font-size: 0.6rem; color: var(--text-sub); margin-bottom: 4px; white-space: nowrap; overflow: hidden; max-width: 60px;">{{ h.earned_fmt }}</div>
                                        <div style="flex: 1; display: flex; align-items: flex-end; width: 100%;">
                                            <div style="width: 100%; background: linear-gradient(180deg, #a14dff, #6c2bd9); border-radius: 4px 4px 0 0; min-height: 4px; height: {{ (h.earned / max_val * 100) if max_val > 0 else 0 }}%; transition: height 0.5s ease;"></div>
                                        </div>
                                        <div style="font-size: 0.55rem; color: var(--text-sub); margin-top: 4px; white-space: nowrap; transform: rotate(-45deg); transform-origin: top center;">{{ h.hour[-5:] }}</div>
                                    </div>
                                    {% endfor %}
                                </div>
                            </div>
                            {% endif %}
                            {% endif %}

                            <div class="table-container">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Thời gian</th>
                                            <th>Mã Key</th>
                                            <th>Máy chủ (Server)</th>
                                            <th>Thế giới</th>
                                            <th>Tọa độ (X, Y, Z)</th>
                                            <th>Chi tiết giao dịch</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {% for coord in coords %}
                                        <tr>
                                            <td>{{ coord.created_at }}</td>
                                            <td><code style="color: #c77dff;">{{ coord.license_key }}</code></td>
                                            <td>{{ coord.server }}</td>
                                            <td>{{ coord.world }}</td>
                                            <td><strong>{{ coord.x }}, {{ coord.y }}, {{ coord.z }}</strong></td>
                                            <td>{{ coord.details }}</td>
                                        </tr>
                                        {% endfor %}
                                    </tbody>
                                </table>
                            </div>
                        {% else %}
                            <div style="text-align: center; padding: 40px; color: var(--text-sub);">
                                📁 Key này chưa có lịch sử hoạt động hoặc chưa check-in tọa độ.
                            </div>
                        {% endif %}
                    {% else %}
                        <div style="text-align: center; padding: 50px 20px; color: var(--text-sub); border: 1px dashed rgba(157, 78, 221, 0.15); border-radius: 10px;">
                            <span style="font-size: 2.2rem; display: block; margin-bottom: 12px;">🔍</span>
                            Vui lòng chọn một Key từ danh sách bên trên để hiển thị lịch sử tọa độ hoạt động.
                        </div>
                    {% endif %}
                </div>
            </div>
        </div>
    </body>
    </html>
    """, licenses=licenses, coords=coords, stats=stats, hourly_data=hourly_data)

def get_plan_details(plan_id):
    if plan_id == "1day":
        return {"name": "GÓI TRẠI NGHIỆM (1 Ngày)", "price": 10000, "duration": 1, "desc": "Bản quyền Auto Sell dùng thử"}
    elif plan_id == "3day":
        return {"name": "GÓI PHỔ THÔNG (3 Ngày)", "price": 30000, "duration": 3, "desc": "Bản quyền Auto Sell Pro Vip"}
    elif plan_id == "lifetime":
        return {"name": "GÓI PRO (Vĩnh Viễn)", "price": 200000, "duration": 9999, "desc": "Bản quyền Auto Sell Pro Vip & cập nhật trọn đời"}
    return None

@app.route("/buy/<plan_id>", methods=["GET"])
def buy_plan(plan_id):
    plan = get_plan_details(plan_id)
    if not plan:
        return "Gói dịch vụ không hợp lệ!", 404

    chars = string.ascii_uppercase + string.digits
    order_id = ''.join(random.choices(chars, k=8))

    conn = get_db()
    c = conn.cursor()
    c.execute('''
        INSERT INTO orders (order_id, plan_id, amount, status, created_key, created_at)
        VALUES (?, ?, ?, 'PENDING', NULL, ?)
    ''', (order_id, plan_id, plan["price"], datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
    conn.commit()
    conn.close()

    global BANK_ID, BANK_ACC, BANK_NAME, MOMO_PHONE
    BANK_ID = "MB"
    BANK_ACC = "1234567890"
    BANK_NAME = "NGUYEN VAN A"
    MOMO_PHONE = "0987654321"

    memo = f"ASV{order_id}"
    qr_url = f"https://img.vietqr.io/image/{BANK_ID}-{BANK_ACC}-compact2.png?amount={plan['price']}&addInfo={memo}&accountName={BANK_NAME}"

    return render_template_string("""
    <!DOCTYPE html>
    <html>
    <head>
        <title>Thanh Toán Đơn Hàng - Paladi Client</title>
        <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
        <style>
            """ + LANDING_CSS + """
            .checkout-container {
                display: grid;
                grid-template-columns: 1fr 1.2fr;
                gap: 40px;
                max-width: 1000px;
                margin: 5vh auto;
                padding: 0 20px;
            }
            @media (max-width: 850px) {
                .checkout-container {
                    grid-template-columns: 1fr;
                }
            }
            .checkout-card {
                background: var(--bg-card);
                border: 1px solid var(--border);
                border-radius: 20px;
                padding: 40px;
                box-shadow: var(--shadow);
                position: relative;
            }
            .qr-wrapper {
                text-align: center;
                background: white;
                padding: 20px;
                border-radius: 16px;
                margin-bottom: 20px;
                box-shadow: 0 10px 25px rgba(0,0,0,0.3);
                display: inline-block;
                width: 100%;
                max-width: 320px;
            }
            .qr-image {
                width: 100%;
                height: auto;
                display: block;
            }
            .status-indicator {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 12px;
                font-weight: 700;
                font-size: 1.1rem;
                color: var(--text-sub);
                margin: 20px 0;
            }
            .spinner {
                width: 20px;
                height: 20px;
                border: 3px solid rgba(138, 43, 226, 0.2);
                border-top: 3px solid var(--accent);
                border-radius: 50%;
                animation: spin 1s linear infinite;
            }
            @keyframes spin {
                0% { transform: rotate(0deg); }
                100% { transform: rotate(360deg); }
            }
            .success-container {
                display: none;
                text-align: center;
                animation: fadeIn 0.5s ease forwards;
            }
            .success-checkmark {
                font-size: 4rem;
                color: var(--success);
                margin-bottom: 15px;
            }
            .key-box {
                background: rgba(7, 2, 17, 0.8);
                border: 1px solid rgba(46, 204, 113, 0.4);
                padding: 15px;
                border-radius: 10px;
                font-family: monospace;
                font-size: 1.2rem;
                font-weight: 700;
                color: #2ecc71;
                margin: 20px 0;
                word-break: break-all;
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: 10px;
            }
            .success-btn {
                background: linear-gradient(135deg, #2ecc71, #27ae60);
                display: inline-flex;
                align-items: center;
                gap: 8px;
                font-weight: 700;
                padding: 12px 24px;
                border-radius: 8px;
                color: #fff;
                text-decoration: none;
                margin-top: 15px;
                box-shadow: 0 10px 20px rgba(46, 204, 113, 0.2);
                transition: all 0.3s;
            }
            .success-btn:hover {
                background: linear-gradient(135deg, #27ae60, #2ecc71);
                transform: translateY(-2px);
                box-shadow: 0 12px 25px rgba(46, 204, 113, 0.4);
            }
        </style>
    </head>
    <body>
        <div class="navbar">
            <div class="logo-container">
                <span class="logo-icon">⭐</span>
                <div class="logo">PALADI CLIENT</div>
            </div>
            <div class="nav-links">
                <a href="/#features">Giới Thiệu</a>
                <a href="/#pricing">Bảng Giá</a>
                <a href="/#buy-guide">Chỗ Mua</a>
                <a href="/#portal">Kích Hoạt Portal</a>
            </div>
        </div>

        <div class="checkout-container">
            <!-- Left Card: Bill details -->
            <div class="checkout-card" id="billing-info-card">
                <h2 style="margin-top: 0; font-size: 1.8rem; color: #fff;">Thông Tin Hóa Đơn</h2>
                <div style="font-size: 0.9rem; color: var(--text-sub); margin-bottom: 25px;">Đơn hàng: #<code>""" + order_id + """</code></div>

                <div style="margin-bottom: 20px;">
                    <div style="font-size: 0.8rem; color: var(--text-sub); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 4px;">Gói dịch vụ</div>
                    <div style="font-size: 1.15rem; font-weight: 700; color: #fff;">""" + plan["name"] + """</div>
                    <div style="font-size: 0.9rem; color: var(--text-sub); margin-top: 4px;">""" + plan["desc"] + """</div>
                </div>

                <div style="margin-bottom: 25px;">
                    <div style="font-size: 0.8rem; color: var(--text-sub); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 4px;">Số tiền cần thanh toán</div>
                    <div style="font-size: 2.2rem; font-weight: 800; color: #e0aaff;">""" + "{:,}".format(plan["price"]) + """ VNĐ</div>
                </div>

                <div style="border-top: 1px solid rgba(138, 43, 226, 0.15); padding-top: 25px;">
                    <h3 style="font-size: 1.05rem; margin-top: 0; color: #fff; margin-bottom: 15px;">Hướng Dẫn Thanh Toán</h3>
                    <p style="font-size: 0.95rem; color: rgba(243, 232, 255, 0.7); line-height: 1.6; margin: 0 0 15px 0;">
                        1. Mở ứng dụng Ngân hàng hoặc ví MoMo.<br>
                        2. Quét mã QR kế bên hoặc nhập thông tin chuyển khoản thủ công.<br>
                        3. <strong style="color: #e0aaff;">BẮT BUỘC:</strong> Điền chính xác nội dung chuyển khoản là <strong style="color: #fff; background: rgba(138, 43, 226, 0.2); padding: 2px 6px; border-radius: 4px; border: 1px solid rgba(138, 43, 226, 0.3);">""" + memo + """</strong>.<br>
                        4. Giữ nguyên trang này, hệ thống sẽ tự động kích hoạt key ngay sau khi nhận được tiền.
                    </p>
                </div>
            </div>

            <!-- Right Card: QR Code & Status -->
            <div class="checkout-card" style="text-align: center;">
                <!-- Payment Area -->
                <div id="payment-area">
                    <h2 style="margin-top: 0; font-size: 1.6rem; color: #fff; margin-bottom: 20px;">Quét Mã QR Chuyển Khoản</h2>
                    <div class="qr-wrapper">
                        <img src="""" + qr_url + """" alt="VietQR Payment Code" class="qr-image">
                    </div>

                    <div class="status-indicator">
                        <div class="spinner"></div>
                        <span id="status-text">Đang chờ chuyển khoản...</span>
                    </div>

                    <div style="font-size: 0.8rem; color: var(--text-sub); max-width: 350px; margin: 0 auto; line-height: 1.5;">
                        Nhận tiền tự động qua hệ thống ngân hàng liên kết VietQR. Thông thường mất từ 5 - 30 giây để xử lý giao dịch.
                    </div>
                </div>

                <!-- Success Area -->
                <div class="success-container" id="success-area">
                    <div class="success-checkmark">✅</div>
                    <h2 style="color: #fff; margin-top: 0; margin-bottom: 10px;">Thanh Toán Thành Công!</h2>
                    <p style="font-size: 0.95rem; color: var(--text-sub); line-height: 1.5; margin-bottom: 20px;">
                        Hệ thống đã xác nhận giao dịch của ngài. Mã kích hoạt Paladi Client đã được tạo thành công:
                    </p>

                    <div class="key-box">
                        <span id="created-key">PLD-XXXX-XXXX</span>
                        <button class="copy-btn" onclick="copyKey()" style="color: #2ecc71; font-weight: bold; border: 1px solid rgba(46, 204, 113, 0.3); padding: 4px 8px; border-radius: 6px; background: rgba(46, 204, 113, 0.1);">Copy Key</button>
                    </div>

                    <p style="font-size: 0.9rem; color: rgba(243, 232, 255, 0.7); text-align: left; line-height: 1.5; margin-bottom: 25px;">
                        Để sử dụng: Tải xuống tệp <b>license.txt</b> và đặt nó vào thư mục Minecraft theo hướng dẫn bên dưới:
                    </p>

                    <div class="folder-tree" style="margin-bottom: 25px;">
                        📂 .minecraft<br>
                        └── 📂 paladi-client<br>
                        &nbsp;&nbsp;&nbsp;&nbsp;└── 📄 license.txt (Đặt tệp tải về vào đây)
                    </div>

                    <a href="#" id="download-license-link" class="success-btn">📥 Tải tệp license.txt</a>
                    <br>
                    <a href="/" class="btn btn-secondary" style="margin-top: 15px; font-size: 0.9rem; padding: 10px 20px;">Về Trang Chủ</a>
                </div>
            </div>
        </div>

        <script>
            let pollingInterval = setInterval(checkOrderStatus, 3000);

            function checkOrderStatus() {
                fetch('/order-status/' + '""" + order_id + """')
                    .then(response => response.json())
                    .then(data => {
                        if (data.status === 'COMPLETED') {
                            clearInterval(pollingInterval);
                            showSuccessState(data.key);
                        }
                    })
                    .catch(err => console.error("Error polling order status:", err));
            }

            function showSuccessState(key) {
                document.getElementById('payment-area').style.display = 'none';
                document.getElementById('billing-info-card').style.display = 'none';
                document.getElementById('success-area').style.display = 'block';
                document.getElementById('created-key').textContent = key;
                document.getElementById('download-license-link').href = '/download/' + key;

                const container = document.querySelector('.checkout-container');
                container.style.gridTemplateColumns = '1fr';
                container.style.maxWidth = '560px';
            }

            function copyKey() {
                const keyText = document.getElementById('created-key').textContent;
                copyToClipboard(keyText, 'Mã kích hoạt Key');
            }

            function copyToClipboard(text, label) {
                navigator.clipboard.writeText(text).then(function() {
                    showToast("Đã sao chép " + label + " vào bộ nhớ tạm!");
                }, function(err) {
                    const el = document.createElement('textarea');
                    el.value = text;
                    document.body.appendChild(el);
                    el.select();
                    document.execCommand('copy');
                    document.body.removeChild(el);
                    showToast("Đã sao chép " + label + " vào bộ nhớ tạm!");
                });
            }

            function showToast(message) {
                let toast = document.getElementById("toast");
                if (!toast) {
                    toast = document.createElement("div");
                    toast.id = "toast";
                    toast.className = "toast";
                    document.body.appendChild(toast);
                }
                toast.textContent = message;
                toast.className = "toast show";
                setTimeout(function() {
                    toast.className = "toast";
                }, 2500);
            }
        </script>
    </body>
    </html>
    """)

@app.route("/order-status/<order_id>", methods=["GET"])
def order_status(order_id):
    conn = get_db()
    c = conn.cursor()
    c.execute("SELECT status, created_key FROM orders WHERE order_id = ?", (order_id,))
    row = c.fetchone()
    conn.close()

    if not row:
        return jsonify({"status": "NOT_FOUND"}), 404

    return jsonify({
        "status": row["status"],
        "key": row["created_key"]
    })

@app.route("/payment-webhook", methods=["POST"])
def payment_webhook():
    data = request.json or {}
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] payment-webhook received: {data}")

    content = ""
    amount = 0

    if "content" in data:
        content = data["content"]
    elif "transferContent" in data:
        content = data["transferContent"]
    elif "description" in data:
        content = data["description"]

    if "transferAmount" in data:
        amount = float(data["transferAmount"])
    elif "amount" in data:
        amount = float(data["amount"])

    if not content and "transaction" in data:
        tx = data["transaction"]
        content = tx.get("content") or tx.get("transferContent") or ""
        amount = float(tx.get("transferAmount") or tx.get("amount") or 0)

    content_clean = "".join(c for c in content if c.isalnum()).upper()

    pld_index = content_clean.find("PLD")
    if pld_index != -1:
        order_id = content_clean[pld_index + 3 : pld_index + 11]

        conn = get_db()
        c = conn.cursor()
        c.execute("SELECT * FROM orders WHERE order_id = ?", (order_id,))
        row = c.fetchone()

        if row and row["status"] == "PENDING" and amount >= row["amount"]:
            plan_id = row["plan_id"]
            plan = get_plan_details(plan_id)

            if plan:
                new_key = generate_key_string()
                flags = "NO_CHIBI" if plan_id == "1day" else ""

                c.execute('''
                    INSERT INTO licenses (key, hwid, duration_days, created_at, expires_at, activated_at, flags, status)
                    VALUES (?, NULL, ?, ?, NULL, NULL, ?, 'PENDING')
                ''', (new_key, plan["duration"], datetime.now().strftime("%Y-%m-%d %H:%M:%S"), flags))

                c.execute('''
                    UPDATE orders
                    SET status = 'COMPLETED', created_key = ?
                    WHERE order_id = ?
                ''', (new_key, order_id))

                conn.commit()
                conn.close()
                return jsonify({"status": "success", "message": f"Successfully activated license key: {new_key}"})
        conn.close()

    return jsonify({"status": "ignored", "message": "Transaction does not match any pending order memo or amount"}), 200

@app.route("/admin/simulate-payment", methods=["GET"])
def simulate_payment():
    order_id = request.args.get("order_id", "").strip().upper()
    if not order_id:
        return "Vui lòng cung cấp order_id!", 400

    conn = get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM orders WHERE order_id = ?", (order_id,))
    row = c.fetchone()

    if not row:
        conn.close()
        return f"Không tìm thấy đơn hàng nào có ID: {order_id}", 404

    if row["status"] != "PENDING":
        conn.close()
        return f"Đơn hàng này đã có trạng thái: {row['status']}", 400

    plan_id = row["plan_id"]
    plan = get_plan_details(plan_id)
    if not plan:
        conn.close()
        return "Lỗi: Không tìm thấy chi tiết gói dịch vụ!", 500

    new_key = generate_key_string()
    flags = "NO_CHIBI" if plan_id == "1day" else ""

    c.execute('''
        INSERT INTO licenses (key, hwid, duration_days, created_at, expires_at, activated_at, flags, status)
        VALUES (?, NULL, ?, ?, NULL, NULL, ?, 'PENDING')
    ''', (new_key, plan["duration"], datetime.now().strftime("%Y-%m-%d %H:%M:%S"), flags))

    c.execute('''
        UPDATE orders
        SET status = 'COMPLETED', created_key = ?
        WHERE order_id = ?
    ''', (new_key, order_id))

    conn.commit()
    conn.close()

    return f"Đã giả lập thanh toán THÀNH CÔNG cho đơn hàng #{order_id}! Hệ thống đã cấp Key: {new_key}"

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    app.run(host="0.0.0.0", port=port, debug=False)
