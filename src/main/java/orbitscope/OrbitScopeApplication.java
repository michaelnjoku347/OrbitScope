package orbitscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Milestone 4: Spring Boot REST API for satellite lists and positions. */
@SpringBootApplication
@EnableScheduling
public class OrbitScopeApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrbitScopeApplication.class, args);
    }
}
