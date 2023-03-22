package com.example;
// tag::class[]
import kalix.javasdk.testkit.KalixTestKit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestkitConfig {
    @Bean
    public KalixTestKit.Settings settings() {
        return KalixTestKit.Settings.DEFAULT.withAclEnabled(); // <1>
    }
}
// end::class[]
