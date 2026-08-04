package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Characterization tests for {@link InvoiceCsvExporter} (BR-19). These pin the accounting file's
 * name, header, 6-digit running sequence, date format, and — importantly for the Java 21 migration
 * — that the file is written with the JVM's platform default charset (JEP 400 risk), and that
 * fields are NOT escaped (a comma in a name corrupts the row).
 */
public class InvoiceCsvExporterTests {

    private static final String HEADER =
        "\u8acb\u6c42\u756a\u53f7,\u9867\u5ba2\u540d,\u8a3a\u7642\u65e5,\u30da\u30c3\u30c8\u540d,"
        + "\u7a2e\u5225,\u8a3a\u7642\u5185\u5bb9,\u5358\u4fa1,\u4f11\u65e5\u52a0\u7b97,\u5272\u5f15,\u91d1\u984d";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final InvoiceCsvExporter exporter = new InvoiceCsvExporter();

    private static Date date(int y, int m0, int d) {
        Calendar c = new GregorianCalendar();
        c.clear();
        c.set(y, m0, d);
        return c.getTime();
    }

    private Invoice invoice(String ownerName, InvoiceLine... lines) {
        Invoice invoice = new Invoice(1, ownerName, date(2013, 0, 1), date(2013, 0, 31));
        for (InvoiceLine line : lines) {
            invoice.addLine(line);
        }
        return invoice;
    }

    @Test
    public void fileNameReplacesSlashInPeriod() throws Exception {
        File dir = tmp.newFolder();
        File out = exporter.export(Collections.<Invoice>emptyList(), dir, "2013/01");
        assertThat(out.getName()).isEqualTo("invoices_2013-01.csv");
    }

    @Test
    public void contentUsesHeaderSequenceAndDefaultCharset() throws Exception {
        File dir = tmp.newFolder();
        InvoiceLine l1 = new InvoiceLine(date(2013, 0, 4), "Rex", "dog", "annual", 4500, 0, 225);
        InvoiceLine l2 = new InvoiceLine(date(2013, 0, 5), "Rex", "dog", "weekend", 4500, 1125, 0);
        List<Invoice> invoices = Collections.singletonList(invoice("Franklin George", l1, l2));

        File out = exporter.export(invoices, dir, "2013/01");

        String nl = System.lineSeparator();
        String expected = HEADER + nl
            + "000001,Franklin George,2013/01/04,Rex,dog,annual,4500,0,225,4275" + nl
            + "000002,Franklin George,2013/01/05,Rex,dog,weekend,4500,1125,0,5625" + nl;

        // Pins that the file is written in the platform default charset (not a forced UTF-8).
        byte[] actual = Files.readAllBytes(out.toPath());
        assertThat(actual).isEqualTo(expected.getBytes(Charset.defaultCharset()));
    }

    @Test
    public void fieldsAreNotEscapedSoACommaInTheNameCorruptsTheRow() throws Exception {
        File dir = tmp.newFolder();
        InvoiceLine l = new InvoiceLine(date(2013, 0, 4), "Rex", "dog", "annual", 4500, 0, 225);
        File out = exporter.export(Collections.singletonList(invoice("Franklin, George", l)), dir, "2013/01");

        List<String> lines = Files.readAllLines(out.toPath(), Charset.defaultCharset());
        String dataRow = lines.get(1);
        assertThat(dataRow).contains("Franklin, George");
        // Header has 10 columns; the un-escaped comma pushes the data row to 11 fields.
        assertThat(HEADER.split(",", -1)).hasSize(10);
        assertThat(dataRow.split(",", -1)).hasSize(11);
    }
}
