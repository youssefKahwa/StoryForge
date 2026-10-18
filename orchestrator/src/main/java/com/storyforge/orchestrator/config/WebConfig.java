package com.storyforge.orchestrator.config;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StoryForgeProperties properties;
    private final Path dashboardDir;

    public WebConfig(StoryForgeProperties properties,
                     @Value("${storyforge.dashboard-dir:../dashboard/public}") String dashboardDir) {
        this.properties = properties;
        this.dashboardDir = Path.of(dashboardDir).toAbsolutePath().normalize();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.corsOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .exposedHeaders("Content-Range", "Accept-Ranges");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /** Serves the built Gatsby dashboard at the API's own address, so one process and one URL is enough. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(new FileSystemResource(dashboardDir.toString() + "/"))
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        Resource direct = super.getResource(resourcePath, location);
                        if (direct != null) {
                            return direct;
                        }
                        String base = resourcePath.isEmpty() || resourcePath.endsWith("/") ? resourcePath
                                : resourcePath + "/";
                        Resource page = super.getResource(base + "index.html", location);
                        return page != null ? page : super.getResource("404.html", location);
                    }
                });
    }
}
