package com.cardpricer.util;

/**
 * Reads the application version generated from pom.xml by the build.
 */
public final class AppVersion {

    public static final String CURRENT = loadVersion();

    private static String loadVersion() {
        try (var input = AppVersion.class.getResourceAsStream("/app-version.properties")) {
            if (input == null) throw new IllegalStateException("Missing build version. Build with Maven.");
            var properties = new java.util.Properties();
            properties.load(input);
            String version = properties.getProperty("version");
            if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")) {
                throw new IllegalStateException("Invalid build version. Build with Maven.");
            }
            VersionNumber.parse(version);
            return version;
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    /** GitHub owner/repo used by {@link com.cardpricer.service.UpdateCheckService}. */
    public static final String GITHUB_OWNER = "NinjaPanda351";
    public static final String GITHUB_REPO  = "OCC_PRICER";

    private AppVersion() {}
}
