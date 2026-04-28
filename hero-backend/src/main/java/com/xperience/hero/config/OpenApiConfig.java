package com.xperience.hero.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event RSVP Manager API")
                        .description("API for managing events, invitations, and RSVPs. " +
                                "Pass the host identifier via the **X-Host-Id** header on all host-only endpoints.")
                        .version("1.0.0"))
                .tags(List.of(
                        new Tag().name("Events").description("Create and manage events (host only)"),
                        new Tag().name("Invitations").description("Send invitations and look up invitation views"),
                        new Tag().name("RSVP").description("Submit or update an RSVP response"),
                        new Tag().name("Dashboard").description("Host dashboard — attendee list and counts")
                ));
    }
}
