package orbitscope.web;

import orbitscope.TleFetcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TleFetcherConfig {
    @Bean
    public TleFetcher tleFetcher() {
        return new TleFetcher();
    }
}
