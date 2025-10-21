package com.example.ip;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

public class WifiUtils {

    private static final String PREFS_NAME = "ipscanner_prefs";
    private static final String KEY_TARGET_SSID = "target_ssid";

    private static final String DEFAULT_SSID = "RUT230_BD18";

    public static boolean isConnectedToTargetWifi(Context context) {
        try {
            String targetSSID = getTargetSSID(context);

            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            WifiManager wifiManager = (WifiManager)
                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null || !wifiManager.isWifiEnabled()) return false;

            WifiInfo info = wifiManager.getConnectionInfo();
            if (info == null) return false;

            String ssid = info.getSSID();
            if (ssid == null || ssid.equals("<unknown ssid>")) return false;
            if (ssid.startsWith("\"") && ssid.endsWith("\""))
                ssid = ssid.substring(1, ssid.length() - 1);

            boolean isWifi = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    isWifi = (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                }
            } else {
                isWifi = info.getNetworkId() != -1;
            }

            boolean ok = isWifi && ssid.equals(targetSSID);
            Log.d("WifiUtils", "📡 Проверка Wi-Fi: текущее=\"" + ssid + "\", нужно=\"" + targetSSID + "\" → " + ok);
            return ok;

        } catch (Exception e) {
            Log.e("WifiUtils", "Ошибка проверки Wi-Fi: " + e.getMessage(), e);
            return false;
        }
    }

    public static String getCurrentSSID(Context context) {
        try {
            WifiManager wifiManager = (WifiManager)
                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return "—";

            WifiInfo info = wifiManager.getConnectionInfo();
            if (info == null || info.getSSID() == null) return "—";

            String ssid = info.getSSID();
            if (ssid.startsWith("\"") && ssid.endsWith("\""))
                ssid = ssid.substring(1, ssid.length() - 1);

            return ssid;
        } catch (Exception e) {
            Log.e("WifiUtils", "Ошибка получения SSID", e);
            return "—";
        }
    }

    public static void setTargetSSID(Context context, String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TARGET_SSID, ssid.trim())
                .apply();
        Log.i("WifiUtils", "✅ Новая целевая сеть сохранена: " + ssid);
    }

    public static String getTargetSSID(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TARGET_SSID, DEFAULT_SSID);
    }
}
