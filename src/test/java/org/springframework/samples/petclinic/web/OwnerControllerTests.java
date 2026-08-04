package org.springframework.samples.petclinic.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Characterization tests for {@link OwnerController#processFindForm} (P-UI owner search): the
 * zero / one / many result branches. Uses standalone MockMvc with a mocked {@link ClinicService}.
 */
public class OwnerControllerTests {

    private ClinicService clinicService;
    private MockMvc mockMvc;

    private Owner owner(int id, String last) {
        Owner o = new Owner();
        o.setId(id);
        o.setFirstName("First");
        o.setLastName(last);
        o.setAddress("addr");
        o.setCity("city");
        o.setTelephone("1234567890");
        return o;
    }

    @Before
    public void setUp() {
        clinicService = mock(ClinicService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerController(clinicService)).build();
    }

    @Test
    public void noMatchRedisplaysFormWithNotFoundError() throws Exception {
        when(clinicService.findOwnerByLastName("Nobody")).thenReturn(Collections.<Owner>emptyList());

        mockMvc.perform(get("/owners").param("lastName", "Nobody"))
            .andExpect(status().isOk())
            .andExpect(view().name("owners/findOwners"))
            .andExpect(model().attributeHasFieldErrors("owner", "lastName"));
    }

    @Test
    public void singleMatchRedirectsToOwnerDetails() throws Exception {
        when(clinicService.findOwnerByLastName("Franklin"))
            .thenReturn(Collections.singletonList(owner(7, "Franklin")));

        mockMvc.perform(get("/owners").param("lastName", "Franklin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/7"));
    }

    @Test
    public void multipleMatchesShowSelectionList() throws Exception {
        when(clinicService.findOwnerByLastName("Davis"))
            .thenReturn(Arrays.asList(owner(1, "Davis"), owner(2, "Davis")));

        mockMvc.perform(get("/owners").param("lastName", "Davis"))
            .andExpect(status().isOk())
            .andExpect(view().name("owners/ownersList"))
            .andExpect(model().attributeExists("selections"));
    }
}
