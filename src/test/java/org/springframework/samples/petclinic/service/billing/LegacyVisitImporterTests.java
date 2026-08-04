package org.springframework.samples.petclinic.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.samples.petclinic.service.billing.LegacyVisitImporter.ImportedVisit;

/**
 * Characterization tests for {@link LegacyVisitImporter} (BR-20). Pins CSV parsing, field trimming,
 * blank-line skipping, error wrapping, and the two-digit-year century window ({@code
 * billing.two.digit.year.start=1980} -> years fall in [1980, 2079]). This importer has no caller in
 * src/main today (OQ-2); the tests document its actual behavior in case it is revived.
 */
public class LegacyVisitImporterTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final LegacyVisitImporter importer = new LegacyVisitImporter();

    private File fileWith(String content) throws Exception {
        File f = tmp.newFile();
        Files.write(f.toPath(), content.getBytes(Charset.defaultCharset()));
        return f;
    }

    private static int yearOf(Date date) {
        Calendar c = new GregorianCalendar();
        c.setTime(date);
        return c.get(Calendar.YEAR);
    }

    @Test
    public void parsesColumnsAndTrimsWhitespace() throws Exception {
        List<ImportedVisit> visits = importer.read(fileWith(" 7 , 13/01/05 , Checkup , Dr Tanaka \n"));

        assertThat(visits).hasSize(1);
        ImportedVisit v = visits.get(0);
        assertThat(v.getPetId()).isEqualTo(7);
        assertThat(v.getDescription()).isEqualTo("Checkup");
        assertThat(v.getStaffName()).isEqualTo("Dr Tanaka");
        Calendar c = new GregorianCalendar();
        c.setTime(v.getVisitDate());
        assertThat(c.get(Calendar.YEAR)).isEqualTo(2013);
        assertThat(c.get(Calendar.MONTH)).isEqualTo(Calendar.JANUARY);
        assertThat(c.get(Calendar.DAY_OF_MONTH)).isEqualTo(5);
    }

    @Test
    public void skipsBlankAndWhitespaceOnlyLines() throws Exception {
        List<ImportedVisit> visits = importer.read(
            fileWith("7,13/01/05,a,b\n\n   \n8,13/02/06,c,d\n"));
        assertThat(visits).hasSize(2);
    }

    /**
     * DISCREPANCY (see docs/as-is/06-open-questions.md OQ-15): the century window is NOT the clean
     * [1980, 2079] the code intends. Because {@link LegacyDateFormats} writes SimpleDateFormat's
     * private {@code defaultCenturyStart} field but never refreshes the derived
     * {@code defaultCenturyStartYear} pivot, "79" resolves to 1979 on Java 8, not 2079. Pinned as
     * the current (buggy) behavior; the public-API fallback taken on Java 21 yields 2079 instead.
     */
    @Test
    public void twoDigitYearPivotReflectsStaleCenturyStart() throws Exception {
        List<ImportedVisit> visits = importer.read(fileWith(
            "1,80/06/15,a,b\n2,79/12/31,a,b\n3,00/01/01,a,b\n4,13/01/05,a,b\n"));

        assertThat(yearOf(visits.get(0).getVisitDate())).isEqualTo(1980);
        assertThat(yearOf(visits.get(1).getVisitDate())).isEqualTo(1979); // intent was 2079
        assertThat(yearOf(visits.get(2).getVisitDate())).isEqualTo(2000);
        assertThat(yearOf(visits.get(3).getVisitDate())).isEqualTo(2013);
    }

    @Test
    public void extraColumnsBeyondTheFourthAreIgnored() throws Exception {
        List<ImportedVisit> visits = importer.read(fileWith("7,13/01/05,Checkup,Dr Tanaka,ignored\n"));
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).getStaffName()).isEqualTo("Dr Tanaka");
    }

    @Test(expected = IllegalStateException.class)
    public void tooFewColumnsThrows() throws Exception {
        importer.read(fileWith("7,13/01/05,Checkup\n"));
    }

    @Test(expected = IllegalStateException.class)
    public void nonNumericPetIdThrows() throws Exception {
        importer.read(fileWith("x,13/01/05,Checkup,Dr Tanaka\n"));
    }
}
