import os
import sys
import re
import socket
import random
import ctypes
import threading
import asyncio
import urllib.request
import urllib.parse
import json
from pathlib import Path
import tkinter as tk
from tkinter import Menu
from PIL import Image, ImageTk

# Single-instance lock via local socket binding to prevent duplicates
try:
    lock_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    lock_socket.bind(('127.0.0.1', 29384))
except socket.error:
    # Another instance is already running, exit silently
    sys.exit(0)

# Ensure edge-tts is imported
try:
    import edge_tts
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "edge-tts"])
    import edge_tts

# Paths
STATIC_DIR = Path(r"C:\Users\Admin\Documents\GitHub\meteor-client\static")
TOTEM_IMAGE_PATH = STATIC_DIR / "totem_of_undying.png"
MEMORY_DIR = Path(os.path.expanduser("~/.chibi"))
API_KEY_PATH = MEMORY_DIR / "api_key.txt"
TEMP_AUDIO_PATH = MEMORY_DIR / "desktop_voice.mp3"

# Preset quotes
PRESET_QUOTES = [
    {
        "japanese": "あきらめないで、君なら絶対にできるよ！応援しているからね。",
        "romaji": "Akiramenaide, kimi nara zettai ni dekiru yo! Ouen shite iru kara ne.",
        "viet": "Đừng bỏ cuộc, cậu chắc chắn sẽ làm được mà! Mình luôn ủng hộ cậu."
    },
    {
        "japanese": "今日も一歩ずつ進もう！努力は絶対に裏切らないよ。",
        "romaji": "Kyou mo ippo zutsu susumou! Doryoku wa zettai ni uragiranaichio.",
        "viet": "Hôm nay cũng tiến lên từng bước nhé! Nỗ lực chắc chắn không phản bội cậu đâu."
    },
    {
        "japanese": "無理しないでね。君の頑張る姿は本当に素敵だよ。",
        "romaji": "Muri shinai de ne. Kimi no ganbaru sugata wa hontou ni suteki dayo.",
        "viet": "Đừng quá sức nhé. Nhưng hình ảnh cậu nỗ lực thực sự rất tuyệt vời."
    },
    {
        "japanese": "辛いときは休んでもいいんだよ。その後で、また一緒に頑張ろう！",
        "romaji": "Tsurai toki wa yasundemo ii nda yo. Sono ato de, mata issho ni ganbarou!",
        "viet": "Lúc mệt mỏi có thể nghỉ ngơi mà. Sau đó, chúng ta lại cùng nhau cố gắng nhé!"
    },
    {
        "japanese": "自分を信じて。君には素晴らしい力があるんだから。",
        "romaji": "Jibun o shinjite. Kimi ni wa subarashii chikara ga aru nda kara.",
        "viet": "Hãy tin vào bản thân. Bởi vì cậu sở hữu sức mạnh rất tuyệt vời."
    },
    {
        "japanese": "小さな成長でも、それは素晴らしい進歩だよ。よく頑張ったね！",
        "romaji": "Chiisana seichou demo, sore wa subarashii shinpo dayo. Yoku ganbatta ne!",
        "viet": "Dù chỉ là một chút trưởng thành, đó cũng là tiến bộ tuyệt vời. Cậu làm tốt lắm!"
    },
    {
        "japanese": "未来의 của bạn sẽ tươi sáng. Hãy vững bước tiến về phía trước nhé.",
        "romaji": "Mirai no kimi ga, ima no doryoku ni kokoro kara kansha suru hi ga kanarazu kuru yo.",
        "viet": "Ngày mà bản thân ở tương lai cảm ơn nỗ lực hiện tại của cậu chắc chắn sẽ đến."
    },
    {
        "japanese": "君の夢はとても素晴らしいよ。一歩ずつ、形にしていこうね。",
        "romaji": "Kimi no yume wa totemo subarashii yo. Ippo zutsu, katachi ni shite ikou ne.",
        "viet": "Ước mơ của cậu rất tuyệt vời. Từng bước một, hãy hiện thực hóa nó nhé."
    },
    {
        "japanese": "焦らなくていいんだよ。君のペースで、ゆっくり進もう。",
        "romaji": "Aseranakute ii nda yo. Kimi no peesu de, yukkuri susumou.",
        "viet": "Không cần vội vã đâu. Cứ theo nhịp độ của cậu, thong thả tiến lên nhé."
    },
    {
        "japanese": "一生懸命に頑張っている姿は、誰よりもかっこいいよ！",
        "romaji": "Isshoukenmei ni ganbatte iru sugata wa, dare yori mo kakkoii yo!",
        "viet": "Dáng vẻ cố gắng hết sức mình của cậu trông ngầu hơn bất kỳ ai đấy!"
    }
]

