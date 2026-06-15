using System;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Media.Control;

class Program {
    static void Main(string[] args) {
        if (args.Length < 2) return;
        string command = args[0];
        string appId  = args[1];
        try {
            var mgr = WindowsRuntimeSystemExtensions.AsTask(
                GlobalSystemMediaTransportControlsSessionManager.RequestAsync()).GetAwaiter().GetResult();
            foreach (var session in mgr.GetSessions()) {
                if ((session.SourceAppUserModelId ?? "") != appId) continue;
                switch (command) {
                    case "play":   WindowsRuntimeSystemExtensions.AsTask(session.TryPlayAsync()).GetAwaiter().GetResult(); break;
                    case "pause":  WindowsRuntimeSystemExtensions.AsTask(session.TryPauseAsync()).GetAwaiter().GetResult(); break;
                    case "toggle": WindowsRuntimeSystemExtensions.AsTask(session.TryTogglePlayPauseAsync()).GetAwaiter().GetResult(); break;
                    case "next":   WindowsRuntimeSystemExtensions.AsTask(session.TrySkipNextAsync()).GetAwaiter().GetResult(); break;
                    case "prev":   WindowsRuntimeSystemExtensions.AsTask(session.TrySkipPreviousAsync()).GetAwaiter().GetResult(); break;
                }
                return;
            }
        } catch {}
    }
}
