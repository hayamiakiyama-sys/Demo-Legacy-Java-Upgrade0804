package org.springframework.samples.petclinic.service.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Pins the CSV handed over to the accounting system (IF-05). The file is written with the platform
 * default charset, which changes in Java 18 and later (T-06), so the test reads it back the same way.
 */
public class InvoiceCsvExporterTests {

    private static final String EXPECTED_HEADER =
        "請求番号,顧客名,診療日,ペット名,種別,診療内容,単価,休日加算,割引,金額";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writesOneRecordPerInvoiceLine() throws Exception {
        File csv = new InvoiceCsvExporter().export(invoices(), folder.getRoot(), "2013/01");

        List<String> lines = readWithDefaultCharset(csv);

        assertEquals("invoices_2013-01.csv", csv.getName());
        assertEquals(3, lines.size());
        assertEquals(EXPECTED_HEADER, lines.get(0));
        assertEquals("000001,Coleman Jean,2013/01/02,Max,cat,rabies shot,4000,0,200,3800", lines.get(1));
        assertEquals("000002,Coleman Jean,2013/01/05,Max,cat,neutered,4000,1000,0,5000", lines.get(2));
    }

    @Test
    public void writesTheHeaderInThePlatformDefaultCharset() throws Exception {
        File csv = new InvoiceCsvExporter().export(invoices(), folder.getRoot(), "2013/01");

        byte[] expected = EXPECTED_HEADER.getBytes(System.getProperty("file.encoding"));
        byte[] actual = new byte[expected.length];
        FileInputStream in = new FileInputStream(csv);
        try {
            assertEquals(expected.length, in.read(actual));
        } finally {
            in.close();
        }
        assertTrue(Arrays.equals(expected, actual));
    }

    private List<Invoice> invoices() {
        Invoice invoice = new Invoice(1, "Coleman Jean", date(2013, 1, 1), date(2013, 1, 31));
        invoice.addLine(new InvoiceLine(date(2013, 1, 2), "Max", "cat", "rabies shot", 4000L, 0L, 200L));
        invoice.addLine(new InvoiceLine(date(2013, 1, 5), "Max", "cat", "neutered", 4000L, 1000L, 0L));
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

    private List<String> readWithDefaultCharset(File file) throws Exception {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        try {
            String line = reader.readLine();
            while (line != null) {
                lines.add(line);
                line = reader.readLine();
            }
        } finally {
            reader.close();
        }
        return lines;
    }
}
