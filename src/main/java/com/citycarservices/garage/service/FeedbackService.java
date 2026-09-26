package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.Booking;
import com.citycarservices.garage.domain.BookingStatus;
import com.citycarservices.garage.domain.Feedback;
import com.citycarservices.garage.form.FeedbackForm;
import com.citycarservices.garage.repo.BookingRepository;
import com.citycarservices.garage.repo.FeedbackRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedback;
    private final BookingRepository bookings;
    private final AuditService audit;

    public FeedbackService(FeedbackRepository feedback, BookingRepository bookings, AuditService audit) {
        this.feedback = feedback;
        this.bookings = bookings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Feedback> all() {
        return feedback.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public List<Feedback> published(int max) {
        return feedback.findByPublishedTrueOrderByIdDesc(PageRequest.of(0, max));
    }

    @Transactional(readOnly = true)
    public List<Feedback> forCustomer(Long customerId) {
        return feedback.findByCustomerIdOrderByIdDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Feedback forBooking(Long bookingId) {
        return feedback.findByBookingId(bookingId).orElse(null);
    }

    @Transactional(readOnly = true)
    public double average() {
        return feedback.findAll().stream().mapToInt(Feedback::getRating).average().orElse(0);
    }

    @Transactional(readOnly = true)
    public long count() {
        return feedback.count();
    }

    @Transactional
    public Feedback submit(Long customerId, Long bookingId, FeedbackForm f) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!b.getCustomer().getId().equals(customerId)) {
            throw new NotFoundException("Booking not found");
        }
        if (b.getStatus() != BookingStatus.DELIVERED && b.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessException("You can review a service once your vehicle has been delivered.");
        }
        if (feedback.findByBookingId(bookingId).isPresent()) {
            throw new BusinessException("You have already reviewed this service. Thank you!");
        }
        Feedback fb = new Feedback();
        fb.setCustomer(b.getCustomer());
        fb.setBooking(b);
        fb.setRating(f.getRating());
        fb.setReviewText(Validators.blankToNull(f.getReviewText()));
        fb = feedback.save(fb);
        audit.log("FEEDBACK", b.getBookingNo() + " rating " + f.getRating());
        return fb;
    }

    @Transactional
    public void reply(Long id, String reply) {
        Feedback fb = feedback.findById(id).orElseThrow(() -> new NotFoundException("Feedback not found"));
        fb.setReply(Validators.blankToNull(reply));
    }

    @Transactional
    public void togglePublished(Long id) {
        Feedback fb = feedback.findById(id).orElseThrow(() -> new NotFoundException("Feedback not found"));
        fb.setPublished(!fb.isPublished());
    }
}
