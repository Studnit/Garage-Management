package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.InspectionForm;
import com.citycarservices.garage.repo.InspectionMediaRepository;
import com.citycarservices.garage.repo.InspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

@Service
public class InspectionService {

    private static final long MAX_PHOTO = 15L * 1024 * 1024;
    private static final long MAX_VIDEO = 50L * 1024 * 1024;
    private static final int MAX_MEDIA = 40;

    private final InspectionRepository inspections;
    private final InspectionMediaRepository mediaRepo;
    private final JobService jobService;
    private final StorageService storage;
    private final AuditService audit;

    public InspectionService(InspectionRepository inspections, InspectionMediaRepository mediaRepo,
                             JobService jobService, StorageService storage, AuditService audit) {
        this.inspections = inspections;
        this.mediaRepo = mediaRepo;
        this.jobService = jobService;
        this.storage = storage;
        this.audit = audit;
    }

    /** Creates the blank checklist (all 13 areas) when the vehicle moves to Inspection. */
    @Transactional
    public Inspection getOrCreate(JobCard job) {
        Inspection i = inspections.findByJobCardId(job.getId()).orElse(null);
        if (i != null) {
            return i;
        }
        i = new Inspection();
        i.setJobCard(job);
        i.setMechanic(job.getMechanic());
        i.setOdometerReading(job.getOdometerIn());
        for (InspectionArea a : InspectionArea.values()) {
            InspectionItem it = new InspectionItem();
            it.setInspection(i);
            it.setArea(a);
            i.getItems().add(it);
        }
        return inspections.save(i);
    }

    @Transactional(readOnly = true)
    public Inspection find(Long jobId) {
        return inspections.findByJobCardId(jobId).orElse(null);
    }

    @Transactional
    public Inspection save(Long jobId, InspectionForm f, boolean submit) {
        JobCard job = jobService.get(jobId);
        jobService.assertCanWork(job);
        BookingStatus st = job.getBooking().getStatus();
        if (st.getStep() < BookingStatus.INSPECTION.getStep() || st.getStep() > BookingStatus.QUALITY_CHECK.getStep()) {
            throw new BusinessException("The inspection can be recorded only while the job is between Inspection and Quality Check.");
        }
        Inspection i = getOrCreate(job);
        if (f.getItems() != null) {
            for (InspectionForm.Item in : f.getItems()) {
                if (in.getArea() == null) {
                    continue;
                }
                for (InspectionItem it : i.getItems()) {
                    if (it.getArea() == in.getArea()) {
                        it.setCondition(in.getCondition() == null ? ItemCondition.NOT_CHECKED : in.getCondition());
                        it.setRemarks(Validators.blankToNull(in.getRemarks()));
                    }
                }
            }
        }
        i.setOdometerReading(f.getOdometerReading());
        i.setNotes(Validators.blankToNull(f.getNotes()));
        i.setDefectiveParts(Validators.blankToNull(f.getDefectiveParts()));
        i.setRecommendedRepairs(Validators.blankToNull(f.getRecommendedRepairs()));
        i.setEstimatedCost(f.getEstimatedCost() == null ? null : Money.round(f.getEstimatedCost()));
        if (i.getMechanic() == null) {
            i.setMechanic(job.getMechanic());
        }
        if (submit) {
            for (InspectionItem it : i.getItems()) {
                if (it.getCondition() == ItemCondition.NOT_CHECKED) {
                    throw new BusinessException("Please mark every area before submitting (missing: "
                            + it.getArea().getLabel() + ").");
                }
            }
            i.setSubmitted(true);
            i.setSubmittedAt(LocalDateTime.now());
            if (i.getOdometerReading() != null && i.getOdometerReading() > 0) {
                Vehicle v = job.getVehicle();
                if (i.getOdometerReading() > v.getOdometer()) {
                    v.setOdometer(i.getOdometerReading());
                }
            }
            audit.log("INSPECTION_SUBMITTED", job.getJobNo());
        }
        return i;
    }

    @Transactional
    public void reopen(Long jobId) {
        Inspection i = inspections.findByJobCardId(jobId).orElseThrow(() -> new NotFoundException("No inspection"));
        i.setSubmitted(false);
        i.setSubmittedAt(null);
        audit.log("INSPECTION_REOPENED", i.getJobCard().getJobNo());
    }

    // ---------- media ----------

    @Transactional
    public InspectionMedia addMedia(Long jobId, MultipartFile file, String caption) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a photo or video to upload.");
        }
        JobCard job = jobService.get(jobId);
        jobService.assertCanWork(job);
        Inspection i = inspections.findByJobCardId(jobId)
                .orElseThrow(() -> new BusinessException("Start the inspection first."));
        if (i.getMedia().size() >= MAX_MEDIA) {
            throw new BusinessException("Maximum " + MAX_MEDIA + " photos/videos per inspection.");
        }
        String type;
        try (InputStream in = file.getInputStream()) {
            type = sniff(in.readNBytes(16));
        } catch (IOException e) {
            throw new BusinessException("Could not read the uploaded file.");
        }
        if (type == null) {
            throw new BusinessException("Unsupported file. Upload JPEG, PNG, WebP, GIF photos or MP4, WebM, MOV videos.");
        }
        boolean video = type.startsWith("video/");
        if (file.getSize() > (video ? MAX_VIDEO : MAX_PHOTO)) {
            throw new BusinessException(video ? "Videos must be under 50 MB." : "Photos must be under 15 MB.");
        }
        String ext = switch (type) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            default -> ".mov";
        };
        String stored;
        try {
            stored = storage.store(file, ext);
        } catch (IOException e) {
            throw new BusinessException("Could not save the file. Please try again.");
        }
        InspectionMedia m = new InspectionMedia();
        m.setInspection(i);
        m.setKind(video ? MediaKind.VIDEO : MediaKind.PHOTO);
        m.setStoredName(stored);
        String orig = file.getOriginalFilename();
        m.setOriginalName(orig == null ? null : orig.substring(0, Math.min(orig.length(), 190)));
        m.setContentType(type);
        m.setSizeBytes(file.getSize());
        m.setCaption(Validators.blankToNull(caption));
        i.getMedia().add(m);
        audit.log("MEDIA_ADDED", job.getJobNo() + " " + type);
        return m;
    }

    @Transactional
    public void deleteMedia(Long mediaId) {
        InspectionMedia m = mediaRepo.findById(mediaId).orElseThrow(() -> new NotFoundException("Media not found"));
        Inspection i = m.getInspection();
        jobService.assertCanWork(i.getJobCard());
        i.getMedia().remove(m);
        storage.delete(m.getStoredName());
    }

    @Transactional(readOnly = true)
    public InspectionMedia media(Long id) {
        return mediaRepo.findById(id).orElseThrow(() -> new NotFoundException("Media not found"));
    }

    static String sniff(byte[] h) {
        int n = h.length;
        if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "image/png";
        }
        if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return "image/gif";
        }
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "image/webp";
        }
        if (n >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            if (h[8] == 'h' && h[9] == 'e' && h[10] == 'i') {
                return null; // HEIC photo - not displayable in browsers
            }
            if (h[8] == 'm' && h[9] == 'i' && h[10] == 'f') {
                return null;
            }
            return h[8] == 'q' && h[9] == 't' ? "video/quicktime" : "video/mp4";
        }
        if (n >= 4 && (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) {
            return "video/webm";
        }
        return null;
    }
}
