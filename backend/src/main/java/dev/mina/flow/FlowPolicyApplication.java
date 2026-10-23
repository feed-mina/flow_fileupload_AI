package dev.mina.flow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlowPolicyApplication {
    public static void main(String[] args) {
        if ("true".equals(System.getenv("MIGRATE_ONLY"))) {
            org.flywaydb.core.Flyway.configure().dataSource(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD"))
                .locations("classpath:db/migration").load().migrate();
            return;
        }
        SpringApplication.run(FlowPolicyApplication.class, args);
    }
}
