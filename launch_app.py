import os
import sys
import webview
from pathlib import Path

def main():
    base_dir = Path(__file__).resolve().parent
    html_path = base_dir / "web_app" / "index.html"
    
    if not html_path.exists():
        print(f"Error: Could not find HTML file at {html_path}")
        return
        
    window = webview.create_window(
        title="Paladi Auto Villager & Order Control Hub",
        url=str(html_path.resolve()),
        width=1280,
        height=850,
        resizable=True,
        min_size=(900, 600),
        text_select=True
    )
    
    webview.start(debug=False)

if __name__ == "__main__":
    main()
