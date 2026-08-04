package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.samples.petclinic.service.billing.ClosingSnapshotStore.Snapshot;

/**
 * Characterization tests for {@link ClosingSnapshotStore} (Java serialization, P-SER). Pins the
 * save/load round-trip and the null-when-absent behavior. {@code loadLatest} has no caller in
 * src/main today (OQ-2/OQ-3); the on-disk {@code .ser} format is a Java-21 compatibility risk, so
 * this test also serves as the baseline for cross-version deserialization checks.
 */
public class ClosingSnapshotStoreTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ClosingSnapshotStore store = new ClosingSnapshotStore();

    @Test
    public void loadLatestReturnsNullWhenNoSnapshotExists() throws Exception {
        assertThat(store.loadLatest(tmp.newFolder())).isNull();
    }

    @Test
    public void savedSnapshotRoundTripsThroughJavaSerialization() throws Exception {
        File dir = tmp.newFolder();
        Invoice invoice = new Invoice(1, "Franklin George", new Date(0L), new Date(0L));
        invoice.addLine(new InvoiceLine(new Date(0L), "Rex", "dog", "annual", 4500, 0, 225));
        List<Invoice> invoices = Collections.singletonList(invoice);

        store.save(dir, "2013/01", invoices);
        Snapshot loaded = store.loadLatest(dir);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getPeriod()).isEqualTo("2013/01");
        assertThat(loaded.getClosedAt()).isNotNull();
        assertThat(loaded.getInvoices()).hasSize(1);
        Invoice back = loaded.getInvoices().get(0);
        assertThat(back.getOwnerName()).isEqualTo("Franklin George");
        assertThat(back.getSubtotal()).isEqualTo(4275L);
        assertThat(back.getTotal()).isEqualTo(4703L);
    }

    @Test
    public void snapshotFileIsWrittenWithTheFixedName() throws Exception {
        File dir = tmp.newFolder();
        store.save(dir, "2013/01", Collections.<Invoice>emptyList());
        assertThat(new File(dir, "petclinic-closing-snapshot.ser")).exists();
    }
}
