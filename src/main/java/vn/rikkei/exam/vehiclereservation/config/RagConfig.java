package vn.rikkei.exam.vehiclereservation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.rikkei.exam.vehiclereservation.service.rag.CorpusIngestionService;

@Configuration
public class RagConfig {

    @Bean
    public ApplicationRunner corpusIngestionRunner(CorpusIngestionService corpusIngestionService,
                                                   @Value("${app.rag.ingest-on-startup:true}") boolean ingestOnStartup) {
        return args -> {
            if (ingestOnStartup) {
                corpusIngestionService.ingest();
            }
        };
    }
}