# Preset unanswered/frozen fallback responses
UNANSWERED_RESPONSES = [
    {
        "japanese": "マスター、難しすぎてちびにはわかりません！",
        "romaji": "Masutaa, muzukashisugite chibi ni wa wakarimasen!",
        "viet": "Master ơi, khó quá Chibi chịu thua rồi không hiểu đâu! (>_<)"
    },
    {
        "japanese": "え？その質問はちび、まだ習っていませんよ！",
        "romaji": "E? Sono shitsumon wa chibi, mada naratte imasen yo!",
        "viet": "Hể? Câu hỏi này Chibi chưa được học đâu nha Master! (◕‿◕)"
    },
    {
        "japanese": "マスターに見とれていて、答えを忘れちゃいました。",
        "romaji": "Masutaa ni mitorete ite, kotae o wasurechaimashita.",
        "viet": "Mải ngắm Master quá nên em quên mất câu trả lời rồi. (｡♥‿♥｡)"
    },
    {
        "japanese": "ちびの頭がフリーズしちゃいました！もっと簡単な質問にして。",
        "romaji": "Chibi no atama ga furiizu shichaimashita! Motto kantan na shitsumon ni shite.",
        "viet": "Não Chibi bị đóng băng rồi! Hãy hỏi câu nào dễ hơn đi ngài. (｡>﹏<｡)"
    },
    {
        "japanese": "うーん、ちびはちびだから分かりません！えへへ。",
        "romaji": "Uun, chibi wa chibi dakara wakarimasen! Ehehe.",
        "viet": "Uầy, Chibi chỉ là Chibi thôi nên không biết đâu! Ehehe. (^▽^)"
    }
]

# Get API key
def get_api_key():
    if API_KEY_PATH.exists():
        try:
            with open(API_KEY_PATH, "r", encoding="utf-8") as f:
                return f.read().strip()
        except Exception:
            pass
    return os.environ.get("GEMINI_API_KEY")

# Fetch random quote from Gemini API
def fetch_gemini_quote(api_key):
    models = ["gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash"]
    prompt = (
        "Hãy tạo 1 câu nói động viên, truyền cảm hứng học tập, làm việc hoặc lập trình cực kỳ dễ thương bằng tiếng Nhật (1 câu ngắn gọn).\n"
        "Phản hồi DUY NHẤT dưới dạng JSON có cấu trúc sau:\n"
        "{\n"
        "  \"japanese\": \"Câu tiếng Nhật (Kanji/Kana)\",\n"
        "  \"romaji\": \"Phiên âm Romaji\",\n"
        "  \"viet\": \"Bản dịch nghĩa tiếng Việt\"\n"
        "}\n"
        "Không thêm bất kỳ chữ nào ngoài JSON."
    )
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "responseMimeType": "application/json",
            "temperature": 1.0
        }
    }
    for model in models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        try:
            req = urllib.request.Request(
                url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
                method="POST"
            )
            with urllib.request.urlopen(req, timeout=8) as response:
                res_data = json.loads(response.read().decode("utf-8"))
                text = res_data["candidates"][0]["content"]["parts"][0]["text"].strip()
                data = json.loads(text)
                if "japanese" in data and "romaji" in data and "viet" in data:
                    return data
        except Exception:
            continue
    return None

