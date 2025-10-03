package com.example.ip;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Простой синхронный сканер: PING + TCP-порты (HTTP 80, SSH 22, Modbus 502)
 */
public class PortScanner {
    private static final String TAG = "PortScanner";

    private static final int PING_TIMEOUT = 2000;   // ms
    private static final int PORT_TIMEOUT = 3000;   // ms

    private static final int PORT_SSH = 22;
    private static final int PORT_HTTP = 80;
    private static final int PORT_MODBUS = 502;

    public static Map<String, String> scanIpSync(String ip) {
        Map<String, String> result = new HashMap<>();
        // PING
        try {
            boolean reachable = InetAddress.getByName(ip).isReachable(PING_TIMEOUT);
            result.put("PING", reachable ? "✅" : "❌");
        } catch (IOException e) {
            Log.w(TAG, "ping error " + ip, e);
            result.put("PING", "❌ error");
        }

        // Ports
        result.put("HTTP", checkPort(ip, PORT_HTTP));
        result.put("SSH", checkPort(ip, PORT_SSH));
        result.put("Modbus", checkPort(ip, PORT_MODBUS));

        return result;
    }

    private static String checkPort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), PORT_TIMEOUT);
            return "✅ открыт";
        } catch (IOException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("refused")) return "❌ refused";
            if (msg.contains("timed out") || msg.contains("timeout")) return "❌ timeout";
            return "❌ closed";
        }
    }
}
