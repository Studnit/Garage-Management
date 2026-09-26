package com.citycarservices.garage.service;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** A generic, pre-formatted report so one template can render every report. */
@Getter
public class ReportTable {
    private final String key;
    private final String title;
    private final String description;
    private final List<String> headers = new ArrayList<>();
    private final List<Boolean> numeric = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private final List<String> totals = new ArrayList<>();
    /** Summary tiles: {label, value}. */
    private final List<String[]> summary = new ArrayList<>();

    public ReportTable(String key, String title, String description) {
        this.key = key;
        this.title = title;
        this.description = description;
    }

    public ReportTable col(String header, boolean isNumeric) {
        headers.add(header);
        numeric.add(isNumeric);
        return this;
    }

    public ReportTable row(String... cells) {
        List<String> l = new ArrayList<>();
        for (String c : cells) {
            l.add(c == null ? "" : c);
        }
        rows.add(l);
        return this;
    }

    public ReportTable totals(String... cells) {
        totals.clear();
        for (String c : cells) {
            totals.add(c == null ? "" : c);
        }
        return this;
    }

    public ReportTable tile(String label, String value) {
        summary.add(new String[] {label, value});
        return this;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿');
        appendLine(sb, headers);
        for (List<String> r : rows) {
            appendLine(sb, r);
        }
        if (!totals.isEmpty()) {
            appendLine(sb, totals);
        }
        return sb.toString();
    }

    private void appendLine(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String c = cells.get(i) == null ? "" : cells.get(i);
            // neutralise spreadsheet formula injection
            if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0 && !c.matches("^-?[0-9.,₹ ]+$")) {
                c = "'" + c;
            }
            sb.append('"').append(c.replace("\"", "\"\"")).append('"');
        }
        sb.append("\r\n");
    }
}
