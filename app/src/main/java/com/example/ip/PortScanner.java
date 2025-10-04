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
     * Значения — "ОТКРЫТ (XX ms)" или "ЗАКРЫТ" / для PING — "XX ms" или "❌".
     */
    public static Map<String, String> scanIpSync(String ip) {
        Map<String, String> res = new HashMap<>();

        try {
            // PING
            String pingResult = checkPing(ip, 1500);
            res.put("PING", pingResult);

            // Порты
            res.put("HTTP", checkPortWithTime(ip, 80, 1500));
            res.put("HTTPS", checkPortWithTime(ip, 443, 1500));
            res.put("SSH", checkPortWithTime(ip, 22, 1500));
            res.put("Modbus", checkPortWithTime(ip, 502, 1500));

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
            long start = System.nanoTime();
            boolean ok = addr.isReachable(timeoutMs);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (ok) return elapsedMs + " ms";
            else return "❌";
        } catch (Exception e) {
            return "❌";
        }
    }

    private static String checkPortWithTime(String ip, int port, int timeoutMs) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return "ОТКРЫТ (" + elapsedMs + " ms)";
        } catch (Exception e) {
            return "ЗАКРЫТ";
        }
    }
}
