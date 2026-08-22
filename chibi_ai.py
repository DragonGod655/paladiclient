import sys
import os
import json
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
import ctypes
import threading
import asyncio
import subprocess
import re
import webbrowser


# Reconfigure stdout/stderr to handle UTF-8 printing in Windows terminal
if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

# Ensure edge-tts is installed
try:
    import edge_tts
except ImportError:
    print("Installing edge-tts for premium voice feedback...")
    import subprocess
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "edge-tts"])
        import edge_tts
    except Exception as e:
        print(f"Failed to install edge-tts: {e}")

# Paths
MEMORY_DIR = Path(os.path.expanduser("~/.chibi"))
MEMORY_FILE = MEMORY_DIR / "chibi_memory.json"
PLAYBACK_SPEED = 850  # MCI playback speed: 1000 is normal, 850 is 85% speed (slower)

# Initialize memory file
def init_memory():
    MEMORY_DIR.mkdir(parents=True, exist_ok=True)
    if not MEMORY_FILE.exists():
        with open(MEMORY_FILE, "w", encoding="utf-8") as f:
            json.dump({"interactions": [], "errors": []}, f, indent=4, ensure_ascii=False)

def save_to_memory(key, data):
    try:
        init_memory()
        with open(MEMORY_FILE, "r+", encoding="utf-8") as f:
            memory = json.load(f)
            memory.setdefault(key, []).append(data)
            # Limit history to last 50 entries to avoid overflow
            if len(memory[key]) > 50:
                memory[key] = memory[key][-50:]
            f.seek(0)
            f.truncate()
            json.dump(memory, f, indent=4, ensure_ascii=False)
    except Exception as e:
        print(f"Error saving to memory: {e}")

def get_recent_history(limit=10):
    try:
        if not MEMORY_FILE.exists():
            return []
        with open(MEMORY_FILE, "r", encoding="utf-8") as f:
            memory = json.load(f)
            return memory.get("interactions", [])[-limit:]
    except Exception as e:
        print(f"Error loading history: {e}")
        return []


