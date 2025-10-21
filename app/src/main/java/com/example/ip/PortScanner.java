package com.example.ip;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class PortScanner {

    private static final String TAG = "PortScanner";

    public static Map<String, String> scanIpSync(String ip,
                                                 boolean checkPing,
                                                 boolean checkHttp,
                                                 boolean checkSsh,
                                                 boolean checkModbus) {
        Map<String, String> result = new HashMap<>();

        if (checkPing) result.putAll(scanPing(ip));
        else result.put("PING", "⏸");

        if (checkHttp) result.putAll(scanHttp(ip));
        else result.put("HTTP", "⏸");

        if (checkSsh) result.putAll(scanSsh(ip));
        else result.put("SSH", "⏸");

        if (checkModbus) result.putAll(scanModbus(ip));
        else result.put("Modbus", "⏸");

        return result;
    }

    public static Map<String, String> scanPing(String ip) {
        Map<String, String> result = new HashMap<>();
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 " + ip);
            int exit = process.waitFor();
            if (exit == 0) {
                result.put("PING", "✅");
                return result;
            }
        } catch (Exception e) {
            Log.w(TAG, "ICMP ping error: " + e.getMessage());
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, 80), 3000);
            result.put("PING", "✅");
        } catch (SocketTimeoutException e) {
            result.put("PING", "❌ (timeout)");
        } catch (ConnectException e) {
            result.put("PING", "❌ (refused)");
        } catch (NoRouteToHostException e) {
            result.put("PING", "❌ (no route)");
        } catch (Exception e) {
            result.put("PING", "❌ (" + e.getClass().getSimpleName() + ")");
        }
        return result;
    }

    public static Map<String, String> scanHttp(String ip) {
        Map<String, String> result = new HashMap<>();
        int[] ports = {80, 443, 8080};
        boolean ok = false;
        String error = "";

        for (int port : ports) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    URL url = new URL("http://" + ip + ":" + port + "/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    if (code >= 200 && code < 500) {
                        ok = true;
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    error = "timeout";
                } catch (ConnectException e) {
                    error = "refused";
                } catch (NoRouteToHostException e) {
                    error = "no route";
                } catch (Exception e) {
                    error = e.getClass().getSimpleName();
                }
                if (ok) break;
            }
            if (ok) break;
        }

        result.put("HTTP", ok ? "✅" : "❌ (" + error + ")");
        return result;
    }

    public static Map<String, String> scanSsh(String ip) {
        Map<String, String> result = new HashMap<>();
        int port = 22;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 3000);
                socket.setSoTimeout(2000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String banner = reader.readLine();

                if (banner != null && banner.startsWith("SSH-")) {
                    result.put("SSH", "✅");
                    return result;
                } else {
                    result.put("SSH", "⚠️ (no banner)");
                    return result;
                }

            } catch (SocketTimeoutException e) {
                if (attempt == 2) result.put("SSH", "❌ (timeout)");
            } catch (ConnectException e) {
                result.put("SSH", "❌ (refused)");
                break;
            } catch (NoRouteToHostException e) {
                result.put("SSH", "❌ (no route)");
                break;
            } catch (Exception e) {
                result.put("SSH", "⚠️ (" + e.getClass().getSimpleName() + ")");
                Log.e(TAG, "SSH error: " + e.getMessage());
                break;
            }
        }
        return result;
    }

    public static Map<String, String> scanModbus(String ip) {
        Map<String, String> result = new HashMap<>();
        int port = 502;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 3000);
                socket.setSoTimeout(2500);

                byte[] req = new byte[]{
                        0x00, 0x01, 0x00, 0x00, 0x00, 0x06,
                        0x01, 0x11, 0x00, 0x00, 0x00, 0x00
                };
                socket.getOutputStream().write(req);
                socket.getOutputStream().flush();

                byte[] buf = new byte[64];
                int read = socket.getInputStream().read(buf);

                if (read > 0) {
                    byte funcCode = buf[7];
                    if (funcCode == 0x11) {
                        result.put("Modbus", "✅");
                        return result;
                    } else if ((funcCode & 0x80) != 0) {
                        result.put("Modbus", "⚠️ (exception)");
                    } else {
                        result.put("Modbus", "⚠️ (unknown)");
                    }
                } else {
                    result.put("Modbus", "❌ (no response)");
                }
                break;

            } catch (SocketTimeoutException e) {
                if (attempt == 2) result.put("Modbus", "❌ (timeout)");
            } catch (ConnectException e) {
                result.put("Modbus", "❌ (refused)");
                break;
            } catch (NoRouteToHostException e) {
                result.put("Modbus", "❌ (no route)");
                break;
            } catch (Exception e) {
                result.put("Modbus", "⚠️ (" + e.getClass().getSimpleName() + ")");
                Log.e(TAG, "Modbus error: " + e.getMessage());
                break;
            }
        }

        return result;
    }
}
