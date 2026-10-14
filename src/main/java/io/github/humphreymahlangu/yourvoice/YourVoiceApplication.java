package io.github.humphreymahlangu.yourvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class YourVoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(YourVoiceApplication.class, args);
    }
}
