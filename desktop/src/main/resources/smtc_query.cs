using System;
using System.IO;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Media.Control;

class Program {
    static void Main() {
        try { Console.WriteLine(Run()); }
        catch { Console.WriteLine("[]"); }
    }
    static string Run() {
        var mgr = WindowsRuntimeSystemExtensions.AsTask(
            GlobalSystemMediaTransportControlsSessionManager.RequestAsync()).GetAwaiter().GetResult();

        var parts = new System.Collections.Generic.List<string>();
        int idx = 0;
        foreach (var session in mgr.GetSessions()) {
            var status = session.GetPlaybackInfo().PlaybackStatus.ToString();
            if (status != "Playing" && status != "Paused") continue;
            bool isPlaying = status == "Playing";
            var m = WindowsRuntimeSystemExtensions.AsTask(session.TryGetMediaPropertiesAsync()).GetAwaiter().GetResult();
            var t = session.GetTimelineProperties();

            string albumArtPath = null;
            try {
                var thumb = m.Thumbnail;
                if (thumb != null) {
                    var dest = Path.Combine(Path.GetTempPath(), "taby_albumart_" + idx + ".jpg");
                    using (var ras = WindowsRuntimeSystemExtensions.AsTask(thumb.OpenReadAsync()).GetAwaiter().GetResult())
                    using (var stream = WindowsRuntimeStreamExtensions.AsStream(ras))
                    using (var fs = File.OpenWrite(dest)) { stream.CopyTo(fs); }
                    albumArtPath = dest;
                }
            } catch {}

            var art = albumArtPath != null ? "\"" + albumArtPath.Replace("\\", "\\\\") + "\"" : "null";
            parts.Add("{\"isPlaying\":" + (isPlaying ? "true" : "false") +
                ",\"title\":\"" + E(m.Title) + "\",\"artist\":\"" + E(m.Artist) +
                "\",\"appId\":\"" + E(session.SourceAppUserModelId) + "\",\"positionMs\":" +
                (long)t.Position.TotalMilliseconds + ",\"durationMs\":" + (long)t.EndTime.TotalMilliseconds +
                ",\"albumArtPath\":" + art + "}");
            idx++;
        }
        return "[" + string.Join(",", parts) + "]";
    }
    static string E(string s) {
        if (s == null) return "";
        return s.Replace("\\", "\\\\").Replace("\"", "\\\"");
    }
}
