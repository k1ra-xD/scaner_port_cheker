package com.example.ip;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class PortScanner {

    /**
     * Сканирует указанный IP и возвращает Map с ключами:
     * PING, HTTP, HTTPS, SSH, Modbus
     *
     * Значения — только "✅" или "❌".
     */
    public static Map<String, String> scanIpSync(String ip) {
        Map<String, String> res = new HashMap<>();

        try {
            // PING
            res.put("PING", checkPing(ip, 1500));

            // Порты
            res.put("HTTP", checkPort(ip, 80, 1500));
            res.put("HTTPS", checkPort(ip, 443, 1500));
            res.put("SSH", checkPort(ip, 22, 1500));
            res.put("Modbus", checkPort(ip, 502, 1500));

        } catch (Exception e) {
            // Если что-то пошло не так — возвращаем ❌ для всех
            res.put("PING", "❌");
            res.put("HTTP", "❌");
            res.put("HTTPS", "❌");
            res.put("SSH", "❌");
            res.put("Modbus", "❌");
        }

        return res;
    }

    private static String checkPing(String ip, int timeoutMs) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            boolean ok = addr.isReachable(timeoutMs);
            return ok ? "✅" : "❌";
        } catch (Exception e) {
            return "❌";
        }
    }

    private static String checkPort(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return "✅";
        } catch (Exception e) {
            return "❌";
        }
    }
}
