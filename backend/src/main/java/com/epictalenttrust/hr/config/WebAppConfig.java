package com.epictalenttrust.hr.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves the web app from the application itself when it is packaged inside (the portable edition).
 * Screens are client-side routes, so any path that is not a file or an API call gets index.html.
 * In the Docker setup nginx serves the web app instead and this configuration stays off.
 */
@Configuration
@ConditionalOnResource(resources = "classpath:static/index.html")
public class WebAppConfig implements WebMvcConfigurer {

    private static final String WEB_ROOT = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Built files have content hashes in their names, so browsers may keep them.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(WEB_ROOT + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations(WEB_ROOT)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        if (path.startsWith("api/") || path.startsWith("actuator/")) {
                            return null;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}
