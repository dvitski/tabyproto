using System;
using System.Windows.Forms;
using Microsoft.Win32;

// Persistent helper: prints LOCK / UNLOCK to stdout on Windows session lock/unlock.
// SystemEvents.SessionSwitch requires a UI-thread message loop, provided by Application.Run().
class SessionMonitor
{
    [STAThread]
    static void Main()
    {
        SystemEvents.SessionSwitch += OnSessionSwitch;
        Application.Run();
    }

    static void OnSessionSwitch(object sender, SessionSwitchEventArgs e)
    {
        if (e.Reason == SessionSwitchReason.SessionLock)
        {
            Console.Out.WriteLine("LOCK");
            Console.Out.Flush();
        }
        else if (e.Reason == SessionSwitchReason.SessionUnlock)
        {
            Console.Out.WriteLine("UNLOCK");
            Console.Out.Flush();
        }
    }
}
