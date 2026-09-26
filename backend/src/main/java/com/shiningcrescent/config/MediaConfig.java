package com.shiningcrescent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MediaConfig implements WebMvcConfigurer {
    private final Path uploadRoot;

    public MediaConfig(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(uploadRoot.toUri().toString());
    }
}