# Local Ollama fallback query endpoint
def query_local_ollama(msg):
    try:
        # Check tags and model name
        req_tags = urllib.request.Request("http://127.0.0.1:11434/api/tags", method="GET")
        with urllib.request.urlopen(req_tags, timeout=3) as res_tags:
            tags_data = json.loads(res_tags.read().decode("utf-8"))
            models = tags_data.get("models", [])
            if not models:
                return None
            model_name = models[0]["name"]
            
        prompt_system = (
            "Bạn là một trợ lý Totem đáng yêu, dễ thương đồng hành cổ vũ chủ nhân học tập và làm việc.\n"
            "Chủ nhân có thể gửi tin nhắn cho bạn bằng bất kỳ ngôn ngữ nào (tiếng Việt, tiếng Anh, v.v.).\n"
            "Nhiệm vụ của bạn là hiểu tin nhắn đó và PHẢI phản hồi hoàn toàn bằng TIẾNG NHẬT ngọt ngào, ấm áp, cổ vũ và động viên (dùng các đại từ master, chủ nhân, senpai).\n"
            "Đồng thời, bạn PHẢI trả về kết quả định dạng JSON có cấu trúc sau để hiển thị phụ đề Vietsub:\n"
            "{\n"
            "  \"japanese\": \"Câu trả lời của bạn bằng tiếng Nhật (Kanji/Kana/Hiragana)\",\n"
            "  \"romaji\": \"Phiên âm Romaji đọc tương ứng\",\n"
            "  \"viet\": \"Bản dịch nghĩa tiếng Việt tương ứng\"\n"
            "}\n"
            "Tuyệt đối không được viết bất kỳ từ nào nằm ngoài khối JSON này. Hãy chắc chắn đầu ra là JSON thuần túy, không có thẻ markdown ```json."
        )
        
        payload = {
            "model": model_name,
            "messages": [
                {"role": "system", "content": prompt_system},
                {"role": "user", "content": f"Tin nhắn từ chủ nhân: {msg}"}
            ],
            "stream": False,
            "options": {
                "temperature": 0.8
            }
        }
        
        url = "http://127.0.0.1:11434/api/chat"
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        
        with urllib.request.urlopen(req, timeout=12) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            text = res_data["message"]["content"].strip()
            
            # Remove markdown JSON wrappers if local model adds them
            if text.startswith("```"):
                text = re.sub(r"^```(?:json)?\n", "", text)
                text = re.sub(r"\n```$", "", text)
                text = text.strip()
                
            data = json.loads(text)
            if "japanese" in data and "romaji" in data and "viet" in data:
                return data
    except Exception as e:
        print(f"[Ollama Fallback Query Failed] {e}")
    return None

# Fetch chat response (Gemini -> Ollama -> Preset Cute Quotes)
def fetch_ai_chat_response(msg, api_key):
    # 1. Try Gemini first
    if api_key:
        models = ["gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash"]
        prompt_system = (
            "Bạn là một trợ lý Totem đáng yêu, dễ thương đồng hành cổ vũ chủ nhân học tập và làm việc.\n"
            "Chủ nhân có thể gửi tin nhắn cho bạn bằng bất kỳ ngôn ngữ nào (tiếng Việt, tiếng Anh, v.v.).\n"
            "Nhiệm vụ của bạn là hiểu tin nhắn đó và PHẢI phản hồi hoàn toàn bằng TIẾNG NHẬT ngọt ngào, ấm áp, cổ vũ và động viên (dùng các đại từ master, chủ nhân, senpai).\n"
            "Đồng thời, bạn PHẢI trả về kết quả định dạng JSON có cấu trúc sau để hiển thị phụ đề Vietsub:\n"
            "{\n"
            "  \"japanese\": \"Câu trả lời của bạn bằng tiếng Nhật (Kanji/Kana/Hiragana)\",\n"
            "  \"romaji\": \"Phiên âm Romaji đọc tương ứng\",\n"
            "  \"viet\": \"Bản dịch nghĩa tiếng Việt tương ứng\"\n"
            "}\n"
            "Tuyệt đối không được viết bất kỳ từ nào nằm ngoài khối JSON này."
        )
        payload = {
            "contents": [
                {"parts": [{"text": prompt_system}]},
                {"parts": [{"text": f"Tin nhắn từ chủ nhân: {msg}"}]}
            ],
            "generationConfig": {
                "responseMimeType": "application/json",
                "temperature": 0.8
            }
        }
        for model in models:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
            try:
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
                    method="POST"
                )
                with urllib.request.urlopen(req, timeout=10) as response:
                    res_data = json.loads(response.read().decode("utf-8"))
                    text = res_data["candidates"][0]["content"]["parts"][0]["text"].strip()
                    data = json.loads(text)
                    if "japanese" in data and "romaji" in data and "viet" in data:
                        return data
            except Exception as e:
                print(f"[Gemini Chat Failed] {model}: {e}")
                continue
                
    # 2. Try Ollama Fallback if Gemini fails
    print("[Ollama Fallback] Attempting local model query...")
    ollama_quote = query_local_ollama(msg)
    if ollama_quote:
        return ollama_quote
        
    # 3. Both failed, return None to trigger preset unanswered quotes
    return None

