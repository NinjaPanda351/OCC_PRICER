# Runtime dependencies

Maven declares runtime versions in `pom.xml` and preserves dependency license and notice resources in the shaded JAR.

| Component | Version | License / upstream |
|---|---|---|
| JSON-java | 20231013 | Public domain — https://github.com/stleary/JSON-java |
| FlatLaf and IntelliJ themes | 3.7 | Apache-2.0 — https://github.com/JFormDesigner/FlatLaf |
| Apache Commons CSV | 1.14.1 | Apache-2.0 — https://commons.apache.org/proper/commons-csv/ |
| Apache Commons IO | 2.20.0 | Apache-2.0 — https://commons.apache.org/proper/commons-io/ |
| Apache Commons Codec | 1.19.0 | Apache-2.0 — https://commons.apache.org/proper/commons-codec/ |
| Xerial SQLite JDBC | 3.53.2.0 | Apache-2.0 / BSD-2-Clause; SQLite public domain — https://github.com/xerial/sqlite-jdbc |

The legacy `lib/` directory is retained for provenance review and is not used by Maven or packaged as a directory. SMTP libraries are no longer dependencies. This notice is not an advisory scan; provenance hashes and security scan results must be recorded before release.
