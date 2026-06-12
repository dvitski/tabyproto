import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

/**
 * Diagnostic for the unplug-while-session-open liveness bug.
 *
 * Phase A: waits for a non-Bluetooth COM port (plug the Taby in).
 * Phase B: opens the port and holds the handle (like UsbTabySession does),
 *          then asks you to UNPLUG. Logs once per second:
 *          - whether the port still appears in SerialPort.getCommPorts()
 *          - isOpen() / bytesAvailable() on the held handle
 *          - whether a 1-byte write succeeds or throws
 *          - whether LISTENING_EVENT_PORT_DISCONNECTED fires
 * Phase C: closes the handle, keeps logging enumeration (port should
 *          disappear now if the open handle was keeping it alive).
 */
public class UsbDiag {

    static String list() {
        StringBuilder sb = new StringBuilder();
        for (SerialPort p : SerialPort.getCommPorts()) {
            if (p.getDescriptivePortName().toLowerCase().contains("bluetooth")) continue;
            sb.append(p.getSystemPortName()).append('(').append(p.getDescriptivePortName()).append(") ");
        }
        return sb.length() == 0 ? "<none>" : sb.toString().trim();
    }

    static String ts() {
        return java.time.LocalTime.now().toString().substring(0, 12);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("jSerialComm version: " + SerialPort.getVersion());
        System.out.println("=== PHASE A: waiting for a non-Bluetooth COM port (plug the Taby in) ===");

        SerialPort target = null;
        for (int i = 0; i < 60 && target == null; i++) {
            for (SerialPort p : SerialPort.getCommPorts()) {
                if (!p.getDescriptivePortName().toLowerCase().contains("bluetooth")) {
                    target = p;
                    break;
                }
            }
            if (target == null) {
                System.out.println(ts() + " no candidate yet; enum: " + list());
                Thread.sleep(1000);
            }
        }
        if (target == null) {
            System.out.println("NO PORT FOUND after 60s - aborting");
            return;
        }
        System.out.println(ts() + " TARGET: " + target.getSystemPortName()
                + " (" + target.getDescriptivePortName() + ")");

        target.setBaudRate(115200);
        if (!target.openPort()) {
            System.out.println("OPEN FAILED (lastErrorCode=" + target.getLastErrorCode() + ")");
            return;
        }
        target.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        target.clearRTS();
        Thread.sleep(50);
        target.clearDTR();

        target.addDataListener(new SerialPortDataListener() {
            @Override public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
            }
            @Override public void serialEvent(SerialPortEvent e) {
                System.out.println(ts() + " *** EVENT: LISTENING_EVENT_PORT_DISCONNECTED fired ***");
            }
        });

        System.out.println("=== PHASE B: handle held open. >>> UNPLUG THE TABY NOW <<< (45s window) ===");
        for (int i = 0; i < 45; i++) {
            String wr;
            try {
                target.getOutputStream().write('\n');
                wr = "write=OK";
            } catch (Exception e) {
                wr = "write THREW " + e.getClass().getSimpleName();
            }
            String name = target.getSystemPortName();
            boolean enumerated = false;
            for (SerialPort p : SerialPort.getCommPorts()) {
                if (p.getSystemPortName().equals(name)) { enumerated = true; break; }
            }
            System.out.println(ts() + " t=" + i + "s enumerated=" + enumerated
                    + " isOpen=" + target.isOpen()
                    + " avail=" + target.bytesAvailable()
                    + " " + wr
                    + " | enum: " + list());
            Thread.sleep(1000);
        }

        System.out.println("=== PHASE C: closing the handle now (keep the Taby UNPLUGGED) ===");
        target.closePort();
        for (int i = 0; i < 8; i++) {
            System.out.println(ts() + " post-close t=" + i + "s | enum: " + list());
            Thread.sleep(1000);
        }
        System.out.println("DONE - you can replug the Taby now");
    }
}
