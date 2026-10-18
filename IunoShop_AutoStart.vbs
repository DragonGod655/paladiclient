Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "c:\Users\Admin\Documents\GitHub\meteor-client"
WshShell.Run """C:\Users\Admin\AppData\Local\Python\pythoncore-3.14-64\pythonw.exe"" server_guardian.py", 0, False
