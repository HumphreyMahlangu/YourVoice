package io.github.humphreymahlangu.votetrust;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoteTrustApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoteTrustApplication.class, args);
    }
}