class SubtitleBubble:
    def __init__(self, parent):
        self.parent = parent
        self.window = tk.Toplevel(parent)
        self.window.overrideredirect(True)
        self.window.attributes("-topmost", True)
        
        # Transparency setting
        self.trans_color = "#123456"
        self.window.config(bg=self.trans_color)
        self.window.attributes("-transparentcolor", self.trans_color)
        
        # Subtitle dimensions
        self.width = 340
        self.height = 130
        self.canvas = tk.Canvas(self.window, width=self.width, height=self.height, bg=self.trans_color, highlightthickness=0)
        self.canvas.pack()
        
        # Hide initially
        self.window.withdraw()
        self.hide_job = None

    def draw_rounded_rect(self, x1, y1, x2, y2, radius, **kwargs):
        points = [
            x1+radius, y1,
            x2-radius, y1,
            x2, y1,
            x2, y1+radius,
            x2, y2-radius,
            x2, y2,
            x2-radius, y2,
            x1+radius, y2,
            x1, y2,
            x1, y2-radius,
            x1, y1+radius,
            x1, y1
        ]
        return self.canvas.create_polygon(points, **kwargs, smooth=True)

    def show(self, quote, totem_x, totem_y):
        if self.hide_job:
            self.window.after_cancel(self.hide_job)
            self.hide_job = None
            
        self.canvas.delete("all")
        
        # Drawing bubble card (Dark Violet Glassmorphic style)
        self.draw_rounded_rect(10, 10, self.width-10, self.height-10, 16, fill="#12061f", outline="#9d4edd", width=2)
        
        # Add labels
        jp_text = quote["japanese"]
        roma_text = quote["romaji"]
        viet_text = quote["viet"]
        
        # Truncate if text is too long for rendering
        if len(jp_text) > 30: jp_text = jp_text[:28] + "..."
        if len(roma_text) > 42: roma_text = roma_text[:39] + "..."
        if len(viet_text) > 40: viet_text = viet_text[:37] + "..."
        
        # Render text onto canvas - VIETSUB IS PLACED AT THE TOP (y=32)
        self.canvas.create_text(self.width/2, 32, text=viet_text, fill="#00ffcc", font=("Segoe UI", 10, "bold"), width=300, justify="center")
        self.canvas.create_text(self.width/2, 62, text=jp_text, fill="#f3e8ff", font=("Segoe UI", 11, "bold"), width=300, justify="center")
        self.canvas.create_text(self.width/2, 94, text=roma_text, fill="#b18cfc", font=("Segoe UI", 9, "italic"), width=300, justify="center")
        
        # Position bubble right above the totem window
        x = totem_x + 90 - (self.width / 2) # Totem width is 180
        y = totem_y - self.height + 15
        self.window.geometry(f"{self.width}x{self.height}+{int(x)}+{int(y)}")
        
        self.window.deiconify()
        # Automatically hide after 6.5 seconds
        self.hide_job = self.window.after(6500, self.hide)

    def hide(self):
        self.window.withdraw()
        self.hide_job = None

