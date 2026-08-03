package org.springframework.samples.petclinic.web;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.samples.petclinic.service.billing.ClosingSnapshotStore;
import org.springframework.samples.petclinic.service.billing.Invoice;
import org.springframework.samples.petclinic.service.billing.InvoiceCsvExporter;
import org.springframework.samples.petclinic.service.billing.LegacyDateFormats;
import org.springframework.samples.petclinic.service.billing.MonthlyClosingService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Monthly closing screen: shows the invoices of a period and exports them for the accounting system.
 */
@Controller
public class BillingController {

    private static final String CONFIG_LOCATION = "/billing/billing.properties";

    private final MonthlyClosingService monthlyClosingService;

    private final InvoiceCsvExporter csvExporter;

    private final ClosingSnapshotStore snapshotStore;

    @Autowired
    public BillingController(MonthlyClosingService monthlyClosingService, InvoiceCsvExporter csvExporter,
                             ClosingSnapshotStore snapshotStore) {
        this.monthlyClosingService = monthlyClosingService;
        this.csvExporter = csvExporter;
        this.snapshotStore = snapshotStore;
    }

    @RequestMapping(value = "/billing/monthly", method = RequestMethod.GET)
    public String showMonthlyClosing(@RequestParam(value = "period", required = false) String period,
                                     Map<String, Object> model) {
        String targetPeriod = period == null ? currentPeriod() : period;
        List<Invoice> invoices = monthlyClosingService.close(targetPeriod);
        model.put("period", targetPeriod);
        model.put("invoices", invoices);
        model.put("grandTotal", grandTotal(invoices));
        return "billing/monthlyReport";
    }

    @RequestMapping(value = "/billing/monthly/export", method = RequestMethod.POST)
    public String exportMonthlyClosing(@RequestParam("period") String period, Map<String, Object> model) {
        List<Invoice> invoices = monthlyClosingService.close(period);
        File directory = exportDirectory();
        File csv = csvExporter.export(invoices, directory, period);
        snapshotStore.save(directory, period, invoices);
        model.put("period", period);
        model.put("invoices", invoices);
        model.put("grandTotal", grandTotal(invoices));
        model.put("exportedFile", csv.getAbsolutePath());
        return "billing/monthlyReport";
    }

    private long grandTotal(List<Invoice> invoices) {
        long total = 0L;
        for (Invoice invoice : invoices) {
            total += invoice.getTotal();
        }
        return total;
    }

    private String currentPeriod() {
        return LegacyDateFormats.periodFormat().format(new java.util.Date());
    }

    private File exportDirectory() {
        String configured = null;
        InputStream in = getClass().getResourceAsStream(CONFIG_LOCATION);
        if (in != null) {
            Properties properties = new Properties();
            try {
                properties.load(in);
                configured = properties.getProperty("billing.export.dir");
            } catch (Exception ex) {
                throw new IllegalStateException("failed to read " + CONFIG_LOCATION, ex);
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // the stream is read fully, nothing to recover
                }
            }
        }
        if (configured == null || configured.trim().isEmpty()) {
            return new File(System.getProperty("java.io.tmpdir"));
        }
        return new File(configured.trim());
    }
}
