param([int]$vk)
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class MediaKey {
    [DllImport("user32.dll")]
    public static extern void keybd_event(byte bVk, byte bScan, int dwFlags, IntPtr dwExtraInfo);
}
"@
[MediaKey]::keybd_event($vk, 0, 0, [IntPtr]::Zero)
[MediaKey]::keybd_event($vk, 0, 2, [IntPtr]::Zero)
