package orbitscope.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // the globe page is on :5173 and the api is on :8080.
        // the browser blocked it until I added this.
        registry.addMapping("/**").allowedMethods("GET").allowedOriginPatterns("*");
    }
}