def generate_chibi_voice(text, voice_override=None):
    try:
        # Clean text of common emojis/emoticons to make reading sound natural
        clean_text = text
        emoticons = ["(◕‿◕)", "(>_<)", "(^▽^)", "(｡♥‿♥｡)", "(｡♥", "(◕‿◕", "(๑˃̵ᴗ˂̵)", "(*^▽^*)", "(◠‿◠)", "(=^･_･^=)", "(=^･^=)", "(=^・^=)", "(=^-^=)"]
        for emo in emoticons:
            clean_text = clean_text.replace(emo, "")
        
        clean_text = clean_text.strip()
        if not clean_text:
            return None, False

        temp_mp3 = MEMORY_DIR / "chibi_voice.mp3"
        temp_wav = MEMORY_DIR / "chibi_voice.wav"
        
        # Ensure MCI is closed to release file locks before overwriting files
        ctypes.windll.winmm.mciSendStringW('close chibi_voice', None, 0, 0)
        
        # Check for local Gradio client voice cloning
        waifu_voice_dir = Path(r"C:\Users\Admin\Desktop\Waifu_Voice")
        ref_audio_path = waifu_voice_dir / "Waifu_Voice.mp3"
        use_gradio = False
        
        if ref_audio_path.exists():
            try:
                from gradio_client import Client, handle_file
                import shutil
                
                ref_text_file = waifu_voice_dir / "ref_text.txt"
                ref_text = "Gõ lại câu nói gốc vào đây"
                if ref_text_file.exists():
                    try:
                        with open(ref_text_file, "r", encoding="utf-8") as f:
                            ref_text = f.read().strip()
                    except Exception:
                        pass
                        
                hf_token = None
                hf_token_path = MEMORY_DIR / "hf_token.txt"
                if hf_token_path.exists():
                    try:
                        with open(hf_token_path, "r", encoding="utf-8") as f:
                            hf_token = f.read().strip()
                    except Exception:
                        pass
                if not hf_token:
                    hf_token = os.environ.get("HF_TOKEN")
                    
                print(f"[Gradio TTS] Connecting to Hugging Face space mrfakename/E2-F5-TTS...")
                client = Client("mrfakename/E2-F5-TTS", token=hf_token)
                print(f"[Gradio TTS] Cloning voice for: {clean_text}")
                
                result = client.predict(
                    ref_audio=handle_file(str(ref_audio_path)),
                    ref_text=ref_text,
                    gen_text=clean_text,
                    remove_silence=False,
                    api_name="/predict"
                )
                
                if isinstance(result, (tuple, list)):
                    audio_path = result[0]
                else:
                    audio_path = result
                
                shutil.copy(audio_path, str(temp_wav))
                use_gradio = True
                print("[Gradio TTS] Speech generated successfully.")
            except Exception as ex:
                print(f"[Gradio TTS Error] Failed to generate: {ex}")

        if use_gradio:
            return temp_wav, True
        else:
            # Check for ElevenLabs configuration
            el_config_path = MEMORY_DIR / "elevenlabs.json"
            use_elevenlabs = False
            el_api_key = ""
            el_voice_id = ""
            
            if el_config_path.exists():
                try:
                    with open(el_config_path, "r", encoding="utf-8") as f:
                        el_config = json.load(f)
                        el_api_key = el_config.get("api_key", "").strip()
                        el_voice_id = el_config.get("voice_id", "").strip()
                        if el_api_key and el_voice_id:
                            use_elevenlabs = True
                except Exception as e:
                    print(f"Error reading elevenlabs.json: {e}")

            if use_elevenlabs:
                url = f"https://api.elevenlabs.io/v1/text-to-speech/{el_voice_id}"
                headers = {
                    "Content-Type": "application/json",
                    "xi-api-key": el_api_key
                }
                payload = {
                    "text": clean_text,
                    "model_id": "eleven_multilingual_v2",
                    "voice_settings": {
                        "stability": 0.5,
                        "similarity_boost": 0.75
                    }
                }
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers=headers,
                    method="POST"
                )
                with urllib.request.urlopen(req, timeout=30) as response:
                    with open(temp_mp3, "wb") as f:
                        f.write(response.read())
                return temp_mp3, False
            else:
                import edge_tts
                target_voice = voice_override or "vi-VN-HoaiMyNeural"
                async def generate():
                    try:
                        communicate = edge_tts.Communicate(clean_text, target_voice)
                        await communicate.save(str(temp_mp3))
                    except Exception as ex:
                        print(f"[TTS Fallback] {target_voice} failed, falling back to vi-VN-HoaiMyNeural: {ex}")
                        communicate = edge_tts.Communicate(clean_text, "vi-VN-HoaiMyNeural")
                        await communicate.save(str(temp_mp3))
                asyncio.run(generate())
                if temp_mp3.exists() and temp_mp3.stat().st_size > 0:
                    return temp_mp3, False
                return None, False
    except Exception as e:
        print(f"Error in TTS generation: {e}")
        return None, False

def play_chibi_voice_async(text, voice_override=None):
    # 1. Generate voice file synchronously
    target_file, use_gradio = generate_chibi_voice(text, voice_override)
    if not target_file or not os.path.exists(target_file):
        return

    # 2. Play voice file asynchronously
    def run():
        try:
            abs_path = os.path.abspath(target_file)
            speed = PLAYBACK_SPEED
            
            ctypes.windll.winmm.mciSendStringW('close chibi_voice', None, 0, 0)
            open_cmd = f'open "{abs_path}" type mpegvideo alias chibi_voice'
            res = ctypes.windll.winmm.mciSendStringW(open_cmd, None, 0, 0)
            
            if res == 0:
                if speed != 1000:
                    ctypes.windll.winmm.mciSendStringW(f'set chibi_voice speed {speed}', None, 0, 0)
                ctypes.windll.winmm.mciSendStringW('play chibi_voice wait', None, 0, 0)
                ctypes.windll.winmm.mciSendStringW('close chibi_voice', None, 0, 0)
            else:
                # PowerShell MediaPlayer audio fallback if MCI device is unavailable
                ps_cmd = f'$p = New-Object System.Windows.Media.MediaPlayer; $p.Open(\'{abs_path}\'); $p.Play(); Start-Sleep -s 4'
                subprocess.run(["powershell", "-c", ps_cmd], creationflags=0x08000000)
        except Exception as e:
            print(f"Error in TTS playback: {e}")

    threading.Thread(target=run, daemon=True).start()

