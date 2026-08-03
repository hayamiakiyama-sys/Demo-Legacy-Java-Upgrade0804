package org.springframework.samples.petclinic.service.billing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps the last closing result on disk so it can be re-printed without running the closing again.
 * The file is written with Java serialization, as the batch scheduler reads it back.
 */
@Component
public class ClosingSnapshotStore {

    private static final Logger LOG = LoggerFactory.getLogger(ClosingSnapshotStore.class);

    private static final String FILE_NAME = "petclinic-closing-snapshot.ser";

    public void save(File directory, String period, List<Invoice> invoices) {
        File file = new File(directory, FILE_NAME);
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(new FileOutputStream(file));
            out.writeObject(new Snapshot(period, new Date(), invoices));
            LOG.info("closing snapshot saved: {}", file.getAbsolutePath());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to save the closing snapshot", ex);
        } finally {
            close(out);
        }
    }

    public Snapshot loadLatest(File directory) {
        File file = new File(directory, FILE_NAME);
        if (!file.exists()) {
            return null;
        }
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new FileInputStream(file));
            return (Snapshot) in.readObject();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read the closing snapshot", ex);
        } finally {
            close(in);
        }
    }

    private void close(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            LOG.debug("could not close the snapshot stream", ignored);
        }
    }

    public static class Snapshot implements Serializable {

        private static final long serialVersionUID = 20140401L;

        private final String period;

        private final Date closedAt;

        private final List<Invoice> invoices;

        Snapshot(String period, Date closedAt, List<Invoice> invoices) {
            this.period = period;
            this.closedAt = closedAt;
            this.invoices = new ArrayList<Invoice>(invoices);
        }

        public String getPeriod() {
            return period;
        }

        public Date getClosedAt() {
            return closedAt;
        }

        public List<Invoice> getInvoices() {
            return invoices;
        }
    }
}
