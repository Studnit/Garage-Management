package com.citycarservices.garage.web;

import com.citycarservices.garage.service.ReportService;
import com.citycarservices.garage.service.ReportTable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class AdminReportController extends BaseController {

    private final ReportService reports;

    public AdminReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/admin/reports")
    public String index(Model model) {
        return show("revenue", null, null, null, null, model);
    }

    @GetMapping({"/admin/reports/{key}"})
    public Object showOrCsv(@PathVariable String key,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String format, Model model) {
        if ("csv".equals(format)) {
            LocalDate f = from == null ? monthStart() : from;
            LocalDate t = to == null ? LocalDate.now() : to;
            ReportTable table = reports.build(key, f, t, q);
            byte[] body = table.toCsv().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "-report.csv\"")
                    .body(body);
        }
        return show(key, from, to, q, format, model);
    }

    private String show(String key, LocalDate from, LocalDate to, String q, String format, Model model) {
        LocalDate f = from == null ? monthStart() : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        if (t.isBefore(f)) {
            LocalDate x = f;
            f = t;
            t = x;
        }
        model.addAttribute("report", reports.build(key, f, t, q));
        model.addAttribute("catalog", ReportService.CATALOG);
        model.addAttribute("key", key);
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("q", q);
        model.addAttribute("usesDates", reports.usesDates(key));
        return "admin/reports";
    }
}