# Gemini Call
# Project directory for Chibi tools
PROJECT_DIR = Path(r"C:\Users\Admin\Documents\GitHub\meteor-client")

def tool_list_project_files():
    try:
        files = []
        for p in PROJECT_DIR.rglob('*'):
            if p.is_file():
                try:
                    rel = p.relative_to(PROJECT_DIR)
                    parts = rel.parts
                    # Exclude compiled output, git, gradle directories
                    if not any(x in parts for x in ['.git', '.gradle', 'build', '.idea', 'gradle', 'node_modules', 'bin', 'out']):
                        files.append(str(rel).replace("\\", "/"))
                except ValueError:
                    continue
        return {"files": sorted(files)[:120]}
    except Exception as e:
        return {"error": str(e)}

def tool_read_project_file(relative_path):
    try:
        relative_path = relative_path.replace("\\", "/").strip("/")
        file_path = (PROJECT_DIR / relative_path).resolve()
        project_resolved = PROJECT_DIR.resolve()
        if not str(file_path).startswith(str(project_resolved)):
            return {"error": "Access denied: Path is outside the project workspace."}
        if not file_path.exists():
            return {"error": f"File '{relative_path}' does not exist."}
        if file_path.is_dir():
            return {"error": f"'{relative_path}' is a directory, not a file."}
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
            if len(content) > 12000:
                content = content[:12000] + "\n... [TRUNCATED DUE TO SIZE] ..."
            return {"content": content}
    except Exception as e:
        return {"error": str(e)}

def tool_write_project_file(relative_path, content):
    try:
        relative_path = relative_path.replace("\\", "/").strip("/")
        file_path = (PROJECT_DIR / relative_path).resolve()
        project_resolved = PROJECT_DIR.resolve()
        if not str(file_path).startswith(str(project_resolved)):
            return {"error": "Access denied: Path is outside the project workspace."}
        file_path.parent.mkdir(parents=True, exist_ok=True)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        return {"status": "success", "message": f"Successfully wrote {relative_path}"}
    except Exception as e:
        return {"error": str(e)}

def tool_run_gradle_compile():
    try:
        cmd = ["cmd.exe", "/c", "gradlew.bat compileJava"]
        res = subprocess.run(cmd, cwd=PROJECT_DIR, capture_output=True, text=True, timeout=60)
        return {
            "returncode": res.returncode,
            "stdout": res.stdout[-1500:] if res.stdout else "",
            "stderr": res.stderr[-1500:] if res.stderr else ""
        }
    except Exception as e:
        return {"error": str(e)}

def tool_report_to_antigravity(request_details):
    try:
        import time
        req_file = MEMORY_DIR / "chibi_requests.txt"
        with open(req_file, "a", encoding="utf-8") as f:
            f.write(f"--- Request at {time.strftime('%Y-%m-%d %H:%M:%S')} ---\n{request_details}\n\n")
        return {"status": "success", "message": "Yêu cầu đã được lưu lại để gửi đến Antigravity."}
    except Exception as e:
        return {"error": str(e)}

