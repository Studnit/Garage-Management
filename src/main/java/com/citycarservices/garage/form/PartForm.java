package com.citycarservices.garage.form;

import com.citycarservices.garage.domain.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PartForm {
    @NotBlank(message = "Part number is required")
    @Size(max = 50)
    private String partNo;

    @NotBlank(message = "Part name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 80)
    private String brand;

    @Size(max = 80)
    private String category;

    @Size(max = 300)
    private String compatibility;

    @NotNull(message = "Purchase price is required")
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @NotNull(message = "Selling price is required")
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @NotNull(message = "GST rate is required")
    @DecimalMin("0.00")
    @DecimalMax("28.00")
    private BigDecimal gstRate = new BigDecimal("28.00");

    @Size(max = 10)
    private String hsnCode;

    @NotNull(message = "Minimum stock level is required")
    @DecimalMin("0.00")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal minStock = BigDecimal.ZERO;

    /** Opening stock, only used when creating a part. */
    @DecimalMin("0.00")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal openingStock = BigDecimal.ZERO;

    @Size(max = 15)
    private String unit = "Nos";

    @Size(max = 40)
    private String rackLocation;

    private Long supplierId;

    private boolean active = true;

    public static PartForm of(Part p) {
        PartForm f = new PartForm();
        f.partNo = p.getPartNo();
        f.name = p.getName();
        f.brand = p.getBrand();
        f.category = p.getCategory();
        f.compatibility = p.getCompatibility();
        f.purchasePrice = p.getPurchasePrice();
        f.sellingPrice = p.getSellingPrice();
        f.gstRate = p.getGstRate();
        f.hsnCode = p.getHsnCode();
        f.minStock = p.getMinStock();
        f.unit = p.getUnit();
        f.rackLocation = p.getRackLocation();
        f.supplierId = p.getSupplier() == null ? null : p.getSupplier().getId();
        f.active = p.isActive();
        return f;
    }
}
