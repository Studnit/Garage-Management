package com.citycarservices.garage.web;

import com.citycarservices.garage.domain.ReminderStatus;
import com.citycarservices.garage.service.ReminderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reminders")
public class AdminReminderController extends BaseController {

    private final ReminderService reminders;

    public AdminReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status, Model model) {
        ReminderStatus st = parseEnum(ReminderStatus.class, status);
        model.addAttribute("reminders", reminders.list(st));
        model.addAttribute("status", st);
        model.addAttribute("statuses", ReminderStatus.values());
        return "admin/reminders";
    }

    @PostMapping("/run")
    public String runNow(RedirectAttributes ra) {
        return run(ra, null, "/admin/reminders", () -> {
            int[] r = reminders.scanAndSend();
            ra.addFlashAttribute("success", r[0] + " new reminder(s) created, " + r[1] + " delivered.");
        });
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, null, "/admin/reminders", () -> {
            boolean ok = reminders.sendOne(id);
            if (ok) {
                ra.addFlashAttribute("success", "Reminder delivered.");
            } else {
                ra.addFlashAttribute("info", "Could not be delivered automatically (no channel configured or it failed). "
                        + "Use the WhatsApp button to send it manually.");
            }
        });
    }

    @PostMapping("/{id}/sent")
    public String sent(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "Marked as sent.", "/admin/reminders", () -> reminders.markSentManually(id));
    }

    @PostMapping("/{id}/dismiss")
    public String dismiss(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "Reminder dismissed.", "/admin/reminders", () -> reminders.dismiss(id));
    }
}
