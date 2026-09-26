package com.citycarservices.garage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doc_sequence")
@Getter
@Setter
public class DocSequence {

    @Id
    @Column(length = 40)
    private String seqKey;

    private long lastValue;
}
