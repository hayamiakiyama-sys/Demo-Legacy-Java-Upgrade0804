package org.springframework.samples.petclinic.service.billing;

import static org.junit.Assert.assertEquals;

import java.io.File;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Pins the import of the file from the old accounting system (IF-07, BR-22). The century window now
 * comes from SimpleDateFormat#set2DigitYearStart, so the configured 1980 start is really applied.
 */
public class LegacyVisitImporterTests {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private TimeZone originalTimeZone;

    @Before
    public void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void readsTwoDigitYearsFromNineteenEighty() throws Exception {
        File file = write("7, 85/04/01 ,rabies shot,Sato", "7,79/12/31,checkup,Suzuki");

        List<LegacyVisitImporter.ImportedVisit> visits = new LegacyVisitImporter().read(file);

        assertEquals(2, visits.size());
        assertEquals("1985/04/01", format(visits.get(0).getVisitDate()));
        assertEquals("2079/12/31", format(visits.get(1).getVisitDate()));
    }

    @Test
    public void readsEveryColumnAndSkipsBlankLines() throws Exception {
        File file = write("12,90/06/15,neutered,Tanaka", "", "   ");

        List<LegacyVisitImporter.ImportedVisit> visits = new LegacyVisitImporter().read(file);

        assertEquals(1, visits.size());
        assertEquals(12, visits.get(0).getPetId());
        assertEquals("neutered", visits.get(0).getDescription());
        assertEquals("Tanaka", visits.get(0).getStaffName());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsARecordWithMissingColumns() throws Exception {
        new LegacyVisitImporter().read(write("7,85/04/01,rabies shot"));
    }

    private String format(java.util.Date date) {
        return new SimpleDateFormat("yyyy/MM/dd").format(date);
    }

    private File write(String... lines) throws Exception {
        File file = folder.newFile("visits.csv");
        PrintWriter writer = new PrintWriter(file, "UTF-8");
        try {
            for (String line : lines) {
                writer.println(line);
            }
        } finally {
            writer.close();
        }
        return file;
    }
}
