package fr.expand.project.importdata.api.server;

import org.junit.Assert;
import org.junit.Test;

public class ImportApiServerSecurityTests {

    @Test
    public void normalizeSameSite_shouldDowngradeNoneForInsecureCookies() {
        Assert.assertEquals("Lax", ImportApiServer.normalizeSameSite("None", false));
        Assert.assertEquals("None", ImportApiServer.normalizeSameSite("None", true));
        Assert.assertEquals("Strict", ImportApiServer.normalizeSameSite("Strict", false));
    }

    @Test
    public void buildSessionCookieHeader_shouldIncludeExpectedSecurityFlags() {
        String header = ImportApiServer.buildSessionCookieHeader(
            "expand_session",
            "token-123",
            true,
            3600L,
            "None",
            false
        );

        Assert.assertTrue(header.contains("expand_session=token-123"));
        Assert.assertTrue(header.contains("Path=/"));
        Assert.assertTrue(header.contains("HttpOnly"));
        Assert.assertTrue(header.contains("SameSite=None"));
        Assert.assertTrue(header.contains("Max-Age=3600"));
        Assert.assertTrue(header.contains("Secure"));
    }

    @Test
    public void buildSessionCookieHeader_shouldExpireCookieImmediatelyWhenClearing() {
        String header = ImportApiServer.buildSessionCookieHeader(
            "expand_session",
            "",
            false,
            0L,
            "Lax",
            true
        );

        Assert.assertTrue(header.contains("expand_session="));
        Assert.assertTrue(header.contains("SameSite=Lax"));
        Assert.assertTrue(header.contains("Max-Age=0"));
        Assert.assertTrue(header.contains("Expires=Thu, 01 Jan 1970 00:00:00 GMT"));
    }
}
