package ai.simplfin.doczbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ai.simplfin.doczbot")
public class DoczbottApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoczbottApplication.class, args);
    }
}