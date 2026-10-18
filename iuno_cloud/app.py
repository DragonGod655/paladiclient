# -*- coding: utf-8 -*-
"""
IUNO SHOP - Cloud Web Service (24/7 Always-On on Render / Railway / Cloud)
Connects directly to Eldorado Seller API to track payouts and orders without needing a PC.
"""

import os
import json
import time
import urllib.request
import urllib.error
from datetime import datetime, timedelta
from flask import Flask, jsonify, request, send_from_directory

app = Flask(__name__, static_folder="static", static_url_path="")

# Credentials from Environment or default to user's registered keys
CLIENT_ID = os.getenv("ELDORADO_CLIENT_ID", "9dcffa15-088b-4eca-b8b1-6509abb25e3a")
CLIENT_SECRET = os.getenv("ELDORADO_CLIENT_SECRET", "PWAgwlExYqo_4WqG0gw007SAM5PsDwKUm8JgCdASWkM")

ELDORADO_AUTH = {
    "token": None,
    "expires_at": 0
}

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

ELDORADO_EVENTS = []

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
def home():
    return send_from_directory("static", "index.html")

@app.route("/api/eldorado/payouts", methods=["GET"])
def api_payouts():
    force = request.args.get("refresh") == "1"
    if force or not ELDORADO_CACHE.get("last_sync"):
        sync_eldorado_orders()
    return jsonify(ELDORADO_CACHE)

@app.route("/api/status", methods=["GET"])
def api_status():
    # Cloud status returns safe bot placeholder and shop status
    return jsonify({
        "status": "online",
        "cloud_mode": True,
        "accounts": [
            {"id": "cloud", "name": "BestLegitStore (Cloud)", "inGame": False, "balance": "28 đơn", "server": "Eldorado Cloud 24/7"}
        ],
        "nearbyPlayers": []
    })

@app.route("/api/eldorado/events", methods=["GET"])
def api_events():
    return jsonify({"events": ELDORADO_EVENTS, "total": len(ELDORADO_EVENTS)})

@app.route("/api/eldorado/settings", methods=["GET", "POST"])
def api_settings():
    return jsonify({
        "client_id": CLIENT_ID[:6] + "..." if CLIENT_ID else "",
        "has_creds": bool(CLIENT_ID and CLIENT_SECRET),
        "sound_enabled": True,
        "desktop_notify": True
    })

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    app.run(host="0.0.0.0", port=port)