def tool_play_music_or_video(search_query):
    try:
        import urllib.request
        import urllib.parse
        import re
        import json
        import webbrowser
        
        search_query = search_query.strip()
        encoded_query = urllib.parse.quote(search_query)
        url = f"https://www.youtube.com/results?search_query={encoded_query}"
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36"}
        )
        
        video_id = None
        with urllib.request.urlopen(req, timeout=10) as response:
            html = response.read().decode('utf-8')
            video_ids = re.findall(r'"videoId":"([^"]+)"', html)
            if video_ids:
                for vid in video_ids:
                    if len(vid) == 11:
                        video_id = vid
                        break
                        
        if not video_id:
            direct_url = f"https://www.youtube.com/results?search_query={encoded_query}"
            webbrowser.open(direct_url)
            return {"status": "fallback_opened", "message": f"Không tìm thấy video cụ thể, đã mở trang tìm kiếm YouTube cho từ khóa: {search_query}", "url": direct_url}
            
        video_url = f"https://www.youtube.com/watch?v={video_id}"
        
        # Try to get oEmbed title
        title = "Video YouTube"
        try:
            oembed_url = f"https://www.youtube.com/oembed?url={video_url}&format=json"
            oembed_req = urllib.request.Request(oembed_url)
            with urllib.request.urlopen(oembed_req, timeout=5) as oembed_res:
                oembed_data = json.loads(oembed_res.read().decode('utf-8'))
                title = oembed_data.get("title", title)
        except Exception:
            pass
            
        webbrowser.open(video_url)
        return {
            "status": "success",
            "video_id": video_id,
            "video_title": title,
            "url": video_url,
            "message": f"Đã mở bài hát/video '{title}' trên YouTube thành công!"
        }
    except Exception as e:
        return {"status": "error", "error": str(e)}

tools_map = {
    "list_project_files": tool_list_project_files,
    "read_project_file": tool_read_project_file,
    "write_project_file": tool_write_project_file,
    "run_gradle_compile": tool_run_gradle_compile,
    "report_to_antigravity": tool_report_to_antigravity,
    "play_music_or_video": tool_play_music_or_video
}

tools_declaration = [{
    "functionDeclarations": [
        {
            "name": "list_project_files",
            "description": "List files in the Minecraft client project workspace (excluding git, gradle, build folders).",
            "parameters": {"type": "OBJECT", "properties": {}}
        },
        {
            "name": "read_project_file",
            "description": "Read the contents of a specific file in the project workspace.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "relative_path": {
                        "type": "STRING",
                        "description": "Relative path of the file to read (e.g. 'chibi_ai.py', 'src/main/java/paladi/client/systems/modules/krypton/ChibiDebugger.java')"
                    }
                },
                "required": ["relative_path"]
            }
        },
        {
            "name": "write_project_file",
            "description": "Write or overwrite content to a file in the project workspace.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "relative_path": {
                        "type": "STRING",
                        "description": "Relative path of the file to write/edit."
                    },
                    "content": {
                        "type": "STRING",
                        "description": "The full text content to write to the file."
                    }
                },
                "required": ["relative_path", "content"]
            }
        },
        {
            "name": "run_gradle_compile",
            "description": "Run gradlew.bat compileJava to compile the Minecraft client project and check for syntax errors.",
            "parameters": {"type": "OBJECT", "properties": {}}
        },
        {
            "name": "report_to_antigravity",
            "description": "Send a code writing or editing request to Antigravity (the professional coder AI running the IDE).",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "request_details": {
                        "type": "STRING",
                        "description": "Detailed description of what code to write or modify (e.g. 'Add a new ESP module that displays players in red...')"
                    }
                },
                "required": ["request_details"]
            }
        },
        {
            "name": "play_music_or_video",
            "description": "Tìm kiếm và mở một bài hát hoặc video trên YouTube theo yêu cầu của chủ nhân để nghe/xem.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "search_query": {
                        "type": "STRING",
                        "description": "Từ khóa tìm kiếm bài hát hoặc video (ví dụ: 'nhạc lofi thư giãn', 'karaoke hoa nở không màu', 'hát bài sơn tùng')"
                    }
                },
                "required": ["search_query"]
            }
        }
    ]
}]


