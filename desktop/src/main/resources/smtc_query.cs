using System;
using System.IO;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Media.Control;

class Program {
    static void Main() {
        try { Console.WriteLine(Run()); }
        catch { Console.WriteLine("{\"state\":\"Idle\"}"); }
    }
    static string Run() {
        var mgr = WindowsRuntimeSystemExtensions.AsTask(
            GlobalSystemMediaTransportControlsSessionManager.RequestAsync()).GetAwaiter().GetResult();
        var s = mgr.GetCurrentSession();
        if (s == null) return "{\"state\":\"Idle\"}";
        var status = s.GetPlaybackInfo().PlaybackStatus.ToString();
        if (status != "Playing" && status != "Paused") return "{\"state\":\"Idle\"}";
        var isPlaying = status == "Playing";
        var m = WindowsRuntimeSystemExtensions.AsTask(s.TryGetMediaPropertiesAsync()).GetAwaiter().GetResult();
        var t = s.GetTimelineProperties();
        string albumArtPath = null;
        try {
            var thumb = m.Thumbnail;
            if (thumb != null) {
                var dest = Path.Combine(Path.GetTempPath(), "taby_albumart.jpg");
                using (var ras = WindowsRuntimeSystemExtensions.AsTask(thumb.OpenReadAsync()).GetAwaiter().GetResult())
                using (var stream = WindowsRuntimeStreamExtensions.AsStream(ras))
                using (var fs = File.OpenWrite(dest)) { stream.CopyTo(fs); }
                albumArtPath = dest;
            }
        } catch {}
        var art = albumArtPath != null ? "\"" + albumArtPath.Replace("\\", "\\\\") + "\"" : "null";
        return "{\"state\":\"Playing\",\"isPlaying\":" + (isPlaying ? "true" : "false") +
               ",\"title\":\"" + E(m.Title) + "\",\"artist\":\"" + E(m.Artist) +
               "\",\"appId\":\"" + E(s.SourceAppUserModelId) + "\",\"positionMs\":" +
               (long)t.Position.TotalMilliseconds + ",\"durationMs\":" + (long)t.EndTime.TotalMilliseconds +
               ",\"albumArtPath\":" + art + "}";
    }
    static string E(string s) {
        if (s == null) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"");
    }
}
