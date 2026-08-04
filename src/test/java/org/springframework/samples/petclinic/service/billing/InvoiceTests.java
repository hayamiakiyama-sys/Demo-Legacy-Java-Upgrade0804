package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.Test;

/**
 * Characterization tests for invoice arithmetic (BR-8..BR-11): line amount = unitPrice + surcharge
 * - discount, subtotal = sum of lines, tax = round(subtotal * 0.1), total = subtotal + tax.
 * Half-up rounding of the tax is pinned explicitly.
 */
public class InvoiceTests {

    private static InvoiceLine line(long unit, long surcharge, long discount) {
        return new InvoiceLine(new Date(0L), "Leo", "cat", "checkup", unit, surcharge, discount);
    }

    @Test
    public void lineAmountAddsSurchargeAndSubtractsDiscount() {
        assertThat(line(4500, 1125, 0).getAmount()).isEqualTo(5625L);
        assertThat(line(4500, 0, 225).getAmount()).isEqualTo(4275L);
    }

    @Test
    public void lineAmountCanGoNegativeWhenDiscountExceedsCharge() {
        // No clamping today: a discount larger than unit+surcharge yields a negative amount.
        assertThat(line(100, 0, 500).getAmount()).isEqualTo(-400L);
    }

    @Test
    public void subtotalIsSumOfLineAmounts() {
        Invoice invoice = new Invoice(1, "Franklin George", new Date(0L), new Date(0L));
        invoice.addLine(line(4500, 0, 225));   // 4275
        invoice.addLine(line(5200, 0, 780));   // 4420
        assertThat(invoice.getSubtotal()).isEqualTo(8695L);
    }

    @Test
    public void taxIsTenPercentRoundedHalfUp() {
        // subtotal 4275 -> 427.5 -> rounds up to 428.
        Invoice halfUp = new Invoice(1, "x", new Date(0L), new Date(0L));
        halfUp.addLine(line(4500, 0, 225));
        assertThat(halfUp.getSubtotal()).isEqualTo(4275L);
        assertThat(halfUp.getTax()).isEqualTo(428L);
        assertThat(halfUp.getTotal()).isEqualTo(4703L);

        // subtotal 4270 -> 427.0 -> 427.
        Invoice exact = new Invoice(1, "x", new Date(0L), new Date(0L));
        exact.addLine(line(4270, 0, 0));
        assertThat(exact.getTax()).isEqualTo(427L);
        assertThat(exact.getTotal()).isEqualTo(4697L);
    }

    @Test
    public void emptyInvoiceHasZeroTotalsAndIsEmpty() {
        Invoice invoice = new Invoice(1, "x", new Date(0L), new Date(0L));
        assertThat(invoice.isEmpty()).isTrue();
        assertThat(invoice.getSubtotal()).isEqualTo(0L);
        assertThat(invoice.getTax()).isEqualTo(0L);
        assertThat(invoice.getTotal()).isEqualTo(0L);
    }
}
