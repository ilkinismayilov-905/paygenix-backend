//package com.example.paygenix.config;
//
//import io.github.cdimascio.dotenv.Dotenv;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.PropertySource;
//import org.springframework.core.env.ConfigurableEnvironment;
//
//import jakarta.annotation.PostConstruct;
//
//@Configuration
//public class DotenvConfig {
//
//    private final ConfigurableEnvironment environment;
//
//    public DotenvConfig(ConfigurableEnvironment environment) {
//        this.environment = environment;
//    }
//
//    @PostConstruct
//    public void loadDotenv() {
//        Dotenv dotenv = Dotenv.configure()
//                .ignoreIfMissing()
//                .load();
//
//        dotenv.entries().forEach(entry -> {
//            System.setProperty(entry.getKey(), entry.getValue());
//        });
//    }
//}