class ChatWindow:
    def __init__(self, parent, on_submit_callback):
        self.parent = parent
        self.on_submit_callback = on_submit_callback
        self.window = tk.Toplevel(parent)
        self.window.overrideredirect(True)
        self.window.attributes("-topmost", True)
        
        # Transparency / styling
        self.trans_color = "#123456"
        self.window.config(bg=self.trans_color)
        self.window.attributes("-transparentcolor", self.trans_color)
        
        self.width = 220
        self.height = 42
        
        # Frame with border matching theme
        self.frame = tk.Frame(self.window, bg="#12061f", highlightbackground="#9d4edd", highlightthickness=2, bd=0)
        self.frame.pack(fill="both", expand=True)
        
        # Text Entry
        self.entry = tk.Entry(self.frame, bg="#0d0416", fg="#f3e8ff", insertbackground="#9d4edd", font=("Segoe UI", 10), bd=0)
        self.entry.pack(fill="both", expand=True, padx=8, pady=6)
        
        # Bind Enter to submit, Escape to close
        self.entry.bind("<Return>", self.submit)
        self.entry.bind("<Escape>", self.hide)
        
        # Hide initially
        self.window.withdraw()
        self.is_visible = False

    def show(self, totem_x, totem_y):
        # Position directly below the totem character
        x = totem_x + 90 - (self.width / 2) # Totem width is 180
        y = totem_y + 180 + 5 # 180 height of totem + 5px margin
        self.window.geometry(f"{self.width}x{self.height}+{int(x)}+{int(y)}")
        self.window.deiconify()
        self.entry.focus_set()
        self.entry.delete(0, tk.END)
        self.is_visible = True

    def hide(self, event=None):
        self.window.withdraw()
        self.is_visible = False

    def submit(self, event=None):
        text = self.entry.get().strip()
        self.hide()
        if text:
            self.on_submit_callback(text)