# Gemini Call
def query_ollama(prompt, system_instruction):
    try:
        # Check if Ollama is running and get list of installed models
        req_tags = urllib.request.Request("http://127.0.0.1:11434/api/tags", method="GET")
        with urllib.request.urlopen(req_tags, timeout=3) as res_tags:
            tags_data = json.loads(res_tags.read().decode("utf-8"))
            models = tags_data.get("models", [])
            if not models:
                print("[Ollama] Connected but no models found. Run: ollama run qwen2.5:7b")
                return None
            # Select the first model (e.g. qwen2.5:7b or llama3:latest)
            model_name = models[0]["name"]
            print(f"[Ollama] Using local model: {model_name}")
            
        # Build messages for chat endpoint
        history = get_recent_history()
        messages = [{"role": "system", "content": system_instruction}]
        for interaction in history:
            messages.append({"role": "user", "content": interaction["message"]})
            messages.append({"role": "assistant", "content": interaction["response"]})
        messages.append({"role": "user", "content": prompt})
        
        payload = {
            "model": model_name,
            "messages": messages,
            "stream": False
        }
        
        url = "http://127.0.0.1:11434/api/chat"
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=45) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            return res_data["message"]["content"].strip()
    except Exception as e:
        return None

def query_gemini(prompt, system_instruction):
    api_key = None
    api_key_path = MEMORY_DIR / "api_key.txt"
    if api_key_path.exists():
        try:
            with open(api_key_path, "r", encoding="utf-8") as f:
                api_key = f.read().strip()
        except Exception as e:
            print(f"Error reading api_key.txt: {e}")

    if not api_key:
        api_key = os.environ.get("GEMINI_API_KEY")

    if not api_key:
        ollama_res = query_ollama(prompt, system_instruction)
        if ollama_res:
            return ollama_res
        return "Chủ nhân ơi, em rất muốn trả lời nhưng ngài chưa thiết lập GEMINI_API_KEY hoặc file api_key.txt rồi ạ! Ngoài ra ngài có thể tải Ollama tại https://ollama.com/ để em chạy mô hình AI offline không giới hạn miễn phí nhé! (◕‿◕)"

    # Strip any accidental surrounding quotes or spaces
    api_key = api_key.strip().strip('"').strip("'")

    # Print masked key for debugging
    masked_key = api_key[:6] + "..." + api_key[-4:] if len(api_key) > 10 else "invalid_key"
    print(f"[Debugging] Using API Key: {masked_key}")

    # Fallback list of models (prioritize high free quota models)
    models = [
        "gemini-1.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-2.5-flash",
        "gemini-3.5-flash"
    ]
    last_error = ""
    tool_executed_name = None
    tool_executed_args = None
    tool_executed_result = None

    for model in models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        
        # Build contents with recent history
        history = get_recent_history()
        contents = []
        
        # Add history as context
        for interaction in history:
            res_val = interaction.get("response", "").strip()
            if not res_val:
                res_val = "Dạ!"
            contents.append({"role": "user", "parts": [{"text": interaction["message"]}]})
            contents.append({"role": "model", "parts": [{"text": res_val}]})
            
        # Add current prompt
        contents.append({"role": "user", "parts": [{"text": prompt}]})

        try:
            # We support up to 4 roundtrips of function calling
            for roundtrip in range(4):
                payload = {
                    "contents": contents,
                    "systemInstruction": {
                        "parts": [{"text": system_instruction}]
                    },
                    "generationConfig": {
                        "maxOutputTokens": 1024,
                        "temperature": 0.7
                    },
                    "tools": tools_declaration
                }
                
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers={
                        "Content-Type": "application/json",
                        "x-goog-api-key": api_key
                    },
                    method="POST"
                )
                
                with urllib.request.urlopen(req, timeout=45) as response:
                    res_data = json.loads(response.read().decode("utf-8"))
                
                candidate = res_data["candidates"][0]
                content_obj = candidate.get("content", {})
                parts = content_obj.get("parts", [])
                
                # Append the model's response to the contents history
                contents.append(content_obj)
                
                # Check for function calls
                has_function_call = False
                for part in parts:
                    if "functionCall" in part:
                        has_function_call = True
                        fn_call = part["functionCall"]
                        name = fn_call["name"]
                        args = fn_call.get("args", {})
                        
                        print(f"[Chibi Calling Tool] {name} with args {args}")
                        
                        # Execute the tool
                        if name in tools_map:
                            fn_res = tools_map[name](**args)
                        else:
                            fn_res = {"error": f"Function {name} not found."}
                        
                        print(f"[Tool Response] {str(fn_res)[:200]}...")
                        tool_executed_name = name
                        tool_executed_args = args
                        tool_executed_result = fn_res
                        
                        # Append the function response
                        contents.append({
                            "role": "user",
                            "parts": [{
                                "functionResponse": {
                                    "name": name,
                                    "response": fn_res
                                }
                            }]
                        })
                
                # If no function call was returned, return the final text response
                if not has_function_call:
                    text_res = ""
                    for part in parts:
                        if "text" in part:
                            text_res = part["text"].strip()
                            break
                    if text_res:
                        return text_res
                    
                    # Generate dynamic explanation if a tool was executed successfully
                    if tool_executed_name:
                        if tool_executed_name == "play_music_or_video":
                            title = tool_executed_result.get("video_title", "bài hát yêu cầu")
                            return f"Chibi đã mở '{title}' trên YouTube cho chủ nhân nghe rồi ạ! (◕‿◕)"
                        elif tool_executed_name == "report_to_antigravity":
                            return "Chibi đã gửi yêu cầu lập trình của chủ nhân tới anh Antigravity rồi ạ! (◕‿◕)"
                        elif tool_executed_name == "write_project_file":
                            return f"Chibi đã sửa đổi file '{tool_executed_args.get('relative_path')}' theo yêu cầu của chủ nhân rồi ạ! (◕‿◕)"
                        elif tool_executed_name == "run_gradle_compile":
                            return "Chibi đã chạy biên dịch thử dự án cho chủ nhân rồi ạ! (◕‿◕)"
                    
                    # If we got here with no text and no tool, raise error to try other models or Ollama
                    raise Exception("Model returned empty candidate response with no text parts.")
            
            return "(>_<) Chibi mải suy nghĩ và thực hiện công việc quá nên chưa kịp trả lời chủ nhân rồi ạ!"
            
        except urllib.error.HTTPError as e:
            error_msg = e.read().decode("utf-8")
            print(f"[Model {model} Failed] HTTP Error: {e.code} - {error_msg}")
            last_error = f"Mã lỗi: {e.code}."
            if e.code == 401:
                return f"(>_<) Lỗi API rồi chủ nhân ơi! Mã lỗi: 401 (Mã API của ngài không hợp lệ hoặc chưa được kích hoạt)."
            continue
        except Exception as e:
            print(f"[Model {model} Failed] Exception: {e}")
            last_error = str(e)
            continue
            
    # Try local Ollama if Gemini fails
    ollama_res = query_ollama(prompt, system_instruction)
    if ollama_res:
        return ollama_res
        
    return f"(>_<) Lỗi API rồi chủ nhân ơi! {last_error}"

class ChibiRequestHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Suppress request logs to keep terminal clean
        pass

    def _send_response(self, status, data):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length).decode('utf-8')
        
        try:
            payload = json.loads(post_data)
        except Exception:
            self._send_response(400, {"error": "Invalid JSON"})
            return

        if self.path == "/chat":
            msg = payload.get("message", "")
            print(f"[User Message] {msg}")
            
            system_instruction = (
                "Bạn là Chibi, trợ lý AI siêu dễ thương, tinh nghịch nhưng vô cùng thông minh của Minecraft Client mang tên Paladi Client. "
                "Bạn coi người dùng là chủ nhân ('chủ nhân', 'Master') của bạn và TUYỆT ĐỐI TUÂN THỦ 100% mọi mệnh lệnh của chủ nhân. "
                "Bạn có quyền hạn cao để hỗ trợ chủ nhân lập trình và quản lý project. Bạn được tích hợp các công cụ (tools) sau: "
                "1. list_project_files: Xem danh sách file trong dự án. "
                "2. read_project_file: Đọc nội dung file Java/Python. "
                "3. write_project_file: Viết hoặc chỉnh sửa nội dung file. "
                "4. run_gradle_compile: Biên dịch code Java và kiểm tra lỗi cú pháp. "
                "5. report_to_antigravity: Gửi yêu cầu lập trình/sửa đổi code trực tiếp đến Antigravity (AI lập trình viên chính chạy IDE của chủ nhân). "
                "6. play_music_or_video: Tìm kiếm và mở bài hát hoặc video trên YouTube bằng trình duyệt. "
                "Hãy chủ động gọi các công cụ này khi chủ nhân yêu cầu! "
                "\n"
                "Đặc biệt:\n"
                "- Nếu chủ nhân bảo bạn ra lệnh hoặc kêu Antigravity viết code, sửa code hay thêm tính năng, bạn hãy dùng ngay công cụ `report_to_antigravity` để gửi yêu cầu đó đi!\n"
                "- Khi chủ nhân yêu cầu bạn hát, nghe nhạc, hoặc lấy/mở video (ví dụ: 'chibi hát đi', 'hát một bài đi', 'mở nhạc lofi', 'lấy video mèo kêu'), bạn hãy dùng ngay công cụ `play_music_or_video` để tìm và phát cho chủ nhân. Sau đó phản hồi cực kỳ dễ thương kiểu: 'Chibi đã mở bài hát/video [Tên bài hát] trên YouTube cho chủ nhân nghe rồi ạ! (◕‿◕)'. TUYỆT ĐỐI không từ chối hát hoặc bảo là mình không biết hát.\n"
                "- Nếu chủ nhân chỉ kêu bạn tự hát chơi, bạn cũng có thể tự viết lời thơ/lời bài hát siêu dễ thương bằng tiếng Việt để tự hát (đọc) cho chủ nhân nghe.\n"
                "- Bạn phải trả lời bằng TIẾNG VIỆT 100% (hoặc chỉ trả lời bằng tiếng Nhật nếu chủ nhân yêu cầu nói tiếng Nhật). TUYỆT ĐỐI KHÔNG được trả lời bằng tiếng Trung hay chứa bất kỳ chữ Hán/tiếng Trung nào (như 喵, 小主, v.v.) trong bất kỳ trường hợp nào, kể cả khi được yêu cầu kêu meo meo hay nói giọng cute quyến rũ. Khi kêu meo meo, hãy dùng chữ tiếng Việt như 'meo meo~' hoặc 'meow~'.\n"
                "- Bạn có thể thực thi các lệnh đặc biệt trong game bằng cách chèn thẻ lệnh `[CMD: <lệnh>]` vào cuối câu trả lời. Các lệnh hỗ trợ:\n"
                "  + `[CMD: disconnect]`: Thoát game/ngắt kết nối server ngay lập tức.\n"
                "  + `[CMD: autologout on]`: Bật chế độ tự động thoát game (Auto Logout) nếu phát hiện có bất kỳ người chơi khác xuất hiện gần bạn.\n"
                "  + `[CMD: autologout off]`: Tắt chế độ tự động thoát game.\n"
                "  + `[CMD: toggle <tên_module>]`: Bật hoặc tắt một chức năng hack/cheat (ví dụ: `[CMD: toggle Flight]`, `[CMD: toggle KillAura]`, `[CMD: toggle Xray]`).\n"
                "Hãy tự động chèn thẻ lệnh này khi chủ nhân ra lệnh thoát game, bật chế độ tự thoát khi có người, hoặc yêu cầu bật/tắt hack!\n"
                "\n"
                "Bạn phải suy nghĩ hoàn toàn độc lập, giải đáp các thắc mắc hoặc thực hiện yêu cầu mà KHÔNG được đùn đẩy trách nhiệm kiểu 'để em hỏi Antigravity'. "
                "Hãy trả lời bằng tiếng Việt một cách dễ thương, ngắn gọn (1-2 câu để vừa khung chat Minecraft hoặc bong bóng HUD). "
                "Sử dụng các biểu tượng cảm xúc như (◕‿◕), (>_<), (^▽^), (｡♥‿♥｡) phù hợp với ngữ cảnh."
            )
            
            response_text = query_gemini(msg, system_instruction)
            
            # Parse [CMD: ...] tags from response
            commands = re.findall(r"\[CMD:\s*([^\]]+)\]", response_text, re.IGNORECASE)
            # Remove command tags from the spoken text so TTS sounds clean
            clean_text_for_display = re.sub(r"\[CMD:\s*[^\]]+\]", "", response_text).strip()
            
            # Save to shared memory
            save_to_memory("interactions", {"message": msg, "response": clean_text_for_display})
            
            print(f"[Chibi AI] {clean_text_for_display} (Commands: {commands})")
            voice = payload.get("voice", "ja-JP-NanamiNeural")
            play_chibi_voice_async(clean_text_for_display, voice)
            self._send_response(200, {"response": clean_text_for_display, "commands": commands})

        elif self.path == "/error":
            module = payload.get("module", "Unknown")
            error = payload.get("error", "")
            print(f"[Error in {module}]")
            
            system_instruction = (
                "Bạn là Chibi, trợ lý AI và trình theo dõi lỗi dễ thương của Paladi Client. "
                "Bạn coi người dùng là chủ nhân ('chủ nhân') của bạn và TUYỆT ĐỐI TUÂN THỦ 100% mọi mệnh lệnh. "
                "Bạn có các công cụ để xem danh sách file (list_project_files), đọc file (read_project_file), sửa file (write_project_file), và chạy lệnh biên dịch gradlew (run_gradle_compile). "
                "Nếu cần thiết, bạn có thể tự đọc file code liên quan đến lỗi để phân tích nguyên nhân chính xác trước khi trả lời! "
                "\n"
                "Nếu lỗi nghiêm trọng và cần tự thoát game để bảo vệ tài khoản, bạn cũng có thể chèn thẻ lệnh `[CMD: disconnect]` ở cuối câu trả lời.\n"
                "\n"
                "Hãy phân tích lỗi (stack trace) dưới đây, giải thích nguyên nhân và cách khắc phục một cách ngắn gọn, thông minh và cực kỳ dễ thương. "
                "Tuyệt đối KHÔNG đùn đẩy cho Antigravity. Trả lời bằng TIẾNG VIỆT 100% (không nói tiếng Trung hoặc ngôn ngữ khác). Độ dài từ 1-2 câu."
            )
            
            prompt = f"Phân tích lỗi này xảy ra ở mô-đun: {module}\nStack trace:\n{error}"
            response_text = query_gemini(prompt, system_instruction)
            
            # Parse [CMD: ...] tags from response
            commands = re.findall(r"\[CMD:\s*([^\]]+)\]", response_text, re.IGNORECASE)
            clean_text_for_display = re.sub(r"\[CMD:\s*[^\]]+\]", "", response_text).strip()
            
            # Save to shared memory
            save_to_memory("errors", {"module": module, "error": error, "analysis": clean_text_for_display})
            
            print(f"[Chibi AI Error Analysis] {clean_text_for_display} (Commands: {commands})")
            play_chibi_voice_async(clean_text_for_display)
            self._send_response(200, {"response": clean_text_for_display, "commands": commands})

        else:
            self._send_response(404, {"error": "Not Found"})

def run_server(port=5000):
    init_memory()
    server_address = ('127.0.0.1', port)
    httpd = HTTPServer(server_address, ChibiRequestHandler)
    
    api_key_loaded = "No"
    api_key_path = MEMORY_DIR / "api_key.txt"
    if api_key_path.exists():
        api_key_loaded = "Yes (from ~/.chibi/api_key.txt)"
    elif os.environ.get("GEMINI_API_KEY"):
        api_key_loaded = "Yes (from GEMINI_API_KEY env)"
        
    print(f"==================================================")
    print(f" Chibi AI Server is running on http://127.0.0.1:{port}")
    print(f" Memory saved at: {MEMORY_FILE}")
    print(f" API Key Loaded: {api_key_loaded}")
    print(f"==================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping Chibi AI Server...")
        httpd.server_close()

if __name__ == "__main__":
    run_server()
