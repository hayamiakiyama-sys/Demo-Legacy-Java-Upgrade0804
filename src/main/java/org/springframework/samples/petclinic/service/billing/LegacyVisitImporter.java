package org.springframework.samples.petclinic.service.billing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the visit file handed over by the previous accounting system. The file uses two digit
 * years and the platform default encoding.
 */
@Component
public class LegacyVisitImporter {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyVisitImporter.class);

    private static final String CONFIG_LOCATION = "/billing/billing.properties";

    private static final int DEFAULT_TWO_DIGIT_YEAR_START = 1980;

    public List<ImportedVisit> read(File file) {
        SimpleDateFormat dateFormat = LegacyDateFormats.importedVisitFormat(twoDigitYearStart());
        List<ImportedVisit> visits = new ArrayList<ImportedVisit>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (line != null) {
                if (!line.trim().isEmpty()) {
                    visits.add(parse(line, dateFormat));
                }
                line = reader.readLine();
            }
            LOG.info("imported {} visits from {}", visits.size(), file.getAbsolutePath());
            return visits;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to import visits from " + file.getAbsolutePath(), ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    LOG.debug("could not close the import file", ignored);
                }
            }
        }
    }

    private ImportedVisit parse(String line, SimpleDateFormat dateFormat) throws Exception {
        String[] columns = line.split(",", -1);
        if (columns.length < 4) {
            throw new IllegalArgumentException("unexpected import record: " + line);
        }
        Date visitDate = dateFormat.parse(columns[1].trim());
        return new ImportedVisit(Integer.parseInt(columns[0].trim()), visitDate, columns[2].trim(), columns[3].trim());
    }

    private int twoDigitYearStart() {
        InputStream in = getClass().getResourceAsStream(CONFIG_LOCATION);
        if (in == null) {
            return DEFAULT_TWO_DIGIT_YEAR_START;
        }
        Properties properties = new Properties();
        try {
            properties.load(in);
            return Integer.parseInt(properties.getProperty("billing.two.digit.year.start",
                String.valueOf(DEFAULT_TWO_DIGIT_YEAR_START)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read " + CONFIG_LOCATION, ex);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
                LOG.debug("could not close billing properties", ignored);
            }
        }
    }

    public static class ImportedVisit {

        private final int petId;

        private final Date visitDate;

        private final String description;

        private final String staffName;

        ImportedVisit(int petId, Date visitDate, String description, String staffName) {
            this.petId = petId;
            this.visitDate = visitDate;
            this.description = description;
            this.staffName = staffName;
        }

        public int getPetId() {
            return petId;
        }

        public Date getVisitDate() {
            return visitDate;
        }

        public String getDescription() {
            return description;
        }

        public String getStaffName() {
            return staffName;
        }
    }
}