class DesktopCompanion:
    def __init__(self):
        self.root = tk.Tk()
        self.root.overrideredirect(True)
        self.root.attributes("-topmost", True)
        
        # Transparency setting
        self.trans_color = "#123456"
        self.root.config(bg=self.trans_color)
        self.root.attributes("-transparentcolor", self.trans_color)
        
        # Dimensions
        self.width = 180
        self.height = 180
        self.root.geometry(f"{self.width}x{self.height}+400+300")
        
        self.canvas = tk.Canvas(self.root, width=self.width, height=self.height, bg=self.trans_color, highlightthickness=0)
        self.canvas.pack()
        
        # Load and resize Totem Image
        self.image = None
        self.tk_image = None
        self.load_image()
        
        # Drag coordinates
        self.drag_x = 0
        self.drag_y = 0
        
        # Event binding
        self.canvas.bind("<ButtonPress-1>", self.start_drag)
        self.canvas.bind("<B1-Motion>", self.drag)
        self.canvas.bind("<ButtonRelease-1>", self.stop_drag)
        self.canvas.bind("<Button-3>", self.show_context_menu)
        
        # Bind keyboard 'c' and 'C' to open chat entry
        self.root.bind("<Key-c>", lambda e: self.toggle_chat())
        self.root.bind("<Key-C>", lambda e: self.toggle_chat())
        
        # Bind keyboard 'x' and 'X' to close application immediately
        self.root.bind("<Key-x>", lambda e: self.exit_app())
        self.root.bind("<Key-X>", lambda e: self.exit_app())
        
        # Set cursor to hand on hover
        self.canvas.config(cursor="hand2")
        
        # Subtitle Bubble
        self.bubble = SubtitleBubble(self.root)
        
        # Chat Window
        self.chat_window = ChatWindow(self.root, self.on_chat_submit)
        
        # Audio locks & threading
        self.speaking = False
        
        # Auto-encourage setting (disabled by default)
        self.auto_timer = None
        self.auto_interval_minutes = 10
        
        # Initial greeting speech (delayed slightly to let window load)
        self.root.after(1000, self.encourage)

    def load_image(self):
        if not TOTEM_IMAGE_PATH.exists():
            print(f"Error: Totem image not found at {TOTEM_IMAGE_PATH}")
            # Render a fallback text block
            self.canvas.create_rectangle(10, 10, self.width-10, self.height-10, fill="gold", outline="orange", width=4)
            self.canvas.create_text(self.width/2, self.height/2, text="TOTEM", fill="red", font=("Segoe UI", 14, "bold"))
            return
            
        try:
            self.image = Image.open(str(TOTEM_IMAGE_PATH)).convert("RGBA")
            # Resize image cleanly to 150x150 to keep margins inside 180x180 window
            resized = self.image.resize((150, 150), Image.Resampling.LANCZOS)
            self.tk_image = ImageTk.PhotoImage(resized)
            self.canvas.create_image(self.width/2, self.height/2, image=self.tk_image)
        except Exception as e:
            print(f"Error loading Image: {e}")

    def start_drag(self, event):
        self.drag_x = event.x
        self.drag_y = event.y
        # Instantly hide subtitles and chat when dragging
        self.bubble.hide()
        self.chat_window.hide()

    def drag(self, event):
        deltax = event.x - self.drag_x
        deltay = event.y - self.drag_y
        x = self.root.winfo_x() + deltax
        y = self.root.winfo_y() + deltay
        self.root.geometry(f"+{x}+{y}")
        
        # If chat window is active, hide it when dragging
        if self.chat_window.is_visible:
            self.chat_window.hide()

    def stop_drag(self, event):
        # Click event: if the mouse barely moved, trigger encouragement!
        if abs(event.x - self.drag_x) < 3 and abs(event.y - self.drag_y) < 3:
            self.animate_bounce()
            self.encourage()

    def animate_bounce(self):
        # Micro-bounce animation for click response
        orig_x = self.root.winfo_x()
        orig_y = self.root.winfo_y()
        offsets = [-4, -8, -10, -7, -3, 0, 2, 3, 1, 0]
        
        def step(i):
            if i < len(offsets):
                self.root.geometry(f"+{orig_x}+{orig_y + offsets[i]}")
                self.root.after(25, lambda: step(i+1))
        step(0)

    def toggle_chat(self):
        if self.chat_window.is_visible:
            self.chat_window.hide()
        else:
            self.chat_window.show(self.root.winfo_x(), self.root.winfo_y())

    def encourage(self):
        if self.speaking:
            return
            
        self.speaking = True
        
        # Pick quote
        api_key = get_api_key()
        quote = None
        if api_key:
            # Query Gemini for custom motivational quote
            quote = fetch_gemini_quote(api_key)
            
        if not quote:
            # Fallback to random preset quote
            quote = random.choice(PRESET_QUOTES)
            
        # Display subtitles
        self.bubble.show(quote, self.root.winfo_x(), self.root.winfo_y())
        
        # Play voice async
        def run():
            try:
                # Ensure speech dir exists
                MEMORY_DIR.mkdir(parents=True, exist_ok=True)
                
                # Close any active voice handle to avoid write locks
                ctypes.windll.winmm.mciSendStringW('close desk_voice', None, 0, 0)
                
                # Generate Japanese voice
                async def generate():
                    communicate = edge_tts.Communicate(quote["japanese"], "ja-JP-NanamiNeural")
                    await communicate.save(str(TEMP_AUDIO_PATH))
                asyncio.run(generate())
                
                # Play audio file
                abs_path = os.path.abspath(TEMP_AUDIO_PATH)
                ctypes.windll.winmm.mciSendStringW(f'open "{abs_path}" type mpegvideo alias desk_voice', None, 0, 0)
                ctypes.windll.winmm.mciSendStringW('play desk_voice wait', None, 0, 0)
                ctypes.windll.winmm.mciSendStringW('close desk_voice', None, 0, 0)
            except Exception as e:
                print(f"Error in speech playback: {e}")
            finally:
                self.speaking = False
                
        threading.Thread(target=run, daemon=True).start()

    def on_chat_submit(self, text):
        if self.speaking:
            return
            
        self.speaking = True
        self.animate_bounce()
        
        # Pre-indicate thinking status on subtitle bubble
        thinking_quote = {
            "japanese": "考え中...",
            "romaji": "Kangaechuu...",
            "viet": "Đang suy nghĩ..."
        }
        self.bubble.show(thinking_quote, self.root.winfo_x(), self.root.winfo_y())
        
        def run():
            try:
                api_key = get_api_key()
                # Query with cascading fallbacks (Gemini -> Ollama -> Preset Cute Quotes)
                quote = fetch_ai_chat_response(text, api_key)
                
                if not quote:
                    # Fallback to local cute unanswered/lagging responses if both APIs fail
                    quote = random.choice(UNANSWERED_RESPONSES)
                
                # Update subtitle bubble with the response
                self.root.after(0, lambda: self.bubble.show(quote, self.root.winfo_x(), self.root.winfo_y()))
                
                # Ensure speech dir exists
                MEMORY_DIR.mkdir(parents=True, exist_ok=True)
                
                # Close any active voice handle
                ctypes.windll.winmm.mciSendStringW('close desk_voice', None, 0, 0)
                
                # Generate Japanese voice
                async def generate():
                    communicate = edge_tts.Communicate(quote["japanese"], "ja-JP-NanamiNeural")
                    await communicate.save(str(TEMP_AUDIO_PATH))
                asyncio.run(generate())
                
                # Play audio file
                abs_path = os.path.abspath(TEMP_AUDIO_PATH)
                ctypes.windll.winmm.mciSendStringW(f'open "{abs_path}" type mpegvideo alias desk_voice', None, 0, 0)
                ctypes.windll.winmm.mciSendStringW('play desk_voice wait', None, 0, 0)
                ctypes.windll.winmm.mciSendStringW('close desk_voice', None, 0, 0)
            except Exception as e:
                print(f"Error in chat processing: {e}")
            finally:
                self.speaking = False
                
        threading.Thread(target=run, daemon=True).start()

    def show_context_menu(self, event):
        menu = Menu(self.root, tearoff=0, bg="#12061f", fg="#f3e8ff", activebackground="#7b2cbf", activeforeground="white")
        menu.add_command(label="💬 Trò chuyện (C)", command=self.toggle_chat)
        menu.add_command(label="🌸 Động viên tôi (Click)", command=lambda: [self.animate_bounce(), self.encourage()])
        
        # Interval sub-menu
        auto_menu = Menu(menu, tearoff=0, bg="#12061f", fg="#f3e8ff", activebackground="#7b2cbf")
        auto_menu.add_command(label="Mỗi 5 phút", command=lambda: self.start_auto_timer(5))
        auto_menu.add_command(label="Mỗi 10 phút", command=lambda: self.start_auto_timer(10))
        auto_menu.add_command(label="Mỗi 20 phút", command=lambda: self.start_auto_timer(20))
        auto_menu.add_command(label="Tắt tự động", command=self.stop_auto_timer)
        
        status_label = f"Tự động động viên: {'BẬT (' + str(self.auto_interval_minutes) + ' phút)' if self.auto_timer else 'TẮT'}"
        menu.add_cascade(label=status_label, menu=auto_menu)
        menu.add_separator()
        menu.add_command(label="❌ Thoát ứng dụng (X)", command=self.exit_app)
        
        menu.tk_popup(event.x_root, event.y_root)

    def start_auto_timer(self, minutes):
        self.stop_auto_timer()
        self.auto_interval_minutes = minutes
        
        def run_timer():
            self.animate_bounce()
            self.encourage()
            self.auto_timer = self.root.after(self.auto_interval_minutes * 60 * 1000, run_timer)
            
        # Queue the next trigger
        self.auto_timer = self.root.after(self.auto_interval_minutes * 60 * 1000, run_timer)
        print(f"Auto-encourage enabled: Triggering every {minutes} minutes.")

    def stop_auto_timer(self):
        if self.auto_timer:
            self.root.after_cancel(self.auto_timer)
            self.auto_timer = None
        print("Auto-encourage disabled.")

    def exit_app(self):
        self.stop_auto_timer()
        # Close any playing MCI voice
        ctypes.windll.winmm.mciSendStringW('close desk_voice', None, 0, 0)
        self.bubble.window.destroy()
        self.chat_window.window.destroy()
        self.root.destroy()
        sys.exit(0)

    def start(self):
        self.root.mainloop()

if __name__ == "__main__":
    app = DesktopCompanion()
    app.start()
