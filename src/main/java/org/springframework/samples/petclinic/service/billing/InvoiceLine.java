package org.springframework.samples.petclinic.service.billing;

import java.io.Serializable;
import java.util.Date;

/**
 * One charged visit inside a monthly invoice.
 */
public class InvoiceLine implements Serializable {

    private static final long serialVersionUID = 20140401L;

    private final Date visitDate;

    private final String petName;

    private final String petType;

    private final String description;

    private final long unitPrice;

    private final long surcharge;

    private final long discount;

    public InvoiceLine(Date visitDate, String petName, String petType, String description,
                       long unitPrice, long surcharge, long discount) {
        this.visitDate = visitDate;
        this.petName = petName;
        this.petType = petType;
        this.description = description;
        this.unitPrice = unitPrice;
        this.surcharge = surcharge;
        this.discount = discount;
    }

    public Date getVisitDate() {
        return visitDate;
    }

    public String getPetName() {
        return petName;
    }

    public String getPetType() {
        return petType;
    }

    public String getDescription() {
        return description;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getSurcharge() {
        return surcharge;
    }

    public long getDiscount() {
        return discount;
    }

    public long getAmount() {
        return unitPrice + surcharge - discount;
    }
}
