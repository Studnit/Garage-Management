package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.domain.Part;
import com.citycarservices.garage.domain.Supplier;
import com.citycarservices.garage.form.PartForm;
import com.citycarservices.garage.form.SupplierForm;
import com.citycarservices.garage.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class AdminInventoryController extends BaseController {

    private final InventoryService inventory;

    public AdminInventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/admin/inventory")
    public String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean low,
                       Model model) {
        model.addAttribute("parts", inventory.search(q, low));
        model.addAttribute("q", q);
        model.addAttribute("low", low);
        model.addAttribute("lowCount", inventory.lowStock().size());
        return "admin/inventory/list";
    }

    @GetMapping("/admin/inventory/new")
    public String newForm(Model model) {
        return form(model, new PartForm(), null);
    }

    @PostMapping("/admin/inventory/new")
    public String create(@Valid @ModelAttribute("form") PartForm form, BindingResult result, Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            return form(model, form, null);
        }
        try {
            Part p = inventory.create(form);
            ra.addFlashAttribute("success", "Part " + p.getPartNo() + " added to the catalogue.");
            return "redirect:/admin/inventory/" + p.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return form(model, form, null);
        }
    }

    @GetMapping("/admin/inventory/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("part", inventory.get(id));
        model.addAttribute("movements", inventory.movementsFor(id));
        model.addAttribute("suppliers", inventory.suppliers());
        return "admin/inventory/detail";
    }

    @GetMapping("/admin/inventory/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Part p = inventory.get(id);
        return form(model, PartForm.of(p), p);
    }

    @PostMapping("/admin/inventory/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") PartForm form, BindingResult result,
                         Model model, RedirectAttributes ra) {
        Part p = inventory.get(id);
        if (result.hasErrors()) {
            return form(model, form, p);
        }
        try {
            inventory.update(id, form);
            ra.addFlashAttribute("success", "Part updated.");
            return "redirect:/admin/inventory/" + id;
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return form(model, form, p);
        }
    }

    private String form(Model model, PartForm f, Part p) {
        model.addAttribute("form", f);
        model.addAttribute("part", p);
        model.addAttribute("isNew", p == null);
        model.addAttribute("suppliers", inventory.suppliers());
        model.addAttribute("formAction", p == null ? "/admin/inventory/new" : "/admin/inventory/" + p.getId() + "/edit");
        return "admin/inventory/form";
    }

    @PostMapping("/admin/inventory/{id}/in")
    public String stockIn(@PathVariable Long id, @RequestParam BigDecimal quantity,
                          @RequestParam(required = false) BigDecimal unitCost,
                          @RequestParam(required = false) Long supplierId,
                          @RequestParam(required = false) String reference,
                          @RequestParam(required = false) String note, RedirectAttributes ra) {
        return run(ra, "Stock added.", "/admin/inventory/" + id,
                () -> inventory.stockIn(id, quantity, unitCost, supplierId, reference, note));
    }

    @PostMapping("/admin/inventory/{id}/out")
    public String stockOut(@PathVariable Long id, @RequestParam BigDecimal quantity,
                           @RequestParam(required = false) String reference,
                           @RequestParam(required = false) String note, RedirectAttributes ra) {
        return run(ra, "Stock reduced.", "/admin/inventory/" + id,
                () -> inventory.stockOut(id, quantity, reference, note));
    }

    // ---------------- suppliers ----------------

    @GetMapping("/admin/suppliers")
    public String suppliers(Model model) {
        model.addAttribute("suppliers", inventory.suppliers());
        model.addAttribute("form", new SupplierForm());
        model.addAttribute("editing", null);
        return "admin/inventory/suppliers";
    }

    @GetMapping("/admin/suppliers/{id}/edit")
    public String editSupplier(@PathVariable Long id, Model model) {
        Supplier s = inventory.supplier(id);
        model.addAttribute("suppliers", inventory.suppliers());
        model.addAttribute("form", SupplierForm.of(s));
        model.addAttribute("editing", s);
        return "admin/inventory/suppliers";
    }

    @PostMapping("/admin/suppliers")
    public String saveSupplier(@Valid @ModelAttribute("form") SupplierForm form, BindingResult result,
                               @RequestParam(required = false) Long id, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("suppliers", inventory.suppliers());
            model.addAttribute("editing", id == null ? null : inventory.supplier(id));
            return "admin/inventory/suppliers";
        }
        try {
            inventory.saveSupplier(id, form);
            ra.addFlashAttribute("success", "Supplier saved.");
            return "redirect:/admin/suppliers";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("suppliers", inventory.suppliers());
            model.addAttribute("editing", id == null ? null : inventory.supplier(id));
            return "admin/inventory/suppliers";
        }
    }
}
