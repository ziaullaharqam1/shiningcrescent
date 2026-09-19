package com.risingcrescent.util;

public final class PhoneNumbers {
    private PhoneNumbers() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Enter a mobile number");
        }
        String d = raw.trim().replaceAll("[\\s\\-()]", "");
        if (d.startsWith("00")) {
            d = "+" + d.substring(2);
        }
        if (d.matches("05\\d{8}")) {
            d = "+971" + d.substring(1);
        } else if (d.matches("5\\d{8}")) {
            d = "+971" + d;
        } else if (d.matches("971\\d{8,9}")) {
            d = "+" + d;
        }
        if (!d.startsWith("+")) {
            throw new IllegalArgumentException("Use an international number, e.g. +9715xxxxxxx");
        }
        if (!d.matches("\\+[1-9]\\d{7,14}")) {
            throw new IllegalArgumentException("That mobile number does not look valid");
        }
        return d;
    }
}
