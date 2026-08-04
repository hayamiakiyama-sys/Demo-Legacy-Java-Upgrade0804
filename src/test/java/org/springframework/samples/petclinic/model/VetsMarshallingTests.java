package org.springframework.samples.petclinic.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Characterization tests for the veterinarian API payload ({@code /vets.xml} via JAXB and
 * {@code /vets.json} via Jackson) at the serialization layer (P-API). JAXB moves to Jakarta / JAXB
 * 4 on Java 21, so pinning the current element/property shape guards the wire format.
 */
public class VetsMarshallingTests {

    private static Vets sampleVets() {
        Vet vet = new Vet();
        vet.setId(1);
        vet.setFirstName("James");
        vet.setLastName("Carter");
        Specialty radiology = new Specialty();
        radiology.setId(1);
        radiology.setName("radiology");
        vet.addSpecialty(radiology);

        Vets vets = new Vets();
        vets.getVetList().add(vet);
        return vets;
    }

    @Test
    public void jaxbMarshalsVetsWithExpectedElements() throws Exception {
        JAXBContext ctx = JAXBContext.newInstance(Vets.class);
        Marshaller marshaller = ctx.createMarshaller();
        StringWriter writer = new StringWriter();
        marshaller.marshal(sampleVets(), writer);
        String xml = writer.toString();

        assertThat(xml).contains("<vets>");
        assertThat(xml).contains("<vetList>");
        assertThat(xml).contains("<firstName>James</firstName>");
        assertThat(xml).contains("<lastName>Carter</lastName>");
        assertThat(xml).contains("radiology");
    }

    @Test
    public void jacksonSerializesVetsAsVetListArray() throws Exception {
        String json = new ObjectMapper().writeValueAsString(sampleVets());

        assertThat(json).contains("\"vetList\"");
        assertThat(json).contains("\"firstName\":\"James\"");
        assertThat(json).contains("\"lastName\":\"Carter\"");
        assertThat(json).contains("\"nrOfSpecialties\":1");
        assertThat(json).contains("radiology");
    }
}
