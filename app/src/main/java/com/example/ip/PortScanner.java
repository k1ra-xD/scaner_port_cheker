package com.example.ip;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class PortScanner {

    public static Map<String, String> scanIpSync(String ip) {
        Map<String, String> res = new HashMap<>();
        res.put("PING", "✅"); // пока фейковый ping
        res.put("HTTP", checkPort(ip, 80));
        res.put("SSH", checkPort(ip, 22));
        res.put("Modbus", checkPort(ip, 502));
        return res;
    }

    private static String checkPort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 2000);
            return "✅";
        } catch (Exception e) {
            return "❌";
        }
    }
}
