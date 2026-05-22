package com.cryptosim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoSimApplication.class, args);
    }
}
