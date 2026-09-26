package com.citycarservices.garage.service;

import com.citycarservices.garage.domain.DocSequence;
import com.citycarservices.garage.repo.DocSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Gap-free-ish, per-year document numbers such as INV-2026-00001. */
@Service
public class NumberService {

    private final DocSequenceRepository repo;

    public NumberService(DocSequenceRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public String next(String prefix) {
        int year = LocalDate.now().getYear();
        String key = prefix + year;
        DocSequence seq = repo.lockByKey(key).orElse(null);
        if (seq == null) {
            seq = new DocSequence();
            seq.setSeqKey(key);
            seq.setLastValue(0);
        }
        seq.setLastValue(seq.getLastValue() + 1);
        repo.saveAndFlush(seq);
        return String.format("%s-%d-%05d", prefix, year, seq.getLastValue());
    }
}
