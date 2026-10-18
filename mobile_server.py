import os
import sys

# Đảm bảo in UTF-8 không bị lỗi trên Windows console (cp1252)
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import uuid
import socket
import json
import re
import time
import threading
import subprocess
import atexit
import concurrent.futures
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime, timedelta
from flask import Flask, send_from_directory, request, jsonify, send_file


# Khởi tạo Flask
app = Flask(__name__)

@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS, PUT, DELETE"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization, X-Requested-With"
    return response

@app.route('/<path:dummy>', methods=['OPTIONS'])
def handle_options(dummy):
    return "", 204

BASE_MINECRAFT_PORT = 29388
MAX_PORT_OFFSET = 30 # Quét từ 29388 tới 29418 (hỗ trợ tới 31 acc MultiMC & Dawn Launcher)
ACTIVE_ACCOUNTS = {} # port -> data
PUBLIC_4G_URL = None
CLOUDFLARE_PROCESS = None
LAUNCHER_CACHE = {}

# Thư mục lưu ảnh bằng chứng giao hàng (Proof of Delivery)
PROOFS_DIR = Path(__file__).parent / "mobile_app" / "delivery_proofs"
PROOFS_DIR.mkdir(parents=True, exist_ok=True)
PROOFS_HISTORY_FILE = PROOFS_DIR / "history.json"
FONT_PATH = Path(__file__).parent / "mobile_app" / "Minecraft.otf"

