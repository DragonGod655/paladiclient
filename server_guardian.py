# -*- coding: utf-8 -*-
"""
Paladi Mobile Web & App Always-On Guardian
Ensures mobile_server.py runs 24/7 without interruption.
If the server crashes, gets killed, or closes, this guardian revives it immediately.
"""

import sys
import os
import time
import subprocess
import urllib.request
import socket

WORKSPACE_DIR = r"c:\Users\Admin\Documents\GitHub\meteor-client"
SERVER_SCRIPT = os.path.join(WORKSPACE_DIR, "mobile_server.py")
PORT = 8080

def is_port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex(('127.0.0.1', port)) == 0

def is_server_responding():
    try:
        url = f"http://127.0.0.1:{PORT}/api/status"
        req = urllib.request.Request(url, headers={'User-Agent': 'PaladiGuardian/1.0'})
        with urllib.request.urlopen(req, timeout=2) as response:
            return response.status == 200
    except Exception:
        return False

def log(msg):
    try:
        log_file = os.path.join(WORKSPACE_DIR, "server_guardian.log")
        with open(log_file, "a", encoding="utf-8") as f:
            f.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {msg}\n")
    except Exception:
        pass

def main():
    os.chdir(WORKSPACE_DIR)
    log("Server guardian started.")
    
    # Check known python paths
    candidate_runners = [
        r"C:\Users\Admin\AppData\Local\Python\bin\pythonw.exe",
        r"C:\Users\Admin\AppData\Local\Python\bin\python.exe",
        sys.executable
    ]
    runner = sys.executable
    for c in candidate_runners:
        if os.path.exists(c):
            runner = c
            break

    log(f"Using Python runner: {runner}")
    server_process = None

    while True:
        try:
            healthy = is_server_responding()

            if not healthy:
                log("Server not responding on port 8080. Attempting revive...")
                if server_process and server_process.poll() is None:
                    try:
                        server_process.terminate()
                        server_process.wait(timeout=2)
                    except Exception:
                        pass

                if is_port_in_use(PORT):
                    try:
                        subprocess.run(
                            f'powershell -Command "Get-NetTCPConnection -LocalPort {PORT} -ErrorAction SilentlyContinue | ForEach-Object {{ Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }}"',
                            shell=True,
                            capture_output=True
                        )
                        time.sleep(1)
                    except Exception:
                        pass

                server_process = subprocess.Popen(
                    [runner, SERVER_SCRIPT],
                    cwd=WORKSPACE_DIR,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL
                )
                log(f"Launched mobile_server.py (PID {server_process.pid})")
                time.sleep(4)
            else:
                time.sleep(5)

        except Exception as e:
            log(f"Guardian loop error: {e}")
            time.sleep(5)


if __name__ == '__main__':
    main()
