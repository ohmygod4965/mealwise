<#
  GUI.ps1 —— 让 Agent 具备「看屏幕 / 点鼠标 / 打键盘」的能力（无需第三方插件）

  关键点（踩过的坑）：
  1. 必须以 DPI-aware 方式启动进程，否则截图坐标与实际点击坐标差 1/缩放倍率
     （本机显示器 125%，Apifox 窗口 DPI=120，窗口 Rect 从 455,292 变成 569,365）。
  2. 抢前台要先按一下 Alt 解锁 Windows 前台窗口锁，否则 SetForegroundWindow 会静默失败。
  3. 点完必须重新截图确认 —— 坐标是基于「窗口相对位置 + 窗口原点」算的，窗口一动就失效。

  用法：
    . .\GUI.ps1
    $w = Get-TargetWindow 'Apifox'          # 切前台并返回 {Handle, Left, Top, Width, Height}
    Save-Screenshot $w 'out.png'            # 1:1 截该窗口（含边框）
    Click-Window $w 700 398                 # 点窗口内相对坐标
    Send-Text '{"a":1}'                     # 剪贴板 + Ctrl+V（中文也可靠）
    Send-Keys '{ENTER}'                     # SendKeys 语法
#>

Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class GuiNative {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, int data, UIntPtr extra);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
[GuiNative]::SetProcessDPIAware() | Out-Null

function Get-TargetWindow {
  param([string]$Name = 'Apifox')

  $p = Get-Process -Name $Name -ErrorAction SilentlyContinue |
       Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
  if (-not $p) { throw "找不到窗口: $Name" }

  # Alt 解锁前台锁，再抢焦点
  [GuiNative]::keybd_event(0x12,0,0,[UIntPtr]::Zero)
  [GuiNative]::keybd_event(0x12,0,2,[UIntPtr]::Zero)
  Start-Sleep -Milliseconds 150
  [GuiNative]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
  Start-Sleep -Milliseconds 400

  $r = New-Object GuiNative+RECT
  [GuiNative]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
  [pscustomobject]@{
    Name   = $Name
    Handle = $p.MainWindowHandle
    Left   = $r.Left
    Top    = $r.Top
    Width  = $r.Right - $r.Left
    Height = $r.Bottom - $r.Top
  }
}

function Save-Screenshot {
  param($Window, [string]$Path)
  $bmp = New-Object System.Drawing.Bitmap $Window.Width, $Window.Height
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($Window.Left, $Window.Top, 0, 0, $bmp.Size)
  $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose()
  Write-Host "screenshot -> $Path ($($Window.Width)x$($Window.Height))"
}

function Click-Window {
  param($Window, [int]$X, [int]$Y, [switch]$Double)
  [GuiNative]::SetCursorPos(($Window.Left + $X), ($Window.Top + $Y)) | Out-Null
  Start-Sleep -Milliseconds 200
  [GuiNative]::mouse_event(0x0002,0,0,0,[UIntPtr]::Zero)
  Start-Sleep -Milliseconds 50
  [GuiNative]::mouse_event(0x0004,0,0,0,[UIntPtr]::Zero)
  if ($Double) {
    Start-Sleep -Milliseconds 80
    [GuiNative]::mouse_event(0x0002,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 50
    [GuiNative]::mouse_event(0x0004,0,0,0,[UIntPtr]::Zero)
  }
  Write-Host "click -> ($($Window.Left + $X), $($Window.Top + $Y))"
}

function Send-Text {
  param([string]$Text, [switch]$SelectAllFirst)
  Set-Clipboard -Value $Text
  Start-Sleep -Milliseconds 150
  if ($SelectAllFirst) {
    [GuiNative]::keybd_event(0x11,0,0,[UIntPtr]::Zero)
    [GuiNative]::keybd_event(0x41,0,0,[UIntPtr]::Zero)
    [GuiNative]::keybd_event(0x41,0,2,[UIntPtr]::Zero)
    [GuiNative]::keybd_event(0x11,0,2,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 200
  }
  [GuiNative]::keybd_event(0x11,0,0,[UIntPtr]::Zero)
  [GuiNative]::keybd_event(0x56,0,0,[UIntPtr]::Zero)
  [GuiNative]::keybd_event(0x56,0,2,[UIntPtr]::Zero)
  [GuiNative]::keybd_event(0x11,0,2,[UIntPtr]::Zero)
  Start-Sleep -Milliseconds 400
  Write-Host "pasted $($Text.Length) chars"
}

function Send-Keys {
  param([string]$Keys)
  [System.Windows.Forms.SendKeys]::SendWait($Keys)
  Start-Sleep -Milliseconds 300
}
