package org.springframework.samples.petclinic.service.billing;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes the closing result as the CSV file consumed by the accounting system.
 * The accounting system expects the platform default encoding.
 */
@Component
public class InvoiceCsvExporter {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceCsvExporter.class);

    private static final String HEADER = "請求番号,顧客名,診療日,ペット名,種別,診療内容,単価,休日加算,割引,金額";

    public File export(List<Invoice> invoices, File directory, String period) {
        File file = new File(directory, "invoices_" + period.replace('/', '-') + ".csv");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter(file));
            writer.println(HEADER);
            int sequence = 1;
            for (Invoice invoice : invoices) {
                for (InvoiceLine line : invoice.getLines()) {
                    writer.println(String.format("%06d,%s,%s,%s,%s,%s,%d,%d,%d,%d",
                        sequence++,
                        invoice.getOwnerName(),
                        dateFormat.format(line.getVisitDate()),
                        line.getPetName(),
                        line.getPetType(),
                        line.getDescription(),
                        line.getUnitPrice(),
                        line.getSurcharge(),
                        line.getDiscount(),
                        line.getAmount()));
                }
            }
            writer.flush();
            LOG.info("exported {} invoices to {}", invoices.size(), file.getAbsolutePath());
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to export invoices to " + file.getAbsolutePath(), ex);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
