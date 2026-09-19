package com.risingcrescent.service;

public final class GeoUtil {
    private GeoUtil() {}

    public static double[] pointForAddress(String address, double warehouseLat, double warehouseLng) {
        String hay = address == null ? "" : address.toLowerCase();
        if (hay.contains("marina")) return new double[]{25.0805, 55.1403};
        if (hay.contains("jbr") || hay.contains("jumeirah beach")) return new double[]{25.0782, 55.1336};
        if (hay.contains("downtown") || hay.contains("burj")) return new double[]{25.1972, 55.2744};
        if (hay.contains("business bay")) return new double[]{25.1850, 55.2650};
        if (hay.contains("deira")) return new double[]{25.2710, 55.3230};
        if (hay.contains("sharjah")) return new double[]{25.3463, 55.4209};
        if (hay.contains("abu dhabi")) return new double[]{24.4539, 54.3773};
        if (hay.contains("al barsha")) return new double[]{25.1112, 55.1980};
        if (hay.contains("silicon") || hay.contains("dubai hills")) return new double[]{25.0010, 55.2450};
        if (hay.contains("jlt")) return new double[]{25.0693, 55.1417};
        int h = hay.hashCode();
        double lat = warehouseLat + ((h % 800) / 10000.0);
        double lng = warehouseLng + (((h / 800) % 900) / 10000.0);
        return new double[]{lat, lng};
    }

    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static int etaMinutes(double km) {
        return Math.max(8, (int) Math.round(km / 0.35) + 6);
    }

    public static String formatDistance(double km) {
        if (km < 0.05) return "Arriving";
        if (km < 1) return Math.round(km * 1000) + " m";
        return String.format(java.util.Locale.US, "%.1f km", km);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
