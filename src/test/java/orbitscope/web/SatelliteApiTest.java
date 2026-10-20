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

    @Test
    void listIncludesGroup() throws Exception {
        mvc.perform(get("/satellites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.satellites[0].group").exists());
    }

    @Test
    void compactPositions() throws Exception {
        mvc.perform(get("/satellites/positions/compact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(greaterThan(0)))
                .andExpect(jsonPath("$.ids").isArray())
                .andExpect(jsonPath("$.latitude").isArray());
    }

    @Test
    void issPassesFromNewYork() throws Exception {
        mvc.perform(get("/satellites/25544/passes").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observer.name").value("New York"))
                .andExpect(jsonPath("$.passes").isArray());
    }
}
