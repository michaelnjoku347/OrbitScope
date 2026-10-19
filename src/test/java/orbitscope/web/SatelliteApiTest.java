package orbitscope.web;

import orbitscope.OrbitScopeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OrbitScopeApplication.class)
@AutoConfigureMockMvc
class SatelliteApiTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void listsSampleSatellitesAsJson() throws Exception {
        mvc.perform(get("/satellites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(greaterThan(0)))
                .andExpect(jsonPath("$.satellites[0].id").exists())
                .andExpect(jsonPath("$.satellites[0].name").exists());
    }

    @Test
    void returnsIssPositionAsJson() throws Exception {
        mvc.perform(get("/satellites/25544/position").param("at", "2026-10-19T14:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(equalTo(25544)))
                .andExpect(jsonPath("$.latitude").isNumber())
                .andExpect(jsonPath("$.longitude").isNumber())
                .andExpect(jsonPath("$.altitudeKm").isNumber());
    }

    @Test
    void unknownSatelliteIsNotFound() throws Exception {
        mvc.perform(get("/satellites/1/position"))
                .andExpect(status().isNotFound());
    }
}
