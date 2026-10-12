import os
import sys
import math
import time
import json
import threading
import urllib.request
from pathlib import Path

import tkinter as tk
from tkinter import ttk
from PIL import Image, ImageTk, ImageDraw

AVATAR_DIR = Path(os.path.expanduser("~/.chibi/avatars"))
AVATAR_DIR.mkdir(parents=True, exist_ok=True)

class AvatarManager:
    """Asynchronous player avatar loader & cacher"""
    def __init__(self):
        self.cache = {} # name -> ImageTk.PhotoImage
        self.raw_images = {} # name -> PIL.Image
        self.loading = set()

    def get_default_avatar(self, name, size=24, is_player=True, is_villager=False):
        # Create a clean pixelated default head
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        
        if is_villager:
            # Villager brown head
            draw.rectangle([2, 2, size - 3, size - 3], fill="#8d5524", outline="#5c3818", width=1)
            # Nose
            draw.rectangle([size//2 - 2, size//2 - 1, size//2 + 1, size//2 + 4], fill="#b37340")
            # Eyes
            draw.rectangle([4, size//3, 7, size//3 + 2], fill="#2e7d32")
            draw.rectangle([size - 8, size//3, size - 5, size//3 + 2], fill="#2e7d32")
        else:
            # Player Steve/Alex colored head
            # Use hash of name for consistent unique color if player
            h = abs(hash(name))
            r = (h & 0xFF) % 150 + 60
            g = ((h >> 8) & 0xFF) % 150 + 60
            b = ((h >> 16) & 0xFF) % 150 + 60
            draw.rectangle([1, 1, size - 2, size - 2], fill=(r, g, b, 255), outline="#0f172a", width=1)
            # Eyes
            draw.rectangle([3, size//3, 7, size//3 + 3], fill="#ffffff")
            draw.rectangle([5, size//3 + 1, 7, size//3 + 3], fill="#2563eb")
            draw.rectangle([size - 8, size//3, size - 4, size//3 + 3], fill="#ffffff")
            draw.rectangle([size - 8, size//3 + 1, size - 6, size//3 + 3], fill="#2563eb")
            # Mouth / Hair
            draw.rectangle([1, 1, size - 2, size//4], fill="#451a03")
        
        return img

    def get_avatar(self, name, size=24, is_villager=False):
        key = f"{name}_{size}_{is_villager}"
        if key in self.cache:
            return self.cache[key]

        # Check local disk cache
        local_file = AVATAR_DIR / f"{name}_{size}.png"
        if local_file.exists():
            try:
                img = Image.open(local_file).convert("RGBA").resize((size, size), Image.Resampling.NEAREST)
                photo = ImageTk.PhotoImage(img)
                self.cache[key] = photo
                self.raw_images[key] = img
                return photo
            except Exception:
                pass

        # Generate default & request online async
        default_img = self.get_default_avatar(name, size=size, is_villager=is_villager)
        photo = ImageTk.PhotoImage(default_img)
        self.cache[key] = photo
        self.raw_images[key] = default_img

        if not is_villager and name and name not in self.loading:
            self.loading.add(name)
            threading.Thread(target=self._fetch_avatar_online, args=(name, size, key, local_file), daemon=True).start()

        return photo

    def _fetch_avatar_online(self, name, size, key, local_file):
        urls = [
            f"https://mc-heads.net/avatar/{name}/{size}",
            f"https://minotar.net/avatar/{name}/{size}"
        ]
        for url in urls:
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "PaladiClientApp/2.5"})
                with urllib.request.urlopen(req, timeout=2.0) as resp:
                    if resp.status == 200:
                        img_data = resp.read()
                        with open(local_file, "wb") as f:
                            f.write(img_data)
                        img = Image.open(local_file).convert("RGBA").resize((size, size), Image.Resampling.NEAREST)
                        photo = ImageTk.PhotoImage(img)
                        self.cache[key] = photo
                        self.raw_images[key] = img
                        break
            except Exception:
                pass

class PaladiControlApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Paladi Auto Villager & Live Radar Hub")
        self.root.geometry("1280x840")
        self.root.minsize(1050, 700)
        self.root.configure(bg="#0b0f19")

        # Bring window to front
        self.root.lift()
        self.root.attributes('-topmost', True)
        self.root.after_idle(self.root.attributes, '-topmost', False)
        self.root.focus_force()

        # Avatar Manager
        self.avatars = AvatarManager()
        self.drawn_images = [] # Prevent garbage collection of canvas images

        # Styles
        self.style = ttk.Style()
        self.style.theme_use("clam")
        
        self.style.configure("Treeview", 
                             background="#131b2e", 
                             foreground="#e2e8f0", 
                             fieldbackground="#131b2e", 
                             rowheight=28,
                             font=("Segoe UI", 9))
        self.style.configure("Treeview.Heading", 
                             background="#1e293b", 
                             foreground="#38bdf8", 
                             font=("Segoe UI", 9, "bold"))
        self.style.map("Treeview", background=[('selected', '#0284c7')])

        # Palette
        self.bg_dark = "#0b0f19"
        self.bg_card = "#131b2e"
        self.bg_card_alt = "#1e293b"
        self.accent_cyan = "#06b6d4"
        self.accent_blue = "#38bdf8"
        self.accent_green = "#10b981"
        self.accent_purple = "#a855f7"
        self.accent_red = "#ef4444"
        self.accent_yellow = "#f59e0b"
        self.text_main = "#f8fafc"
        self.text_dim = "#94a3b8"

        # State data
        self.state_file = Path(os.path.expanduser("~/.chibi/paladi_live_state.json"))
        self.live_data = {}
        self.is_connected = False
        self.radar_zoom = 2.4

        self.setup_ui()
        self.start_sync_thread()

    def setup_ui(self):
        # ─── HEADER ───
        header = tk.Frame(self.root, bg=self.bg_card, height=65, padx=18, pady=10)
        header.pack(fill="x", side="top")

        title_box = tk.Frame(header, bg=self.bg_card)
        title_box.pack(side="left")

        title = tk.Label(title_box, text="💎 PALADI CLIENT CONTROL & LIVE RADAR", font=("Segoe UI", 15, "bold"), fg=self.accent_blue, bg=self.bg_card)
        title.pack(anchor="w")

        self.lbl_conn = tk.Label(title_box, text="● Đang chờ kết nối với Minecraft (Fabric 1.21.4)...", font=("Segoe UI", 9), fg=self.text_dim, bg=self.bg_card)
        self.lbl_conn.pack(anchor="w")

        # Threat Alert Badge in Header
        self.threat_badge = tk.Label(header, text="🛡️ KHU VỰC AN TOÀN (0 NGƯỜI CHƠI)", font=("Segoe UI", 10, "bold"), bg="#064e3b", fg="#34d399", padx=14, pady=6, bd=1, relief="solid")
        self.threat_badge.pack(side="right")

        # ─── MAIN CONTENT (Notebook Tabs) ───
        notebook_frame = tk.Frame(self.root, bg=self.bg_dark, padx=12, pady=10)
        notebook_frame.pack(fill="both", expand=True)

        self.notebook = ttk.Notebook(notebook_frame)
        self.notebook.pack(fill="both", expand=True)

        # TAB 1: Radar & Giám Sát Người Chơi (Map / Radar)
        self.tab_radar = tk.Frame(self.notebook, bg=self.bg_dark, padx=10, pady=10)
        self.notebook.add(self.tab_radar, text=" 🗺️ Bản Đồ Radar Avatar & Dân Làng ")

        # TAB 2: Lịch Sử Bán & Giao Dịch (Sales History)
        self.tab_history = tk.Frame(self.notebook, bg=self.bg_dark, padx=10, pady=10)
        self.notebook.add(self.tab_history, text=" 💰 Lịch Sử Bán Hàng & Order ")

        # TAB 3: Kho Đồ & Trạng Thái Bot (Status & Inventory)
        self.tab_status = tk.Frame(self.notebook, bg=self.bg_dark, padx=10, pady=10)
        self.notebook.add(self.tab_status, text=" 📊 Trạng Thái & Kho Đồ ")

        self.build_radar_tab()
        self.build_history_tab()
        self.build_status_tab()

    # ──────────────────────────────────────────────
    #  BUILD TAB 1: RADAR & MINI-MAP WITH AVATARS
    # ──────────────────────────────────────────────
    def build_radar_tab(self):
        paned = tk.PanedWindow(self.tab_radar, orient="horizontal", bg=self.bg_dark, sashwidth=6)
        paned.pack(fill="both", expand=True)

        # Radar Canvas Box
        radar_box = tk.Frame(paned, bg=self.bg_card, bd=1, relief="solid", padx=10, pady=10)
        paned.add(radar_box, minsize=560)

        # Radar Controls Header
        r_head = tk.Frame(radar_box, bg=self.bg_card)
        r_head.pack(fill="x", pady=(0, 6))

        tk.Label(r_head, text="📡 RADAR AVATAR NGƯỜI CHƠI (BÁN KÍNH 64M)", font=("Segoe UI", 10, "bold"), fg=self.accent_cyan, bg=self.bg_card).pack(side="left")

        # Zoom buttons
        btn_zin = tk.Button(r_head, text="➕ Phóng to", font=("Segoe UI", 8), bg="#334155", fg="white", relief="flat", padx=6, command=lambda: self.adjust_zoom(0.4))
        btn_zin.pack(side="right", padx=2)
        btn_zout = tk.Button(r_head, text="➖ Thu nhỏ", font=("Segoe UI", 8), bg="#334155", fg="white", relief="flat", padx=6, command=lambda: self.adjust_zoom(-0.4))
        btn_zout.pack(side="right", padx=2)

        # Canvas
        self.radar_canvas = tk.Canvas(radar_box, bg="#070a13", highlightthickness=1, highlightbackground="#334155")
        self.radar_canvas.pack(fill="both", expand=True)

        # Radar Legend
        legend_box = tk.Frame(radar_box, bg=self.bg_card, pady=6)
        legend_box.pack(fill="x")
        tk.Label(legend_box, text="👤 Avatar Bạn", font=("Segoe UI", 8, "bold"), fg=self.accent_blue, bg=self.bg_card).pack(side="left", padx=8)
        tk.Label(legend_box, text="🔴 Avatar Người khác (Khung Đỏ)", font=("Segoe UI", 8, "bold"), fg=self.accent_red, bg=self.bg_card).pack(side="left", padx=8)
        tk.Label(legend_box, text="👨‍🌾 Dân làng (Xanh lá)", font=("Segoe UI", 8, "bold"), fg=self.accent_green, bg=self.bg_card).pack(side="left", padx=8)
        tk.Label(legend_box, text="❌ Dân làng hết lượt (Xám)", font=("Segoe UI", 8, "bold"), fg="#64748b", bg=self.bg_card).pack(side="left", padx=8)
        tk.Label(legend_box, text="★ Điểm Home (Vàng)", font=("Segoe UI", 8, "bold"), fg=self.accent_yellow, bg=self.bg_card).pack(side="left", padx=8)

        # Right: Info & Entity List
        info_box = tk.Frame(paned, bg=self.bg_card, bd=1, relief="solid", padx=12, pady=10)
        paned.add(info_box, minsize=420)

        tk.Label(info_box, text="👥 DANH SÁCH NGƯỜI CHƠI & DÂN LÀNG XUNG QUANH", font=("Segoe UI", 10, "bold"), fg=self.accent_blue, bg=self.bg_card).pack(anchor="w", pady=(0, 6))

        # Entities Treeview
        columns = ("type", "name", "dist", "info")
        self.tree_entities = ttk.Treeview(info_box, columns=columns, show="headings", height=12)
        self.tree_entities.heading("type", text="Loại")
        self.tree_entities.heading("name", text="Tên / ID")
        self.tree_entities.heading("dist", text="Khoảng cách")
        self.tree_entities.heading("info", text="Trạng thái / Đồ cầm")

        self.tree_entities.column("type", width=70, anchor="center")
        self.tree_entities.column("name", width=110, anchor="w")
        self.tree_entities.column("dist", width=85, anchor="center")
        self.tree_entities.column("info", width=140, anchor="w")

        self.tree_entities.pack(fill="both", expand=True)

        # Live Player Pos Card
        self.pos_card = tk.LabelFrame(info_box, text=" 📍 Vị Trí & Tọa Độ Bạn ", font=("Segoe UI", 9, "bold"), fg=self.accent_cyan, bg=self.bg_card_alt, padx=10, pady=8)
        self.pos_card.pack(fill="x", pady=(10, 0))

        self.lbl_pos_text = tk.Label(self.pos_card, text="Tọa độ: X: --- | Y: --- | Z: --- | Hướng: ---°", font=("Segoe UI", 9), fg=self.text_main, bg=self.bg_card_alt)
        self.lbl_pos_text.pack(anchor="w")

        self.lbl_hp_text = tk.Label(self.pos_card, text="Máu: --/20 HP | Thức ăn: --/20", font=("Segoe UI", 9), fg=self.accent_green, bg=self.bg_card_alt)
        self.lbl_hp_text.pack(anchor="w")

    # ──────────────────────────────────────────────
    #  BUILD TAB 2: SALES & TRADE HISTORY
    # ──────────────────────────────────────────────
    def build_history_tab(self):
        head = tk.Frame(self.tab_history, bg=self.bg_dark)
        head.pack(fill="x", pady=(0, 8))

        tk.Label(head, text="📜 LỊCH SỬ BÁN HÀNG & GIAO DỊCH TỰ ĐỘNG", font=("Segoe UI", 12, "bold"), fg=self.accent_green, bg=self.bg_dark).pack(side="left")

        # History Treeview
        h_cols = ("time", "type", "item", "amount", "price", "total", "status")
        self.tree_history = ttk.Treeview(self.tab_history, columns=h_cols, show="headings", height=18)
        
        self.tree_history.heading("time", text="Thời gian")
        self.tree_history.heading("type", text="Loại GD")
        self.tree_history.heading("item", text="Vật phẩm")
        self.tree_history.heading("amount", text="Số lượng")
        self.tree_history.heading("price", text="Đơn giá / Thao tác")
        self.tree_history.heading("total", text="Chi tiết / Tổng")
        self.tree_history.heading("status", text="Trạng thái")

        self.tree_history.column("time", width=90, anchor="center")
        self.tree_history.column("type", width=80, anchor="center")
        self.tree_history.column("item", width=140, anchor="w")
        self.tree_history.column("amount", width=90, anchor="center")
        self.tree_history.column("price", width=140, anchor="center")
        self.tree_history.column("total", width=120, anchor="center")
        self.tree_history.column("status", width=110, anchor="center")

        self.tree_history.pack(fill="both", expand=True)

    # ──────────────────────────────────────────────
    #  BUILD TAB 3: INVENTORY & BOT STATUS
    # ──────────────────────────────────────────────
    def build_status_tab(self):
        grid = tk.Frame(self.tab_status, bg=self.bg_dark)
        grid.pack(fill="x", pady=(0, 14))

        self.c_emeralds = self.create_metric_card(grid, "❇️ Emeralds", "0", self.accent_green)
        self.c_emeralds.pack(side="left", fill="both", expand=True, padx=(0, 6))

        self.c_pork = self.create_metric_card(grid, "📦 Rương / Thịt", "0", self.accent_cyan)
        self.c_pork.pack(side="left", fill="both", expand=True, padx=3)

        self.c_berries = self.create_metric_card(grid, "🍓 Sweet Berries", "0", self.accent_purple)
        self.c_berries.pack(side="left", fill="both", expand=True, padx=3)

        self.c_gapples = self.create_metric_card(grid, "🍎 Táo Vàng", "0", self.accent_yellow)
        self.c_gapples.pack(side="left", fill="both", expand=True, padx=(6, 0))

        detail_panel = tk.LabelFrame(self.tab_status, text=" 🔄 Trạng Thái Chi Tiết Module ", font=("Segoe UI", 11, "bold"), fg=self.accent_blue, bg=self.bg_card, padx=16, pady=12, bd=1, relief="solid")
        detail_panel.pack(fill="both", expand=True)

        self.lbl_st_state = self.create_info_line(detail_panel, "Trạng thái máy trạng thái (State):", "TRADING_VILLAGER")
        self.lbl_st_missing = self.create_info_line(detail_panel, "Nguyên liệu đang thiếu:", "Không (Đang đủ đồ trade)")
        self.lbl_st_price = self.create_info_line(detail_panel, "Giá Order tính toán gần nhất:", "24.01 đ")
        self.lbl_st_progress = self.create_info_line(detail_panel, "Tiến độ đơn hàng (Your Orders):", "0/10K Delivered (Đang chờ)")
        self.lbl_st_restock = self.create_info_line(detail_panel, "Đếm ngược hồi lượt (Restock):", "0 giây (Đang sẵn sàng)")

    def create_metric_card(self, parent, title, val, color):
        card = tk.Frame(parent, bg=self.bg_card, padx=14, pady=12, bd=1, relief="solid")
        tk.Label(card, text=title, font=("Segoe UI", 9, "bold"), fg=self.text_dim, bg=self.bg_card).pack(anchor="w")
        lbl_v = tk.Label(card, text=val, font=("Segoe UI", 18, "bold"), fg=color, bg=self.bg_card)
        lbl_v.pack(anchor="w", pady=(4, 0))
        card.value_label = lbl_v
        return card

    def create_info_line(self, parent, label_text, default_val):
        row = tk.Frame(parent, bg=self.bg_card, pady=6)
        row.pack(fill="x")
        tk.Label(row, text=label_text, font=("Segoe UI", 9, "bold"), fg=self.text_main, bg=self.bg_card).pack(side="left")
        val = tk.Label(row, text=default_val, font=("Segoe UI", 9), fg=self.accent_cyan, bg=self.bg_card)
        val.pack(side="right")
        return val

    def adjust_zoom(self, delta):
        self.radar_zoom = max(1.0, min(5.0, self.radar_zoom + delta))

    # ──────────────────────────────────────────────
    #  DATA SYNC & RADAR DRAWING WITH AVATARS
    # ──────────────────────────────────────────────
    def start_sync_thread(self):
        t = threading.Thread(target=self.sync_loop, daemon=True)
        t.start()

    def sync_loop(self):
        while True:
            try:
                data = None
                # Method 1: HTTP API on 29388
                try:
                    req = urllib.request.Request("http://127.0.0.1:29388/api/status", headers={"User-Agent": "PaladiApp"})
                    with urllib.request.urlopen(req, timeout=0.25) as resp:
                        if resp.status == 200:
                            data = json.loads(resp.read().decode("utf-8"))
                except Exception:
                    pass

                # Method 2: Local JSON file fallback
                if not data and self.state_file.exists():
                    try:
                        with open(self.state_file, "r", encoding="utf-8") as f:
                            data = json.load(f)
                    except Exception:
                        pass

                if data:
                    self.live_data = data
                    self.is_connected = True
                    self.root.after(0, self.update_gui)
                else:
                    self.is_connected = False
                    self.root.after(0, self.update_offline_status)

            except Exception:
                pass

            time.sleep(0.25)

    def update_offline_status(self):
        self.lbl_conn.config(text="● Chưa phát hiện Minecraft chạy module AutoVillagerOrder...", fg=self.accent_red)

    def update_gui(self):
        if not self.live_data: return
        data = self.live_data

        p = data.get("player", {})
        st = data.get("status", {})
        inv = data.get("inventory", {})
        entities = data.get("nearbyEntities", [])
        history = data.get("history", [])

        # Header Connection Status
        p_name = p.get("name", "Player")
        self.lbl_conn.config(text=f"● Đã kết nối với {p_name} | Minecraft 1.21.4 Fabric", fg=self.accent_green)

        # Player stats & position card
        x, y, z = p.get("x", 0.0), p.get("y", 0.0), p.get("z", 0.0)
        yaw = p.get("yaw", 0.0)
        hp, max_hp = p.get("health", 20.0), p.get("maxHealth", 20.0)
        food = p.get("food", 20)
        self.lbl_pos_text.config(text=f"Tọa độ: X: {x:.1f} | Y: {y:.1f} | Z: {z:.1f} | Hướng: {yaw:.1f}°")
        self.lbl_hp_text.config(text=f"Máu: {hp:.1f}/{max_hp:.1f} HP | Thức ăn: {food}/20")

        # Threat Alert Badge
        players = [e for e in entities if e.get("type") == "player"]
        if players:
            closest = min(players, key=lambda e: float(e.get("dist", 999)))
            dist = float(closest.get("dist", 0))
            name = closest.get("name", "Người lạ")
            self.threat_badge.config(text=f"⚠️ CẢNH BÁO: CÓ '{name}' CÁCH {dist:.1f}M!", bg="#7f1d1d", fg="#fca5a5")
        else:
            self.threat_badge.config(text="🛡️ KHU VỰC AN TOÀN (0 NGƯỜI CHƠI)", bg="#064e3b", fg="#34d399")

        # Inventory Cards
        self.c_emeralds.value_label.config(text=f"{inv.get('emeralds', 0):,}")
        total_chests_pork = inv.get('chests', 0) if inv.get('chests', 0) > 0 else inv.get('porkchop', 0)
        self.c_pork.value_label.config(text=f"{total_chests_pork:,}")
        self.c_berries.value_label.config(text=f"{inv.get('berries', 0):,}")
        self.c_gapples.value_label.config(text=f"{inv.get('gapples', 0):,}")

        # Status Tab Details
        self.lbl_st_state.config(text=st.get("state", "UNKNOWN"))
        self.lbl_st_missing.config(text=st.get("missingItem", "Không"))
        self.lbl_st_price.config(text=f"{st.get('orderPrice', 0.0):.2f} đ")
        self.lbl_st_progress.config(text=st.get("orderProgress", "Chưa có"))
        restock_s = st.get("restockSeconds", 0)
        if restock_s > 0:
            self.lbl_st_restock.config(text=f"{restock_s}s ({restock_s//60} phút còn lại)")
        else:
            self.lbl_st_restock.config(text="0 giây (Sẵn sàng trade)")

        # Entities Treeview
        self.tree_entities.delete(*self.tree_entities.get_children())
        for e in sorted(entities, key=lambda x: float(x.get("dist", 999))):
            t = e.get("type", "")
            if t == "player":
                self.tree_entities.insert("", "end", values=("🔴 Player", e.get("name"), f"{e.get('dist', 0)}m", f"HP: {e.get('health', 20)} | {e.get('holding', 'Tay không')}"))
            elif t == "villager":
                locked = e.get("locked", False)
                status = "❌ Hết lượt" if locked else "✅ Sẵn sàng"
                self.tree_entities.insert("", "end", values=("👨‍🌾 Villager", f"ID: {e.get('id')}", f"{e.get('dist', 0)}m", status))

        # History Treeview
        self.tree_history.delete(*self.tree_history.get_children())
        for h in history:
            self.tree_history.insert("", "end", values=(
                h.get("time", ""),
                h.get("type", ""),
                h.get("item", ""),
                h.get("amount", ""),
                h.get("price", ""),
                h.get("total", ""),
                h.get("status", "")
            ))

        # Draw Radar Canvas with Player Avatars
        self.draw_radar_with_avatars(p, entities, st.get("homePos"))

    def draw_radar_with_avatars(self, player, entities, home_pos):
        c = self.radar_canvas
        c.delete("all")
        self.drawn_images.clear() # Clear references for current frame

        w = c.winfo_width()
        h = c.winfo_height()
        if w < 10 or h < 10: return

        cx = w / 2
        cy = h / 2
        px = player.get("x", 0.0)
        pz = player.get("z", 0.0)
        yaw = player.get("yaw", 0.0)
        p_name = player.get("name", "DragonGod")
        scale = self.radar_zoom

        # 1. Draw Grid & Concentric Rings (10m, 25m, 45m, 60m)
        c.create_line(cx, 0, cx, h, fill="#1e293b", dash=(2, 4))
        c.create_line(0, cy, w, cy, fill="#1e293b", dash=(2, 4))

        for radius_m in [10, 25, 45, 60]:
            r_px = radius_m * scale
            c.create_oval(cx - r_px, cy - r_px, cx + r_px, cy + r_px, outline="#1e293b", width=1, dash=(2, 4))
            c.create_text(cx + 4, cy - r_px + 8, text=f"{radius_m}m", fill="#475569", font=("Segoe UI", 7))

        # 2. Draw Home Pin
        if home_pos and len(home_pos) >= 3:
            hx, hz = home_pos[0], home_pos[2]
            dx = (hx - px) * scale
            dz = (hz - pz) * scale
            sx = cx + dx
            sy = cy + dz
            if 10 < sx < w - 10 and 10 < sy < h - 10:
                c.create_text(sx, sy, text="★", fill=self.accent_yellow, font=("Segoe UI", 15, "bold"))
                c.create_text(sx, sy + 13, text="Home", fill=self.accent_yellow, font=("Segoe UI", 8, "bold"))

        # 3. Draw Entities (Villagers & Other Players)
        for e in entities:
            ex = float(e.get("x", px))
            ez = float(e.get("z", pz))
            dx = (ex - px) * scale
            dz = (ez - pz) * scale
            sx = cx + dx
            sy = cy + dz

            # Check bounds
            if not (15 <= sx <= w - 15 and 15 <= sy <= h - 15): continue

            etype = e.get("type", "")
            if etype == "player":
                # ─── PLAYER WITH AVATAR ───
                name = e.get("name", "Player")
                dist = e.get("dist", 0)
                hp = e.get("health", 20.0)

                # Get avatar photo
                avatar_img = self.avatars.get_avatar(name, size=24)
                self.drawn_images.append(avatar_img)

                # Red Warning Glow & Border
                c.create_rectangle(sx - 14, sy - 14, sx + 14, sy + 14, outline=self.accent_red, width=2)
                # Avatar Image
                c.create_image(sx, sy, image=avatar_img)
                # Player Name & Distance Label
                c.create_text(sx, sy - 18, text=f"{name} ({dist}m)", fill="#fca5a5", font=("Segoe UI", 8, "bold"))
                # HP Bar
                c.create_text(sx, sy + 18, text=f"{hp:.0f} HP", fill="#f87171", font=("Segoe UI", 7))

            elif etype == "villager":
                # ─── VILLAGER ───
                locked = e.get("locked", False)
                v_avatar = self.avatars.get_avatar(f"villager_{e.get('id')}", size=18, is_villager=True)
                self.drawn_images.append(v_avatar)

                color_outline = "#64748b" if locked else self.accent_green
                c.create_rectangle(sx - 10, sy - 10, sx + 10, sy + 10, outline=color_outline, width=1.5)
                c.create_image(sx, sy, image=v_avatar)

                if locked:
                    c.create_text(sx, sy, text="❌", font=("Segoe UI", 9, "bold"), fill=self.accent_red)

        # 4. Draw Center Player (YOU) with Avatar & Direction Pointer
        # Direction Arrow
        rad = math.radians(yaw + 90) # Minecraft 0 yaw is South (+Z)
        dir_len = 30
        end_x = cx + dir_len * math.cos(rad)
        end_y = cy + dir_len * math.sin(rad)
        c.create_line(cx, cy, end_x, end_y, fill=self.accent_cyan, width=2.5, arrow="last")

        # Your Avatar (Size 28)
        my_avatar = self.avatars.get_avatar(p_name, size=28)
        self.drawn_images.append(my_avatar)

        # Cyan Glowing Border
        c.create_rectangle(cx - 16, cy - 16, cx + 16, cy + 16, outline=self.accent_cyan, width=2)
        c.create_image(cx, cy, image=my_avatar)
        c.create_text(cx, cy + 22, text=f"Bạn ({p_name})", fill=self.accent_cyan, font=("Segoe UI", 8, "bold"))

if __name__ == "__main__":
    root = tk.Tk()
    app = PaladiControlApp(root)
    root.mainloop()