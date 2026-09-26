package com.citycarservices.garage.web;

import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.InspectionMedia;
import com.citycarservices.garage.domain.Role;
import com.citycarservices.garage.service.InspectionService;
import com.citycarservices.garage.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Serves inspection photos/videos only to staff who work on jobs and to the owning customer. */
@Controller
public class MediaController {

    private final InspectionService inspections;
    private final StorageService storage;

    public MediaController(InspectionService inspections, StorageService storage) {
        this.inspections = inspections;
        this.storage = storage;
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<Resource> media(@PathVariable Long id, @AuthenticationPrincipal GarageUserDetails me) {
        InspectionMedia m = inspections.media(id);
        if (me.getRole() == Role.CUSTOMER) {
            Long owner = m.getInspection().getJobCard().getCustomer().getUser() == null ? null
                    : m.getInspection().getJobCard().getCustomer().getUser().getId();
            if (owner == null || !owner.equals(me.getId())) {
                throw new NotFoundException("File not found");
            }
        } else if (me.getRole() == Role.INVENTORY_MANAGER) {
            throw new NotFoundException("File not found");
        }
        Resource file = storage.load(m.getStoredName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(file);
    }
}