def load_proofs_history():
    if PROOFS_HISTORY_FILE.exists():
        try:
            with open(PROOFS_HISTORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return []

def save_proofs_history(history):
    try:
        with open(PROOFS_HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, ensure_ascii=False, indent=2)
    except Exception:
        pass

def capture_in_game_screenshot(target, amount, proof_id=None, acc_identifier=None):
    """Chụp ảnh màn hình thực tế từ game Minecraft (qua PaladiLiveSync API hoặc cửa sổ game)"""
    if not proof_id:
        proof_id = f"proof_{int(time.time())}_{uuid.uuid4().hex[:6]}"
    
    clean_target = str(target).strip()
    clean_amount = str(amount).strip().replace("$", "").strip()
    if clean_amount.lower().endswith("m") or clean_amount.lower().endswith("b") or clean_amount.lower().endswith("k"):
        clean_amount = clean_amount[:-1] + clean_amount[-1].upper()

    out_file = PROOFS_DIR / f"{proof_id}.png"
    captured = False

    target_port = resolve_target_port(acc_identifier)
    if target_port:
        try:
            # 1. Gửi yêu cầu chụp ảnh trực tiếp từ framebuffer của Minecraft client
            encoded_target = urllib.parse.quote(clean_target)
            encoded_amount = urllib.parse.quote(clean_amount)
            api_url = f"http://127.0.0.1:{target_port}/api/screenshot?target={encoded_target}&amount={encoded_amount}&delay=250"
            req = urllib.request.Request(api_url, headers={"User-Agent": "PaladiMobile/1.0"})
            with urllib.request.urlopen(req, timeout=5.0) as resp:
                if resp.status == 200:
                    img_bytes = resp.read()
                    if img_bytes and len(img_bytes) > 200:
                        with open(out_file, "wb") as f_img:
                            f_img.write(img_bytes)
                        captured = True
                        print(f" [v] Da chup anh thanh cong tu game Minecraft tren port {target_port} ({len(img_bytes)} bytes)")
        except Exception as ex_api:
            print(f" [!] Khong the chup qua API port {target_port}: {ex_api}")

    # 2. Fallback: Nếu không kết nối được API client, chụp cửa sổ Minecraft bằng ImageGrab
    if not captured:
        try:
            from PIL import ImageGrab
            screenshot = None
            try:
                import pygetwindow as gw
                mc_windows = [w for w in gw.getAllWindows() if "minecraft" in w.title.lower()]
                if mc_windows:
                    w = mc_windows[0]
                    if w.width > 200 and w.height > 200:
                        screenshot = ImageGrab.grab(bbox=(max(0, w.left), max(0, w.top), w.right, w.bottom))
            except Exception:
                pass

            if screenshot is None:
                screenshot = ImageGrab.grab()

            if screenshot:
                screenshot.save(out_file)
                captured = True
                print(f" [v] Da chup man hinh game qua fallback ImageGrab!")
        except Exception as ex_grab:
            print(f" [!] Loi chup anh man hinh fallback: {ex_grab}")

    proof_info = {
        "id": proof_id,
        "target": clean_target,
        "amount": clean_amount,
        "url": f"/api/delivery-proof/{proof_id}",
        "download_url": f"/api/delivery-proof/{proof_id}?download=1&filename=Eldorado_Pay_{clean_target}_{clean_amount}.png",
        "filename": f"Eldorado_Pay_{clean_target}_{clean_amount}.png",
        "time": time.strftime("%H:%M:%S"),
        "date": time.strftime("%Y-%m-%d"),
        "source": "in_game_screenshot" if captured else "fallback"
    }

    history = load_proofs_history()
    history.insert(0, proof_info)
    if len(history) > 100:
        history = history[:100]
    save_proofs_history(history)

    return proof_info

# Alias tuong thich nguoc
generate_delivery_proof_image = capture_in_game_screenshot

def get_launcher_for_port(port):
    if port in LAUNCHER_CACHE:
        return LAUNCHER_CACHE[port]
    try:
        lines = subprocess.check_output(f'netstat -ano -p tcp | findstr /R ":{port} .*LISTENING"', shell=True, text=True, timeout=1.2).strip().splitlines()
        for line in lines:
            parts = line.split()
            if len(parts) >= 5 and parts[3] == "LISTENING":
                pid = parts[-1]
                ps_cmd = f'(Get-CimInstance Win32_Process -Filter "ProcessId = {pid}").CommandLine'
                p_out = subprocess.check_output(f'powershell -NoProfile -Command "{ps_cmd}"', shell=True, text=True, timeout=1.5).lower()
                if ".dawn" in p_out or "dawn" in p_out:
                    launcher = "Dawn Launcher"
                elif "multimc" in p_out or "mmc" in p_out:
                    launcher = "MultiMC"
                elif "feather" in p_out:
                    launcher = "Feather Client"
                else:
                    launcher = "MultiMC / Dawn"
                LAUNCHER_CACHE[port] = launcher
                return launcher
    except Exception:
        pass
    return "MultiMC / Dawn"

def get_local_ip():
    """Lấy địa chỉ IPv4 nội bộ (LAN) của máy tính trong mạng Wi-Fi"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

LOCAL_IP = get_local_ip()
PORT = 8080

def cleanup_cloudflared():
    global CLOUDFLARE_PROCESS
    if CLOUDFLARE_PROCESS:
        try:
            CLOUDFLARE_PROCESS.terminate()
            CLOUDFLARE_PROCESS.kill()
        except Exception:
            pass

atexit.register(cleanup_cloudflared)

def start_cloudflared_tunnel():
    """Tự động khởi động Cloudflare Quick Tunnel để truy cập từ xa qua 4G/5G/Internet (Tự kết nối lại vĩnh viễn)"""
    global PUBLIC_4G_URL, CLOUDFLARE_PROCESS
    cf_path = Path(__file__).parent / "tools" / "cloudflared.exe"
    if not cf_path.exists():
        print(f" [!] Khong tim thay {cf_path}, bo qua ket noi 4G.")
        return

    while True:
        cmd = [str(cf_path), "tunnel", "--url", f"http://127.0.0.1:{PORT}", "--no-autoupdate"]
        try:
            CLOUDFLARE_PROCESS = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1
            )
            for line in iter(CLOUDFLARE_PROCESS.stdout.readline, ''):
                if not line:
                    break
                match = re.search(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com", line)
                if match:
                    PUBLIC_4G_URL = match.group(0)
                    border = "=" * 66
                    print("\n" + border)
                    print(" [>] LINK 4G / 5G / INTERNET TOAN CAU DA KICH HOAT THANH CONG!")
                    print(" [>] BAN CO THE RA NGOAI DUONG, DUNG 4G TRUY CAP VA /PAY BINH THUONG:")
                    print(f"     ->  {PUBLIC_4G_URL}")
                    print(border)
                    try:
                        with open("link_4g_dien_thoai.txt", "w", encoding="utf-8") as f_link:
                            f_link.write(f"LINK DUNG TREN DIEN THOAI (4G/5G):\n{PUBLIC_4G_URL}\n\nLINK KHI O NHA (WI-FI):\nhttp://{LOCAL_IP}:{PORT}\n")
                        
                        cfg_js = f'window.PALADI_CONFIG = {{\n    server_url: "{PUBLIC_4G_URL}",\n    local_url: "http://{LOCAL_IP}:{PORT}"\n}};\n'
                        with open(Path(__file__).parent / "mobile_app" / "server_config.js", "w", encoding="utf-8") as f_cfg:
                            f_cfg.write(cfg_js)
                        with open(Path(__file__).parent / "mobile_app" / "server_config.json", "w", encoding="utf-8") as f_cfg_json:
                            json.dump({"server_url": PUBLIC_4G_URL}, f_cfg_json)

                        def async_rebuild_apk():
                            try:
                                import build_apk
                                build_apk.build_apk(PUBLIC_4G_URL)
                            except Exception as ex_apk:
                                print(f" [!] Async APK rebuild warning: {ex_apk}")
                        threading.Thread(target=async_rebuild_apk, daemon=True).start()
                    except Exception as ex:
                        print(f" [!] Error saving 4G config: {ex}")
                    try:
                        import qrcode
                        qr = qrcode.QRCode(border=1)
                        qr.add_data(PUBLIC_4G_URL)
                        qr.make(fit=True)
                        print(" [!] QUET MA QR NAY TREN DIEN THOAI DE LUU LINK 4G VAO MAY:")
                        qr.print_ascii(invert=True)
                        print(border + "\n")
                    except Exception:
                        pass
            if CLOUDFLARE_PROCESS:
                CLOUDFLARE_PROCESS.wait()
        except Exception as e:
            print(f" [!] Loi khi mo Cloudflare Tunnel: {e}")
        print(" [!] Cloudflare Tunnel bi ngat, dang khoi dong lai sau 5 giay...")
        time.sleep(5)


# ----------------------------------------------------------------------
# Launcher Detection & Multi-Account Scanner (MultiMC / Dawn / TLauncher)
# ----------------------------------------------------------------------
def get_launcher_for_port(port):
    if port in LAUNCHER_CACHE:
        return LAUNCHER_CACHE[port]
    try:
        lines = subprocess.check_output(f'netstat -ano -p tcp | findstr /R ":{port} .*LISTENING"', shell=True, text=True, timeout=1.2).strip().splitlines()
        for line in lines:
            parts = line.split()
            if len(parts) >= 5 and parts[3] == "LISTENING":
                pid = parts[-1]
                ps_args = ['powershell', '-NoProfile', '-Command', f"(Get-CimInstance Win32_Process -Filter 'ProcessId={pid}').CommandLine"]
                p_out = subprocess.check_output(ps_args, text=True, timeout=1.5).lower()
                if ".dawn" in p_out or "dawn" in p_out or "feather" in p_out:
                    launcher = "Dawn Launcher"
                elif "tlauncher" in p_out:
                    launcher = "TLauncher"
                elif "multimc" in p_out or "mmc" in p_out:
                    launcher = "MultiMC"
                else:
                    launcher = "MultiMC / Dawn"
                LAUNCHER_CACHE[port] = launcher
                return launcher
    except Exception:
        pass
    return "MultiMC / Dawn"

def check_single_port(port):
    try:
        req = urllib.request.Request(f"http://127.0.0.1:{port}/api/status", headers={"User-Agent": "PaladiMobile/1.0"})
        with urllib.request.urlopen(req, timeout=0.6) as response:
            data = json.loads(response.read().decode("utf-8"))
            if data and isinstance(data, dict):
                launcher_type = get_launcher_for_port(port)
                if not data.get("player") or not data["player"].get("name"):
                    data["player"] = {
                        "name": f"Bot-{port}",
                        "server": "Chưa kết nối",
                        "balance": "0$",
                        "health": 20.0,
                        "maxHealth": 20.0,
                        "food": 20,
                        "x": 0.0, "y": 64.0, "z": 0.0,
                        "launcher": launcher_type
                    }
                elif not data["player"].get("launcher") or data["player"]["launcher"] == "MultiMC":
                    if launcher_type != "MultiMC / Dawn":
                        data["player"]["launcher"] = launcher_type
                data["port"] = port
                data["id"] = str(port)
                data["gameConnected"] = data.get("inGame", True)
                data["lastSeen"] = time.time()
                return port, data
    except Exception:
        pass
    return port, None

def scan_multi_accounts_loop():
    """Liên tục dò quét tất cả các tài khoản Minecraft đang mở trong MultiMC"""
    global ACTIVE_ACCOUNTS
    while True:
        ports = [BASE_MINECRAFT_PORT + i for i in range(MAX_PORT_OFFSET + 1)]
        current_active = {}
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
            futures = [executor.submit(check_single_port, p) for p in ports]
            for f in concurrent.futures.as_completed(futures):
                try:
                    p, acc_data = f.result()
                    if acc_data is not None:
                        current_active[p] = acc_data
                except Exception:
                    pass
        ACTIVE_ACCOUNTS = current_active
        time.sleep(0.8)

def resolve_target_port(acc_identifier=None):
    """Tìm cổng port của account dựa vào port hoặc tên người chơi (IGN)"""
    if not ACTIVE_ACCOUNTS:
        return None
    if acc_identifier:
        # 1. Tìm theo port chính xác
        try:
            p = int(acc_identifier)
            if p in ACTIVE_ACCOUNTS:
                return p
        except ValueError:
            pass
        # 2. Tìm theo IGN người chơi
        acc_str = str(acc_identifier).strip().lower()
        for p, d in ACTIVE_ACCOUNTS.items():
            ign = d.get("player", {}).get("name", "").strip().lower()
            if ign == acc_str:
                return p
    # Mặc định lấy account đầu tiên đang online
    return sorted(ACTIVE_ACCOUNTS.keys())[0]

def get_accounts_summary_list():
    summary = []
    for p in sorted(ACTIVE_ACCOUNTS.keys()):
        d = ACTIVE_ACCOUNTS[p]
        player = d.get("player", {})
        earnings = d.get("earnings", {})
        shards = d.get("shards", {})
        inv = d.get("inventory", {})
        nearby = d.get("nearbyEntities", [])
        axe_sell = earnings.get("axeSell", {})
        launcher = player.get("launcher") or d.get("launcher") or get_launcher_for_port(p)
        summary.append({
            "id": str(p),
            "port": p,
            "name": player.get("name", f"Bot-{p}"),
            "launcher": launcher,
            "balance": player.get("balance", "0$"),
            "shards": shards.get("balanceFormatted", player.get("shards", "0 ★")),
            "shardsPerHour": shards.get("perHourFormatted", "+0/h"),
            "server": player.get("server", "Chưa kết nối"),
            "health": player.get("health", 20.0),
            "maxHealth": player.get("maxHealth", 20.0),
            "food": player.get("food", 20),
            "x": player.get("x", 0.0),
            "y": player.get("y", 0.0),
            "z": player.get("z", 0.0),
            "perHour": earnings.get("perHourFormatted", "$0/h"),
            "totalEarned": earnings.get("totalEarnedFormatted", "$0"),
            "uptime": earnings.get("uptimeFormatted", "00h 00m 00s"),
            "totalShardsEarned": shards.get("totalEarnedFormatted", "0"),
            "axeSellHits": axe_sell.get("hits", 0),
            "axeSellEarned": axe_sell.get("earnedFormatted", "$0"),
            "emeralds": inv.get("emeralds", 0),
            "nearbyCount": len(nearby)
        })
    return summary

# ----------------------------------------------------------------------
# Routes
# ----------------------------------------------------------------------
@app.route("/")
def index():
    return send_from_directory("mobile_app", "index.html")

@app.route("/payouts")
@app.route("/mt-store")
def payouts_view():
    return send_from_directory("mobile_app", "payouts.html")

@app.route("/sw.js")
def service_worker():
    response = send_from_directory("mobile_app", "sw.js")
    response.headers["Content-Type"] = "application/javascript"
    response.headers["Service-Worker-Allowed"] = "/"
    return response

@app.route("/manifest.json")
def manifest():
    response = send_from_directory("mobile_app", "manifest.json")
    response.headers["Content-Type"] = "application/manifest+json"
    return response

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

@app.route("/<path:path>")
def static_files(path):
    return send_from_directory("mobile_app", path)

@app.route("/api/server-info")
def server_info():
    return jsonify({
        "local_ip": LOCAL_IP,
        "port": PORT,
        "mobile_url": f"http://{LOCAL_IP}:{PORT}",
        "public_url": PUBLIC_4G_URL
    })

@app.route("/api/accounts", methods=["GET"])
def api_accounts():
    """Lấy danh sách tất cả các tài khoản Minecraft đang mở qua MultiMC"""
    accs = get_accounts_summary_list()
    return jsonify({
        "count": len(accs),
        "accounts": accs
    })

@app.route("/api/status", methods=["GET"])
def proxy_status():
    """Proxy dữ liệu trạng thái tài khoản đang được chọn (hoặc tài khoản đầu tiên)"""
    acc_id = request.args.get("acc")
    target_port = resolve_target_port(acc_id)
    accounts_list = get_accounts_summary_list()

    if target_port:
        try:
            req = urllib.request.Request(f"http://127.0.0.1:{target_port}/api/status", headers={"User-Agent": "PaladiMobile/1.0"})
            with urllib.request.urlopen(req, timeout=0.6) as response:
                fresh = json.loads(response.read().decode("utf-8"))
                if fresh and fresh.get("player"):
                    fresh["port"] = target_port
                    fresh["id"] = str(target_port)
                    fresh["gameConnected"] = True
                    fresh["public_url"] = PUBLIC_4G_URL
                    fresh["accounts"] = accounts_list
                    fresh["activeAccountId"] = str(target_port)
                    fresh["totalAccounts"] = len(accounts_list)
                    ACTIVE_ACCOUNTS[target_port] = fresh
                    return jsonify(fresh)
        except Exception:
            pass

    if target_port and target_port in ACTIVE_ACCOUNTS:
        data = dict(ACTIVE_ACCOUNTS[target_port])
        data["gameConnected"] = True
        data["public_url"] = PUBLIC_4G_URL
        data["accounts"] = accounts_list
        data["activeAccountId"] = str(target_port)
        data["totalAccounts"] = len(accounts_list)
        return jsonify(data)

    # Fallback khi chưa mở bất kỳ account MultiMC nào
    return jsonify({
        "gameConnected": False,
        "message": "Đang chờ MultiMC / Minecraft Client kết nối...",
        "public_url": PUBLIC_4G_URL,
        "accounts": [],
        "activeAccountId": None,
        "totalAccounts": 0,
        "player": {
            "name": "Chờ kết nối MultiMC",
            "health": 20.0,
            "maxHealth": 20.0,
            "food": 20,
            "x": 0.0,
            "y": 64.0,
            "z": 0.0,
            "server": "Chưa kết nối",
            "balance": "0$"
        },
        "nearbyEntities": [],
        "history": [],
        "earnings": {
            "totalEarned": 0.0,
            "totalEarnedFormatted": "$0",
            "perHour": 0.0,
            "perHourFormatted": "$0/h",
            "uptimeSeconds": 0,
            "uptimeFormatted": "00h 00m 00s"
        },
        "shards": {
            "balance": "0",
            "balanceFormatted": "0 ★",
            "balanceNum": 0.0,
            "inventoryCount": 0,
            "totalEarned": 0.0,
            "totalEarnedFormatted": "+0",
            "perHour": 0.0,
            "perHourFormatted": "+0/h"
        }
    })

@app.route("/api/pay", methods=["POST", "GET"])
def proxy_pay():
    """Gửi lệnh /pay từ app điện thoại vào tài khoản được chỉ định"""
    if request.method == "POST":
        payload = request.get_json(silent=True) or {}
        target = payload.get("target", "").strip()
        amount = payload.get("amount", "").strip()
        mode = payload.get("mode", "pay").strip()
        acc = payload.get("acc") or payload.get("account")
        force = bool(payload.get("force", False))
    else:
        target = request.args.get("target", "").strip()
        amount = request.args.get("amount", "").strip()
        mode = request.args.get("mode", "pay").strip()
        acc = request.args.get("acc") or request.args.get("account")
        force = request.args.get("force") == "true"

    if not target or not amount:
        return jsonify({"success": False, "error": "Vui lòng nhập tên người chơi và số tiền!"}), 400

    target_port = resolve_target_port(acc)
    if not target_port:
        return jsonify({"success": False, "senderOffline": True, "error": "Không có tài khoản Minecraft nào đang mở trong Dawn Launcher / MultiMC!"}), 503

    acc_info = ACTIVE_ACCOUNTS.get(target_port, {})
    if acc_info.get("inGame") is False:
        sender_name = acc_info.get("player", {}).get("name", "Tài khoản của bạn")
        return jsonify({
            "success": False,
            "senderOffline": True,
            "error": f"Tài khoản người bán ({sender_name}) đang OFFLINE (chưa vào server)! Hãy cho acc vào game trước khi pay."
        }), 400

    try:
        post_data = json.dumps({"target": target, "amount": amount, "mode": mode, "force": force}).encode("utf-8")
        req = urllib.request.Request(
            f"http://127.0.0.1:{target_port}/api/pay",
            data=post_data,
            headers={"Content-Type": "application/json", "User-Agent": "PaladiMobile/1.0"}
        )
        with urllib.request.urlopen(req, timeout=2.5) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            res_data["senderPort"] = target_port
            res_data["senderName"] = ACTIVE_ACCOUNTS.get(target_port, {}).get("player", {}).get("name", "Unknown")
            if res_data.get("success"):
                try:
                    # Đợi 350ms để Minecraft render thông báo lên Action Bar và Chat
                    time.sleep(0.35)
                    proof_info = capture_in_game_screenshot(target, amount, acc_identifier=target_port)
                    res_data["proof"] = proof_info
                except Exception as pe:
                    print(f"Error capturing in-game proof on pay: {pe}")
            return jsonify(res_data)
    except Exception as e:
        return jsonify({
            "success": False,
            "error": f"Không thể gửi lệnh tới acc trên port {target_port}: {str(e)}"
        }), 502

@app.route("/api/delivery-proofs", methods=["GET"])
def api_delivery_proofs():
    """Lấy danh sách các ảnh chụp bằng chứng giao hàng gần đây"""
    history = load_proofs_history()
    return jsonify({
        "count": len(history),
        "proofs": history
    })

@app.route("/api/delivery-proof/<proof_id>", methods=["GET"])
def api_delivery_proof_image(proof_id):
    """Xem hoặc tải ảnh chụp bằng chứng giao hàng về điện thoại"""
    filename = f"{proof_id}.png" if not proof_id.endswith(".png") else proof_id
    file_path = PROOFS_DIR / filename
    if not file_path.exists():
        return jsonify({"error": "Không tìm thấy ảnh bằng chứng"}), 404
    as_attachment = request.args.get("download") == "1"
    download_name = request.args.get("filename") or f"{filename}"
    return send_file(
        str(file_path),
        mimetype="image/png",
        as_attachment=as_attachment,
        download_name=download_name
    )

@app.route("/api/create-proof", methods=["POST", "GET"])
def api_create_proof():
    """Tự tạo hoặc chụp thử ảnh bằng chứng giao hàng"""
    if request.method == "POST":
        payload = request.get_json(silent=True) or {}
        target = payload.get("target", "Shedynek").strip()
        amount = payload.get("amount", "40M").strip()
    else:
        target = request.args.get("target", "Shedynek").strip()
        amount = request.args.get("amount", "40M").strip()
    
    acc = payload.get("acc") if request.method == "POST" else request.args.get("acc")
    proof_info = capture_in_game_screenshot(target, amount, acc_identifier=acc)
    return jsonify({
        "success": True,
        "proof": proof_info
    })

@app.route("/api/command", methods=["POST", "GET"])
def proxy_command():
    """Gửi câu lệnh Minecraft tùy ý từ app điện thoại vào tài khoản được chọn"""
    if request.method == "POST":
        payload = request.get_json(silent=True) or {}
        command = payload.get("command", "").strip()
        acc = payload.get("acc") or payload.get("account")
    else:
        command = request.args.get("command", request.args.get("cmd", "")).strip()
        acc = request.args.get("acc") or request.args.get("account")

    if not command:
        return jsonify({"success": False, "error": "Vui lòng nhập câu lệnh!"}), 400

    target_port = resolve_target_port(acc)
    if not target_port:
        return jsonify({"success": False, "error": "Không có tài khoản Minecraft nào online!"}), 503

    try:
        post_data = json.dumps({"command": command}).encode("utf-8")
        req = urllib.request.Request(
            f"http://127.0.0.1:{target_port}/api/command",
            data=post_data,
            headers={"Content-Type": "application/json", "User-Agent": "PaladiMobile/1.0"}
        )
        with urllib.request.urlopen(req, timeout=2.5) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            return jsonify(res_data)
    except Exception as e:
        return jsonify({
            "success": False,
            "error": f"Lỗi gửi lệnh tới game (port {target_port}): {str(e)}"
        }), 502

@app.route("/api/reset-stats", methods=["POST", "GET"])
def proxy_reset_stats():
    """Gửi yêu cầu đặt lại thống kê doanh thu /h"""
    acc = request.args.get("acc")
    target_port = resolve_target_port(acc)
    if not target_port:
        return jsonify({"success": True, "message": "Đã reset trên máy chủ trung gian"})

    try:
        req = urllib.request.Request(
            f"http://127.0.0.1:{target_port}/api/reset-stats",
            headers={"User-Agent": "PaladiMobile/1.0"}
        )
        with urllib.request.urlopen(req, timeout=2.0) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            return jsonify(res_data)
    except Exception as e:
        return jsonify({"success": True, "message": "Đã reset trên máy chủ trung gian"})

# ======================================================================
# ELDORADO PAYOUT TRACKER & REAL-TIME ALERTS
# ======================================================================
ELDORADO_CONFIG_FILE = Path(__file__).parent / "eldorado_config.json"
ELDORADO_NOTIFIED_FILE = Path(__file__).parent / "eldorado_notified.json"
ELDORADO_EVENTS = []
ELDORADO_CACHE = {
    "configured": False,
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

def load_eldorado_config():
    default_cfg = {
        "client_id": "9dcffa15-088b-4eca-b8b1-6509abb25e3a",
        "client_secret": "PWAgwlExYqo_4WqG0gw007SAM5PsDwKUm8JgCdASWkM",
        "sound_enabled": True,
        "desktop_notify": True,
        "check_interval": 30
    }
    if ELDORADO_CONFIG_FILE.exists():
        try:
            with open(ELDORADO_CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                default_cfg.update(data)
        except Exception:
            pass
    return default_cfg

def save_eldorado_config(cfg):
    try:
        with open(ELDORADO_CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False, indent=2)
    except Exception:
        pass

def load_eldorado_notified():
    if ELDORADO_NOTIFIED_FILE.exists():
        try:
            with open(ELDORADO_NOTIFIED_FILE, "r", encoding="utf-8") as f:
                return set(json.load(f))
        except Exception:
            pass
    return set()

def save_eldorado_notified(notified_set):
    try:
        with open(ELDORADO_NOTIFIED_FILE, "w", encoding="utf-8") as f:
            json.dump(list(notified_set), f, ensure_ascii=False, indent=2)
    except Exception:
        pass

def parse_iso_datetime(date_str):
    if not date_str:
        return None
    try:
        clean = date_str.replace("Z", "+00:00")
        dt = datetime.fromisoformat(clean)
        # Convert UTC to Vietnam local time (+7 hours)
        return (dt + timedelta(hours=7)).replace(tzinfo=None)
    except Exception:
        pass
    for fmt in ("%Y-%m-%dT%H:%M:%S.%f", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(date_str[:19], fmt) + timedelta(hours=7)
        except Exception:
            pass
    return None

def trigger_payout_alert(order_id, buyer, amount, currency):
    """Kích hoạt âm thanh máy tính, Windows Toast và Web Event khi tiền về"""
    cfg = load_eldorado_config()
    clean_id = order_id[:8] if order_id else "N/A"

    # 1. Phát âm thanh máy tính
    if cfg.get("sound_enabled", True):
        try:
            import winsound
            winsound.MessageBeep(winsound.MB_ICONEXCLAMATION)
        except Exception:
            pass

    # 2. Thông báo Windows Toast
    if cfg.get("desktop_notify", True):
        try:
            title = "TIEN ELDORADO DA VE!"
            msg = f"Don {clean_id}.. ({buyer}): +{amount} {currency} da den han nhan tien ve vi!"
            ps = f'''
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            $x = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
            $x.GetElementsByTagName("text")[0].AppendChild($x.CreateTextNode('{title}')) | Out-Null
            $x.GetElementsByTagName("text")[1].AppendChild($x.CreateTextNode('{msg}')) | Out-Null
            $t = [Windows.UI.Notifications.ToastNotification]::new($x)
            [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('PaladiPay').Show($t)
            '''
            subprocess.Popen(["powershell", "-NoProfile", "-Command", ps], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass

    # 3. Hàng đợi sự kiện cho Web & App điện thoại
    event = {
        "id": uuid.uuid4().hex[:8],
        "time": datetime.now().strftime("%H:%M:%S %d/%m/%Y"),
        "order_id": order_id,
        "buyer": buyer,
        "amount": amount,
        "currency": currency,
        "title": "💰 Tiền Eldorado Đã Về!",
        "message": f"Đơn hàng #{clean_id}.. ({buyer}) giá trị {amount} {currency} đã được mở khóa tiền về ví!",
        "timestamp": int(time.time() * 1000)
    }
    ELDORADO_EVENTS.insert(0, event)
    if len(ELDORADO_EVENTS) > 50:
        ELDORADO_EVENTS.pop()
    print(f"\n[$$$] 🔔 THONG BAO TIEN VE ELDORADO: Don #{clean_id}.. ({buyer}) +{amount} {currency}!")

ELDORADO_AUTH = {
    "token": None,
    "expires_at": 0
}

def get_eldorado_access_token():
    cfg = load_eldorado_config()
    client_id = cfg.get("client_id", "").strip()
    client_secret = cfg.get("client_secret", "").strip()
    if not client_id or not client_secret:
        return None, "Chưa cấu hình Client ID và Secret của Eldorado"

    now_ts = time.time()
    if ELDORADO_AUTH["token"] and now_ts < (ELDORADO_AUTH["expires_at"] - 60):
        return ELDORADO_AUTH["token"], None

    try:
        url = "https://www.eldorado.gg/api/authentication/seller/token"
        payload = json.dumps({"clientId": client_id, "clientSecret": client_secret}).encode("utf-8")
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
    """Hàm đồng bộ và tính toán thời gian nhận tiền từ tài khoản Eldorado chính chủ"""
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
        notified = load_eldorado_notified()
        new_notified = False

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
                estimate_date = rec_date + timedelta(days=5) # Received + 5 ngày
            else:
                del_date = get_latest_log("Delivered")
                if not del_date and curr_state == "Delivered":
                    del_date = parse_iso_datetime(state_obj.get("createdDate"))
                if del_date:
                    base_state = "Delivered"
                    base_date = del_date
                    estimate_date = del_date + timedelta(days=8) # Delivered + 8 ngày

            if not estimate_date:
                continue

            order_id = o.get("id") or "N/A"
            buyer = o.get("buyerUsername") or "Buyer"
            price_obj = o.get("totalPrice") or {}
            amount = price_obj.get("amount", 0.0)
            currency = price_obj.get("currency", "USD")

            diff_sec = (estimate_date - now).total_seconds()
            is_overdue = diff_sec <= 0

            # Tính chuỗi đếm ngược
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

            # KIỂM TRA ĐẾN HẠN TIỀN VỀ ĐỂ PHÁT THÔNG BÁO!
            if is_overdue and order_id not in notified:
                notified.add(order_id)
                new_notified = True
                trigger_payout_alert(order_id, buyer, amount, currency)

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

        if new_notified:
            save_eldorado_notified(notified)

        # Sắp xếp: đơn sắp đến hạn nhất lên đầu
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

def eldorado_monitor_loop():
    """Luồng ngầm định kỳ quét tiền về của Eldorado"""
    time.sleep(3)
    while True:
        try:
            sync_eldorado_orders()
        except Exception:
            pass
        cfg = load_eldorado_config()
        interval = max(10, int(cfg.get("check_interval", 30)))
        time.sleep(interval)

@app.route("/api/eldorado/payouts", methods=["GET"])
def api_eldorado_payouts():
    """API lấy danh sách lịch tiền về Eldorado"""
    force = request.args.get("refresh") == "1"
    if force or not ELDORADO_CACHE.get("last_sync"):
        sync_eldorado_orders()
    return jsonify(ELDORADO_CACHE)

@app.route("/api/eldorado/settings", methods=["GET", "POST"])
def api_eldorado_settings():
    """Lấy hoặc cập nhật cấu hình kết nối MT Store / Eldorado"""
    if request.method == "POST":
        payload = request.get_json(silent=True) or {}
        cfg = load_eldorado_config()
        if "client_id" in payload:
            cfg["client_id"] = payload["client_id"].strip()
        if "client_secret" in payload:
            cfg["client_secret"] = payload["client_secret"].strip()
        if "sound_enabled" in payload:
            cfg["sound_enabled"] = bool(payload["sound_enabled"])
        if "desktop_notify" in payload:
            cfg["desktop_notify"] = bool(payload["desktop_notify"])
        if "check_interval" in payload:
            cfg["check_interval"] = max(10, int(payload["check_interval"]))
        save_eldorado_config(cfg)
        threading.Thread(target=sync_eldorado_orders, daemon=True).start()
        return jsonify({"success": True, "message": "Đã lưu cấu hình Eldorado thành công!"})

    cfg = load_eldorado_config()
    masked = dict(cfg)
    cid = masked.get("client_id", "")
    sec = masked.get("client_secret", "")
    masked["has_creds"] = bool(cid and sec)
    if sec and len(sec) > 8:
        masked["client_secret"] = sec[:4] + "..." + sec[-4:]
    return jsonify(masked)

@app.route("/api/eldorado/test-notify", methods=["POST", "GET"])
def api_eldorado_test_notify():
    """Kích hoạt chuông và thông báo test thử nghiệm"""
    trigger_payout_alert("test_order_9999", "Tester-Demo", 30.3, "USD")
    return jsonify({"success": True, "message": "Đã gửi thông báo test thành công!"})

@app.route("/api/eldorado/events", methods=["GET"])
def api_eldorado_events():
    """Lấy danh sách các sự kiện tiền về thời gian thực"""
    since = int(request.args.get("since", 0))
    unread = [e for e in ELDORADO_EVENTS if e.get("timestamp", 0) > since]
    return jsonify({"events": unread, "total": len(ELDORADO_EVENTS)})

def print_banner():
    mobile_url = f"http://{LOCAL_IP}:{PORT}"
    border = "=" * 66
    try:
        print("\n" + border)
        print("      [*] PALADI MULTI-MC CONTROLLER - HE THONG QUAN LY DA TAI KHOAN")
        print(border)
        print(" [v] May chu dieu khien da san sang hoat dong!")
        print(" [v] Ho tro tu dong nhan dien 1 hoac nhieu acc tren MultiMC!")
        print(" [v] He thong theo doi Tien ve Eldorado (Payout Tracker) da san sang!")
        print(f" [1] Khi o nha (Cung Wi-Fi): {mobile_url}")
        print(" [2] Khi o ngoai duong (Dung 4G/5G/Internet): Dang khoi tao tunnel...")
        print(border)
        
        # In mã QR Code LAN
        try:
            import qrcode
            qr = qrcode.QRCode(border=1)
            qr.add_data(mobile_url)
            qr.make(fit=True)
            print(" [!] QUET QR DUOI DAY DE VAO NHANH QUA WI-FI NOI BO:")
            qr.print_ascii(invert=True)
            print(border + "\n")
        except Exception:
            pass
    except Exception as e:
        print(f"Server started at {mobile_url}")

if __name__ == "__main__":
    print_banner()
    # 1. Kích hoạt Cloudflare Quick Tunnel ở luồng ngầm
    t_cf = threading.Thread(target=start_cloudflared_tunnel, daemon=True)
    t_cf.start()
    
    # 2. Kích hoạt bộ dò quét Multi-Account MultiMC ở luồng ngầm
    t_scan = threading.Thread(target=scan_multi_accounts_loop, daemon=True)
    t_scan.start()

    # 3. Kích hoạt bộ theo dõi Payout Eldorado ở luồng ngầm
    t_el = threading.Thread(target=eldorado_monitor_loop, daemon=True)
    t_el.start()

    # 4. Chạy Flask server trên 0.0.0.0
    app.run(host="0.0.0.0", port=PORT, debug=False)

