package org.springframework.samples.petclinic.service.billing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Monthly invoice for one owner.
 */
public class Invoice implements Serializable {

    private static final long serialVersionUID = 20140401L;

    private final int ownerId;

    private final String ownerName;

    private final Date periodFrom;

    private final Date periodTo;

    private final List<InvoiceLine> lines = new ArrayList<InvoiceLine>();

    public Invoice(int ownerId, String ownerName, Date periodFrom, Date periodTo) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
    }

    public void addLine(InvoiceLine line) {
        lines.add(line);
    }

    public int getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Date getPeriodFrom() {
        return periodFrom;
    }

    public Date getPeriodTo() {
        return periodTo;
    }

    public List<InvoiceLine> getLines() {
        return lines;
    }

    public long getSubtotal() {
        long total = 0L;
        for (InvoiceLine line : lines) {
            total += line.getAmount();
        }
        return total;
    }

    public long getTax() {
        return Math.round(getSubtotal() * 0.1d);
    }

    public long getTotal() {
        return getSubtotal() + getTax();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
