package org.springframework.samples.petclinic.service.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Pins the serialized closing snapshot read back by the batch scheduler (IF-06, T-09).
 */
public class ClosingSnapshotStoreTests {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final ClosingSnapshotStore store = new ClosingSnapshotStore();

    @Test
    public void returnsNullWhenNoClosingHasRunYet() {
        assertNull(store.loadLatest(folder.getRoot()));
    }

    @Test
    public void writesAndReadsBackTheClosingResult() {
        store.save(folder.getRoot(), "2013/01", invoices());

        ClosingSnapshotStore.Snapshot snapshot = store.loadLatest(folder.getRoot());

        assertEquals("2013/01", snapshot.getPeriod());
        assertEquals(1, snapshot.getInvoices().size());
        Invoice invoice = snapshot.getInvoices().get(0);
        assertEquals("Coleman Jean", invoice.getOwnerName());
        assertEquals(3800L, invoice.getSubtotal());
        assertEquals(4180L, invoice.getTotal());
    }

    private List<Invoice> invoices() {
        Invoice invoice = new Invoice(1, "Coleman Jean", date(2013, 1, 1), date(2013, 1, 31));
        invoice.addLine(new InvoiceLine(date(2013, 1, 2), "Max", "cat", "rabies shot", 4000L, 0L, 200L));
        List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice);
        return invoices;
    }

    private Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }
}
