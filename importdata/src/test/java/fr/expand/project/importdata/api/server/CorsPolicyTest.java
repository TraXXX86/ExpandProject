package fr.expand.project.importdata.api.server;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class CorsPolicyTest {

    @Test
    public void fromSettings_shouldUseDevDefaults() {
        CorsPolicy policy = CorsPolicy.fromSettings(new HashMap<>());

        Assert.assertEquals("dev", policy.getEnvironment());
        Assert.assertTrue(policy.isOriginAllowed("http://localhost:5173"));
        Assert.assertTrue(policy.isOriginAllowed("http://127.0.0.1:3000/"));
        Assert.assertTrue(policy.isAllowCredentials());
    }

    @Test
    public void fromSettings_shouldUseEnvironmentSpecificOrigins() {
        Map<String, String> settings = new HashMap<>();
        settings.put("APP_ENV", "recette");
        settings.put(
            "CORS_ALLOWED_ORIGINS_RECETTE",
            "https://recette.expand.example, https://admin.recette.expand.example/"
        );

        CorsPolicy policy = CorsPolicy.fromSettings(settings);

        Assert.assertEquals("recette", policy.getEnvironment());
        Assert.assertTrue(policy.isOriginAllowed("https://recette.expand.example"));
        Assert.assertTrue(policy.isOriginAllowed("https://admin.recette.expand.example"));
        Assert.assertFalse(policy.isOriginAllowed("http://localhost:5173"));
    }

    @Test
    public void resolve_shouldFilterRequestedHeadersAndEchoAllowedOrigin() {
        Map<String, String> settings = new HashMap<>();
        settings.put("CORS_ALLOWED_ORIGINS", "https://ui.expand.example");

        CorsPolicy policy = CorsPolicy.fromSettings(settings);
        CorsPolicy.ResolvedCors resolvedCors = policy.resolve(
            "https://ui.expand.example/",
            "Authorization, X-Session-Token, X-Not-Allowed"
        );

        Assert.assertTrue(resolvedCors.isAllowed());
        Assert.assertEquals(
            "https://ui.expand.example",
            resolvedCors.getHeaders().get("Access-Control-Allow-Origin")
        );
        Assert.assertEquals(
            "Authorization,X-Session-Token",
            resolvedCors.getHeaders().get("Access-Control-Allow-Headers")
        );
        Assert.assertEquals("true", resolvedCors.getHeaders().get("Access-Control-Allow-Credentials"));
        Assert.assertEquals("Origin,Access-Control-Request-Headers", resolvedCors.getHeaders().get("Vary"));
    }

    @Test
    public void resolve_shouldRejectDisallowedOrigins() {
        Map<String, String> settings = new HashMap<>();
        settings.put("APP_ENV", "prod");

        CorsPolicy policy = CorsPolicy.fromSettings(settings);
        CorsPolicy.ResolvedCors resolvedCors = policy.resolve("https://evil.expand.example", "Authorization");

        Assert.assertFalse(resolvedCors.isAllowed());
        Assert.assertTrue(resolvedCors.getHeaders().isEmpty());
    }
}
