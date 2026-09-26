package com.citycarservices.garage.config;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Template formatting helpers, used as ${@fmt.money(x)}. Indian digit grouping. */
@Component("fmt")
public class Fmt {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter INPUT_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public String money(BigDecimal v) {
        return "₹" + plain(v);
    }

    /** Indian grouping with two decimals, e.g. 1,23,456.50 */
    public String plain(BigDecimal v) {
        BigDecimal r = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
        String s = r.abs().toPlainString();
        int dot = s.indexOf('.');
        String ip = dot < 0 ? s : s.substring(0, dot);
        String fp = dot < 0 ? ".00" : s.substring(dot);
        String grouped;
        if (ip.length() <= 3) {
            grouped = ip;
        } else {
            String last3 = ip.substring(ip.length() - 3);
            String rest = ip.substring(0, ip.length() - 3);
            StringBuilder sb = new StringBuilder();
            while (rest.length() > 2) {
                sb.insert(0, "," + rest.substring(rest.length() - 2));
                rest = rest.substring(0, rest.length() - 2);
            }
            sb.insert(0, rest);
            grouped = sb + "," + last3;
        }
        return (r.signum() < 0 ? "-" : "") + grouped + fp;
    }

    /** Quantity without needless trailing zeros. */
    public String qty(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString();
    }

    public String date(LocalDate d) {
        return d == null ? "-" : D.format(d);
    }

    public String dateTime(LocalDateTime d) {
        return d == null ? "-" : DT.format(d);
    }

    public String time(LocalDateTime d) {
        return d == null ? "-" : T.format(d);
    }

    public String inputDateTime(LocalDateTime d) {
        return d == null ? "" : INPUT_DT.format(d);
    }

    /** WhatsApp click-to-chat link with a prefilled message (10-digit Indian mobile). */
    public String waLink(String mobile, String message) {
        String enc = java.net.URLEncoder.encode(message == null ? "" : message, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "https://wa.me/91" + (mobile == null ? "" : mobile.replaceAll("[^0-9]", "")) + "?text=" + enc;
    }

    public String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] p = name.trim().split("\\s+");
        String s = p[0].substring(0, 1);
        if (p.length > 1) {
            s += p[p.length - 1].substring(0, 1);
        }
        return s.toUpperCase(Locale.ROOT);
    }

    public String stars(double rating) {
        long r = Math.round(rating);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append(i <= r ? '★' : '☆');
        }
        return sb.toString();
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
        "Ninety"};

    private String below1000(int n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 100) {
            sb.append(ONES[n / 100]).append(" Hundred");
            n %= 100;
            if (n > 0) {
                sb.append(' ');
            }
        }
        if (n >= 20) {
            sb.append(TENS[n / 10]);
            if (n % 10 > 0) {
                sb.append(' ').append(ONES[n % 10]);
            }
        } else if (n > 0) {
            sb.append(ONES[n]);
        }
        return sb.toString();
    }

    /** Amount in words, Indian system (Lakh / Crore). */
    public String words(BigDecimal amount) {
        BigDecimal a = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP).abs();
        long rupees = a.longValue();
        int paise = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder sb = new StringBuilder();
        if (rupees == 0) {
            sb.append("Zero");
        } else {
            long crore = rupees / 10000000L;
            long lakh = (rupees / 100000L) % 100;
            long thousand = (rupees / 1000L) % 100;
            long rest = rupees % 1000L;
            if (crore > 0) {
                sb.append(words(BigDecimal.valueOf(crore)).replace(" Rupees Only", "")).append(" Crore ");
            }
            if (lakh > 0) {
                sb.append(below1000((int) lakh)).append(" Lakh ");
            }
            if (thousand > 0) {
                sb.append(below1000((int) thousand)).append(" Thousand ");
            }
            if (rest > 0) {
                sb.append(below1000((int) rest));
            }
        }
        String out = sb.toString().trim() + " Rupees";
        if (paise > 0) {
            out += " and " + below1000(paise) + " Paise";
        }
        return out + " Only";
    }
}
