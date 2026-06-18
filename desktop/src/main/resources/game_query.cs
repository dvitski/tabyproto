using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

class Program {
    [DllImport("user32.dll")]
    static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll")]
    static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    static extern IntPtr MonitorFromWindow(IntPtr hwnd, uint dwFlags);

    [DllImport("user32.dll")]
    static extern bool GetMonitorInfo(IntPtr hMonitor, ref MONITORINFO lpmi);

    const uint MONITOR_DEFAULTTONEAREST = 2;

    [StructLayout(LayoutKind.Sequential)]
    struct RECT { public int Left, Top, Right, Bottom; }

    [StructLayout(LayoutKind.Sequential)]
    struct MONITORINFO {
        public uint cbSize;
        public RECT rcMonitor;
        public RECT rcWork;
        public uint dwFlags;
    }

    static void Main() {
        try { Console.WriteLine(Run()); }
        catch { Console.WriteLine("{\"processName\":null,\"isFullscreen\":false}"); }
    }

    static string Run() {
        IntPtr hwnd = GetForegroundWindow();
        if (hwnd == IntPtr.Zero) return "{\"processName\":null,\"isFullscreen\":false}";

        uint pid;
        GetWindowThreadProcessId(hwnd, out pid);
        string processName = null;
        try { processName = Process.GetProcessById((int)pid).ProcessName; } catch {}

        bool isFullscreen = false;
        RECT windowRect;
        if (GetWindowRect(hwnd, out windowRect)) {
            IntPtr hMonitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
            MONITORINFO mi = new MONITORINFO();
            mi.cbSize = (uint)Marshal.SizeOf(typeof(MONITORINFO));
            if (GetMonitorInfo(hMonitor, ref mi)) {
                isFullscreen = windowRect.Left <= mi.rcMonitor.Left &&
                               windowRect.Top <= mi.rcMonitor.Top &&
                               windowRect.Right >= mi.rcMonitor.Right &&
                               windowRect.Bottom >= mi.rcMonitor.Bottom;
            }
        }

        string nameJson = processName != null ? "\"" + processName.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"" : "null";
        return "{\"processName\":" + nameJson + ",\"isFullscreen\":" + (isFullscreen ? "true" : "false") + "}";
    }
}
