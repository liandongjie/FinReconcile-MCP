package com.example.finreconcile;

import com.example.finreconcile.repository.BatchRepository;
import com.example.finreconcile.repository.CsvBatchRepository;
import com.example.finreconcile.service.ReconcileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@SpringBootApplication
public class FinReconcileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinReconcileApplication.class, args);
    }

    @Bean
    BatchRepository batchRepository(@Value("${FIN_RECONCILE_DATA_DIR:data}") String dataDirectory) {
        return new CsvBatchRepository(Path.of(dataDirectory));
    }

    @Bean
    ReconcileService reconcileService(BatchRepository repository) {
        return new ReconcileService(repository);
    }
}
