package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;

import java.util.regex.Pattern;

public final class Validators {

    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern REG = Pattern.compile("^[A-Z0-9]{5,12}$");

    private Validators() {
    }

    /** Normalises an Indian mobile number to 10 digits or throws. */
    public static String mobile(String raw) {
        String d = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (d.length() == 12 && d.startsWith("91")) {
            d = d.substring(2);
        } else if (d.length() == 11 && d.startsWith("0")) {
            d = d.substring(1);
        }
        if (!MOBILE.matcher(d).matches()) {
            throw new BusinessException("Enter a valid 10-digit Indian mobile number.");
        }
        return d;
    }

    public static String regNo(String raw) {
        String r = raw == null ? "" : raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (!REG.matcher(r).matches()) {
            throw new BusinessException("Enter a valid vehicle registration number, e.g. MH12AB1234.");
        }
        return r;
    }

    public static void password(String pw) {
        if (pw == null || pw.length() < 8 || pw.length() > 72) {
            throw new BusinessException("Password must be 8 to 72 characters long.");
        }
        boolean letter = pw.chars().anyMatch(Character::isLetter);
        boolean digit = pw.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new BusinessException("Password must contain at least one letter and one number.");
        }
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
