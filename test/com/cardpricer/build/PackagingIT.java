package com.cardpricer.build;

import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Runs after shading, against the deliverable rather than the compilation directory. */
class PackagingIT {
    @Test
    void artifactContainsRuntimeDependenciesAndOnlyApprovedApplicationResources() throws Exception {
        Path artifact = Path.of(System.getProperty("app.jar"));
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertEquals("com.cardpricer.gui.MainSwingApplication", jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertEquals(System.getProperty("project.version"), jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            for (String resource : new String[]{"assets/OCC_Icon_400x400.png", "changelog.txt", "app-version.properties",
                    "org/json/JSONObject.class", "org/apache/commons/csv/CSVFormat.class", "org/apache/commons/io/IOUtils.class",
                    "org/apache/commons/codec/binary/Base64.class", "com/formdev/flatlaf/FlatLaf.class", "org/sqlite/JDBC.class",
                    "org/sqlite/native/Windows/x86_64/sqlitejdbc.dll", "org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib"}) {
                assertNotNull(jar.getEntry(resource), resource);
            }
            var names = jar.stream().map(entry -> entry.getName()).toList();
            assertTrue(names.stream().anyMatch(name -> name.startsWith("com/formdev/flatlaf/intellijthemes/themes/") && name.endsWith(".json")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith("com/formdev/flatlaf/natives/") && name.endsWith(".dll")));
            assertFalse(names.stream().anyMatch(name -> name.contains("HelpEmailService") || name.startsWith("javax/mail/") || name.startsWith("com/sun/mail/")));
            assertFalse(names.stream().anyMatch(name -> name.startsWith("audit/") || name.startsWith("data/")
                    || name.startsWith(".git/") || name.startsWith(".idea/") || name.endsWith(".java") || name.endsWith("notes.txt") || name.endsWith("failedSetNotes")));
            for (String name : names) {
                if (name.startsWith("com/cardpricer/") && !name.endsWith("/")) assertTrue(name.endsWith(".class"), name);
            }
            Properties version = new Properties();
            try (var stream = jar.getInputStream(jar.getEntry("app-version.properties"))) { version.load(stream); }
            assertEquals(System.getProperty("project.version"), version.getProperty("version"));
        }
        // No test or IDE dependency can satisfy classes missing from the deliverable.
        try (var loader = new URLClassLoader(new java.net.URL[]{artifact.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> version = Class.forName("com.cardpricer.util.AppVersion", true, loader);
            assertEquals(System.getProperty("project.version"), version.getField("CURRENT").get(null));
            for (String name : new String[]{"com.cardpricer.gui.MainSwingApplication", "com.formdev.flatlaf.FlatLightLaf",
                    "com.cardpricer.service.CardCsvEncoder", "org.apache.commons.csv.CSVFormat"}) {
                assertNotNull(Class.forName(name, false, loader));
            }
        }
    }
}
