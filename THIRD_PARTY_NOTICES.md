# Third-party notices

The ETT HR portal is built on open-source components. Every component below is used unmodified,
under a licence that allows commercial use and distribution as part of a proprietary product.
This file lists them with their licences. Keep it with every copy of the software you ship.

## Licence summary

| Licence | What it asks of you |
| --- | --- |
| Apache-2.0 (most of the backend) | Keep the licence and any NOTICE files. The NOTICE files travel inside the shipped jars. |
| MIT, ISC, BSD-2/3-Clause, 0BSD, EDL-1.0, Bouncy Castle | Keep the copyright notice and licence text. |
| EPL-2.0 (Logback, AspectJ, Jakarta APIs) | Used as unmodified binaries. Source is available from each project; if you ever modify one of them, publish those changes. |
| Public domain (CC0) | No conditions. |
| OFL-1.1 (IBM Plex Sans, IBM Plex Mono, Source Serif 4 fonts) | The fonts may be bundled and used freely, but not sold on their own. |

Components offered under a choice of licences are used under the most permissive option:

- **Logback** (EPL-2.0 or LGPL-2.1): used under EPL-2.0.
- **Jakarta Mail, Annotations and Transaction APIs, Angus Mail** (EPL-2.0, GPL-2.0 with Classpath Exception, or EDL-1.0): used under EPL-2.0 or EDL-1.0.

No component is used under a GPL or AGPL licence.

Runtime platforms are not bundled into the application and keep their own licences: Eclipse Temurin
Java 25 runtime (GPL-2.0 with Classpath Exception), PostgreSQL 18 (PostgreSQL Licence), nginx 1.30
(BSD-2-Clause) and Mailpit (MIT, local testing only). Node.js is used only to build the web app.

To regenerate the backend list, run
`mvn org.codehaus.mojo:license-maven-plugin:2.4.0:add-third-party -Dlicense.excludedScopes=test,provided,system`
in `backend/`.

## Backend (Java), shipped inside the application jar

| Component | Artifact | Version | Licence |
| --- | --- | --- | --- |
| Logback Classic Module | `ch.qos.logback:logback-classic` | 1.5.38 | EPL-2.0 or LGPL-2.1 |
| Logback Core Module | `ch.qos.logback:logback-core` | 1.5.38 | EPL-2.0 or LGPL-2.1 |
| Jackson-annotations | `com.fasterxml.jackson.core:jackson-annotations` | 2.21 | Apache-2.0 |
| Jackson-core | `com.fasterxml.jackson.core:jackson-core` | 2.21.7 | Apache-2.0 |
| jackson-databind | `com.fasterxml.jackson.core:jackson-databind` | 2.21.7 | Apache-2.0 |
| Jackson-dataformat-YAML | `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | 2.21.7 | Apache-2.0 |
| Jackson datatype: JSR310 | `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.21.7 | Apache-2.0 |
| ClassMate | `com.fasterxml:classmate` | 1.7.3 | Apache-2.0 |
| curvesapi | `com.github.virtuald:curvesapi` | 1.08 | BSD |
| Jackcess | `com.healthmarketscience.jackcess:jackcess` | 5.0.4 | Apache-2.0 |
| Jackcess Encrypt | `com.healthmarketscience.jackcess:jackcess-encrypt` | 5.0.2 | Apache-2.0 |
| java-libpst | `com.pff:java-libpst` | 0.9.3 | Apache-2.0 |
| istack common utility code runtime | `com.sun.istack:istack-commons-runtime` | 4.1.2 | EDL-1.0 (BSD-3-Clause) |
| HikariCP | `com.zaxxer:HikariCP` | 7.0.2 | Apache-2.0 |
| SparseBitSet | `com.zaxxer:SparseBitSet` | 1.3 | Apache-2.0 |
| Apache Commons Codec | `commons-codec:commons-codec` | 1.21.0 | Apache-2.0 |
| Apache Commons IO | `commons-io:commons-io` | 2.22.0 | Apache-2.0 |
| Apache Commons Logging | `commons-logging:commons-logging` | 1.3.6 | Apache-2.0 |
| picocli | `info.picocli:picocli` | 4.7.7 | Apache-2.0 |
| micrometer-commons | `io.micrometer:micrometer-commons` | 1.17.2 | Apache-2.0 |
| micrometer-core | `io.micrometer:micrometer-core` | 1.17.2 | Apache-2.0 |
| micrometer-jakarta9 | `io.micrometer:micrometer-jakarta9` | 1.17.2 | Apache-2.0 |
| micrometer-observation | `io.micrometer:micrometer-observation` | 1.17.2 | Apache-2.0 |
| swagger-annotations-jakarta | `io.swagger.core.v3:swagger-annotations-jakarta` | 2.2.55 | Apache-2.0 |
| swagger-core-jakarta | `io.swagger.core.v3:swagger-core-jakarta` | 2.2.55 | Apache-2.0 |
| swagger-models-jakarta | `io.swagger.core.v3:swagger-models-jakarta` | 2.2.55 | Apache-2.0 |
| Jakarta Activation API | `jakarta.activation:jakarta.activation-api` | 2.1.4 | EDL-1.0 (BSD-3-Clause) |
| Jakarta Annotations API | `jakarta.annotation:jakarta.annotation-api` | 3.0.0 | EPL-2.0 or GPL-2.0 with Classpath Exception |
| Jakarta Dependency Injection | `jakarta.inject:jakarta.inject-api` | 2.0.1 | Apache-2.0 |
| Jakarta Mail API | `jakarta.mail:jakarta.mail-api` | 2.1.5 | EDL-1.0 (BSD-3-Clause) or EPL-2.0 or GPL-2.0 with Classpath Exception |
| Jakarta Persistence API | `jakarta.persistence:jakarta.persistence-api` | 3.2.0 | EDL-1.0 (BSD-3-Clause) or EPL-2.0 |
| jakarta.transaction API | `jakarta.transaction:jakarta.transaction-api` | 2.0.1 | EPL-2.0 or GPL-2.0 with Classpath Exception |
| Jakarta Validation API | `jakarta.validation:jakarta.validation-api` | 3.1.1 | Apache-2.0 |
| Jakarta XML Binding API | `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.5 | EDL-1.0 (BSD-3-Clause) |
| Byte Buddy (without dependencies) | `net.bytebuddy:byte-buddy` | 1.18.15 | Apache-2.0 |
| ANTLR 4 Runtime | `org.antlr:antlr4-runtime` | 4.13.2 | BSD-3-Clause |
| Apache Commons Collections | `org.apache.commons:commons-collections4` | 4.5.0 | Apache-2.0 |
| Apache Commons Compress | `org.apache.commons:commons-compress` | 1.28.0 | Apache-2.0 |
| Apache Commons CSV | `org.apache.commons:commons-csv` | 1.14.1 | Apache-2.0 |
| Apache Commons Lang | `org.apache.commons:commons-lang3` | 3.20.0 | Apache-2.0 |
| Apache Commons Math | `org.apache.commons:commons-math3` | 3.6.1 | Apache-2.0 |
| Apache Commons Secure XML | `org.apache.commons:commons-secure-xml` | 1.1.0 | Apache-2.0 |
| Apache James :: Mime4j :: Core | `org.apache.james:apache-mime4j-core` | 0.8.15 | Apache-2.0 |
| Apache James :: Mime4j :: DOM | `org.apache.james:apache-mime4j-dom` | 0.8.15 | Apache-2.0 |
| Apache Log4j API | `org.apache.logging.log4j:log4j-api` | 2.25.5 | Apache-2.0 |
| Log4j API to SLF4J Adapter | `org.apache.logging.log4j:log4j-to-slf4j` | 2.25.5 | Apache-2.0 |
| Apache FontBox | `org.apache.pdfbox:fontbox` | 3.0.8 | Apache-2.0 |
| Apache PDFBox | `org.apache.pdfbox:pdfbox` | 3.0.8 | Apache-2.0 |
| Apache PDFBox io | `org.apache.pdfbox:pdfbox-io` | 3.0.8 | Apache-2.0 |
| Apache PDFBox tools | `org.apache.pdfbox:pdfbox-tools` | 3.0.8 | Apache-2.0 |
| Apache POI - Common | `org.apache.poi:poi` | 5.5.1 | Apache-2.0 |
| Apache POI - API based on OPC and OOXML schemas | `org.apache.poi:poi-ooxml` | 5.5.1 | Apache-2.0 |
| Apache POI | `org.apache.poi:poi-scratchpad` | 5.5.1 | Apache-2.0 |
| Apache Tika core | `org.apache.tika:tika-core` | 4.2.0 | Apache-2.0 |
| Apache Tika ML charset encoding detector | `org.apache.tika:tika-encoding-detector-mojibuster` | 4.2.0 | Apache-2.0 |
| Apache Tika ML core (no Tika dependencies) | `org.apache.tika:tika-ml-core` | 4.2.0 | Apache-2.0 |
| Apache Tika data URI commons | `org.apache.tika:tika-parser-datauri-commons` | 4.2.0 | Apache-2.0 |
| Apache Tika html parser module | `org.apache.tika:tika-parser-html-module` | 4.2.0 | Apache-2.0 |
| Apache Tika Microsoft parser module | `org.apache.tika:tika-parser-microsoft-module` | 4.2.0 | Apache-2.0 |
| Apache Tika PDF parser module | `org.apache.tika:tika-parser-pdf-module` | 4.2.0 | Apache-2.0 |
| Apache Tika text parser module | `org.apache.tika:tika-parser-text-module` | 4.2.0 | Apache-2.0 |
| Apache Tika XML parser module | `org.apache.tika:tika-parser-xml-module` | 4.2.0 | Apache-2.0 |
| Apache Tika XMP commons | `org.apache.tika:tika-parser-xmp-commons` | 4.2.0 | Apache-2.0 |
| Apache Tika ZIP commons | `org.apache.tika:tika-parser-zip-commons` | 4.2.0 | Apache-2.0 |
| tomcat-embed-core | `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.27 | Apache-2.0 |
| tomcat-embed-el | `org.apache.tomcat.embed:tomcat-embed-el` | 11.0.27 | Apache-2.0 |
| tomcat-embed-websocket | `org.apache.tomcat.embed:tomcat-embed-websocket` | 11.0.27 | Apache-2.0 |
| XmlBeans | `org.apache.xmlbeans:xmlbeans` | 5.3.0 | Apache-2.0 |
| AspectJ Weaver | `org.aspectj:aspectjweaver` | 1.9.25.1 | EPL-2.0 |
| attoparser | `org.attoparser:attoparser` | 2.0.7.RELEASE | Apache-2.0 |
| Bouncy Castle JavaMail Jakarta S/MIME APIs | `org.bouncycastle:bcjmail-jdk18on` | 1.86 | Bouncy Castle (MIT-style) |
| Bouncy Castle PKIX, CMS, EAC, TSP, PKCS, OCSP, CMP, and CRMF APIs | `org.bouncycastle:bcpkix-jdk18on` | 1.86 | Bouncy Castle (MIT-style) |
| Bouncy Castle Provider | `org.bouncycastle:bcprov-jdk18on` | 1.86 | Bouncy Castle (MIT-style) |
| Bouncy Castle ASN.1 Extension and Utility APIs | `org.bouncycastle:bcutil-jdk18on` | 1.86 | Bouncy Castle (MIT-style) |
| Checker Qual | `org.checkerframework:checker-qual` | 3.55.1 | MIT |
| commonmark-java core | `org.commonmark:commonmark` | 0.31.0 | BSD-2-Clause |
| commonmark-java extension for strikethrough | `org.commonmark:commonmark-ext-gfm-strikethrough` | 0.31.0 | BSD-2-Clause |
| commonmark-java extension for tables | `org.commonmark:commonmark-ext-gfm-tables` | 0.31.0 | BSD-2-Clause |
| Angus Activation Registries | `org.eclipse.angus:angus-activation` | 2.0.3 | EDL-1.0 (BSD-3-Clause) |
| Angus Mail Provider | `org.eclipse.angus:angus-mail` | 2.0.5 | EDL-1.0 (BSD-3-Clause) or EPL-2.0 or GPL-2.0 with Classpath Exception |
| flyway-core | `org.flywaydb:flyway-core` | 12.4.0 | Apache-2.0 |
| flyway-database-postgresql | `org.flywaydb:flyway-database-postgresql` | 12.4.0 | Apache-2.0 |
| JAXB Core | `org.glassfish.jaxb:jaxb-core` | 4.0.9 | EDL-1.0 (BSD-3-Clause) |
| JAXB Runtime | `org.glassfish.jaxb:jaxb-runtime` | 4.0.9 | EDL-1.0 (BSD-3-Clause) |
| TXW2 Runtime | `org.glassfish.jaxb:txw2` | 4.0.9 | EDL-1.0 (BSD-3-Clause) |
| HdrHistogram | `org.hdrhistogram:HdrHistogram` | 2.2.2 | BSD-2-Clause or Public Domain, per Creative Commons CC0 |
| Hibernate Models | `org.hibernate.models:hibernate-models` | 1.1.2 | Apache-2.0 |
| Hibernate ORM - hibernate-core | `org.hibernate.orm:hibernate-core` | 7.4.13.Final | Apache-2.0 |
| Hibernate Validator Engine | `org.hibernate.validator:hibernate-validator` | 9.1.5.Final | Apache-2.0 |
| JBoss Logging 3 | `org.jboss.logging:jboss-logging` | 3.6.3.Final | Apache-2.0 |
| jsoup Java HTML Parser | `org.jsoup:jsoup` | 1.23.2 | MIT |
| JSpecify annotations | `org.jspecify:jspecify` | 1.0.1 | Apache-2.0 |
| PostgreSQL JDBC Driver | `org.postgresql:postgresql` | 42.7.14 | BSD-2-Clause |
| JUL to SLF4J bridge | `org.slf4j:jul-to-slf4j` | 2.0.20 | MIT |
| SLF4J API Module | `org.slf4j:slf4j-api` | 2.0.20 | MIT |
| springdoc-openapi-starter-common | `org.springdoc:springdoc-openapi-starter-common` | 3.2.0 | Apache-2.0 |
| springdoc-openapi-starter-webmvc-api | `org.springdoc:springdoc-openapi-starter-webmvc-api` | 3.2.0 | Apache-2.0 |
| springdoc-openapi-starter-webmvc-ui | `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 3.2.0 | Apache-2.0 |
| spring-boot | `org.springframework.boot:spring-boot` | 4.1.2 | Apache-2.0 |
| spring-boot-actuator | `org.springframework.boot:spring-boot-actuator` | 4.1.2 | Apache-2.0 |
| spring-boot-actuator-autoconfigure | `org.springframework.boot:spring-boot-actuator-autoconfigure` | 4.1.2 | Apache-2.0 |
| spring-boot-autoconfigure | `org.springframework.boot:spring-boot-autoconfigure` | 4.1.2 | Apache-2.0 |
| spring-boot-data-commons | `org.springframework.boot:spring-boot-data-commons` | 4.1.2 | Apache-2.0 |
| spring-boot-data-jpa | `org.springframework.boot:spring-boot-data-jpa` | 4.1.2 | Apache-2.0 |
| spring-boot-flyway | `org.springframework.boot:spring-boot-flyway` | 4.1.2 | Apache-2.0 |
| spring-boot-health | `org.springframework.boot:spring-boot-health` | 4.1.2 | Apache-2.0 |
| spring-boot-hibernate | `org.springframework.boot:spring-boot-hibernate` | 4.1.2 | Apache-2.0 |
| spring-boot-http-converter | `org.springframework.boot:spring-boot-http-converter` | 4.1.2 | Apache-2.0 |
| spring-boot-jackson | `org.springframework.boot:spring-boot-jackson` | 4.1.2 | Apache-2.0 |
| spring-boot-jdbc | `org.springframework.boot:spring-boot-jdbc` | 4.1.2 | Apache-2.0 |
| spring-boot-jpa | `org.springframework.boot:spring-boot-jpa` | 4.1.2 | Apache-2.0 |
| spring-boot-mail | `org.springframework.boot:spring-boot-mail` | 4.1.2 | Apache-2.0 |
| spring-boot-micrometer-metrics | `org.springframework.boot:spring-boot-micrometer-metrics` | 4.1.2 | Apache-2.0 |
| spring-boot-micrometer-observation | `org.springframework.boot:spring-boot-micrometer-observation` | 4.1.2 | Apache-2.0 |
| spring-boot-persistence | `org.springframework.boot:spring-boot-persistence` | 4.1.2 | Apache-2.0 |
| spring-boot-security | `org.springframework.boot:spring-boot-security` | 4.1.2 | Apache-2.0 |
| spring-boot-servlet | `org.springframework.boot:spring-boot-servlet` | 4.1.2 | Apache-2.0 |
| spring-boot-sql | `org.springframework.boot:spring-boot-sql` | 4.1.2 | Apache-2.0 |
| spring-boot-starter | `org.springframework.boot:spring-boot-starter` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-actuator | `org.springframework.boot:spring-boot-starter-actuator` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-data-jpa | `org.springframework.boot:spring-boot-starter-data-jpa` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-flyway | `org.springframework.boot:spring-boot-starter-flyway` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-jackson | `org.springframework.boot:spring-boot-starter-jackson` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-jdbc | `org.springframework.boot:spring-boot-starter-jdbc` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-logging | `org.springframework.boot:spring-boot-starter-logging` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-mail | `org.springframework.boot:spring-boot-starter-mail` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-micrometer-metrics | `org.springframework.boot:spring-boot-starter-micrometer-metrics` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-security | `org.springframework.boot:spring-boot-starter-security` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-thymeleaf | `org.springframework.boot:spring-boot-starter-thymeleaf` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-tomcat | `org.springframework.boot:spring-boot-starter-tomcat` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-tomcat-runtime | `org.springframework.boot:spring-boot-starter-tomcat-runtime` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-validation | `org.springframework.boot:spring-boot-starter-validation` | 4.1.2 | Apache-2.0 |
| spring-boot-starter-webmvc | `org.springframework.boot:spring-boot-starter-webmvc` | 4.1.2 | Apache-2.0 |
| spring-boot-thymeleaf | `org.springframework.boot:spring-boot-thymeleaf` | 4.1.2 | Apache-2.0 |
| spring-boot-tomcat | `org.springframework.boot:spring-boot-tomcat` | 4.1.2 | Apache-2.0 |
| spring-boot-transaction | `org.springframework.boot:spring-boot-transaction` | 4.1.2 | Apache-2.0 |
| spring-boot-validation | `org.springframework.boot:spring-boot-validation` | 4.1.2 | Apache-2.0 |
| spring-boot-web-server | `org.springframework.boot:spring-boot-web-server` | 4.1.2 | Apache-2.0 |
| spring-boot-webmvc | `org.springframework.boot:spring-boot-webmvc` | 4.1.2 | Apache-2.0 |
| Spring Data Core | `org.springframework.data:spring-data-commons` | 4.1.2 | Apache-2.0 |
| Spring Data JPA | `org.springframework.data:spring-data-jpa` | 4.1.2 | Apache-2.0 |
| spring-security-config | `org.springframework.security:spring-security-config` | 7.1.2 | Apache-2.0 |
| spring-security-core | `org.springframework.security:spring-security-core` | 7.1.2 | Apache-2.0 |
| spring-security-crypto | `org.springframework.security:spring-security-crypto` | 7.1.2 | Apache-2.0 |
| spring-security-web | `org.springframework.security:spring-security-web` | 7.1.2 | Apache-2.0 |
| Spring AOP | `org.springframework:spring-aop` | 7.0.10 | Apache-2.0 |
| Spring Aspects | `org.springframework:spring-aspects` | 7.0.10 | Apache-2.0 |
| Spring Beans | `org.springframework:spring-beans` | 7.0.10 | Apache-2.0 |
| Spring Context | `org.springframework:spring-context` | 7.0.10 | Apache-2.0 |
| Spring Context Support | `org.springframework:spring-context-support` | 7.0.10 | Apache-2.0 |
| Spring Core | `org.springframework:spring-core` | 7.0.10 | Apache-2.0 |
| Spring Expression Language (SpEL) | `org.springframework:spring-expression` | 7.0.10 | Apache-2.0 |
| Spring JDBC | `org.springframework:spring-jdbc` | 7.0.10 | Apache-2.0 |
| Spring Object/Relational Mapping | `org.springframework:spring-orm` | 7.0.10 | Apache-2.0 |
| Spring Transaction | `org.springframework:spring-tx` | 7.0.10 | Apache-2.0 |
| Spring Web | `org.springframework:spring-web` | 7.0.10 | Apache-2.0 |
| Spring Web MVC | `org.springframework:spring-webmvc` | 7.0.10 | Apache-2.0 |
| thymeleaf | `org.thymeleaf:thymeleaf` | 3.1.5.RELEASE | Apache-2.0 |
| thymeleaf-spring6 | `org.thymeleaf:thymeleaf-spring6` | 3.1.5.RELEASE | Apache-2.0 |
| unbescape | `org.unbescape:unbescape` | 1.1.6.RELEASE | Apache-2.0 |
| Swagger UI | `org.webjars:swagger-ui` | 5.34.0 | Apache-2.0 |
| webjars-locator-lite | `org.webjars:webjars-locator-lite` | 1.1.5 | MIT |
| SnakeYAML | `org.yaml:snakeyaml` | 2.6 | Apache-2.0 |
| Jackson-core | `tools.jackson.core:jackson-core` | 3.1.7 | Apache-2.0 |
| jackson-databind | `tools.jackson.core:jackson-databind` | 3.1.7 | Apache-2.0 |

## Data files, shipped inside the application jar

`backend/src/main/resources/geo/in-pincodes.csv` (India PIN codes with their post office town, district, state and
map position) was built from two packages and is used to place candidates on the map offline:

| Data | Source | Licence |
| --- | --- | --- |
| PIN code coordinates | pincode-lat-long 1.0.3, by ramnithik | ISC |
| PIN code town, district and state | india-pincode-search 1.0.2, by Mayank Jhawar, from the All India Pincode Directory published on data.gov.in | ISC; the directory itself under the Government Open Data Licence – India |

ISC licence (both packages): Permission to use, copy, modify, and/or distribute this software for any purpose with or
without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all
copies. THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE.

The Government Open Data Licence – India allows use, adaptation and redistribution, including commercially, with
attribution: *Contains data from the All India Pincode Directory, data.gov.in, Government of India.*

## Portable edition only

The portable edition (`packaging/build-portable.sh`) adds these to the application jar and the downloads:

| Component | Artifact | Version | Licence |
| --- | --- | --- | --- |
| Embedded PostgreSQL launcher | `io.zonky.test:embedded-postgres` | 2.2.2 | Apache-2.0 |
| XZ for Java | `org.tukaani:xz` | 1.11 | 0BSD |
| PostgreSQL server binaries | `io.zonky.test.postgres:embedded-postgres-binaries-*` | 18.6.0 | PostgreSQL Licence (BSD-style); the bundled libraries keep their own permissive licences |
| Launch4j launcher stub (inside `ETT HR Portal.exe`) | `net.sf.launch4j:launch4j` | 3.50 | MIT (launcher stub); the Launch4j build tool itself is BSD-3-Clause and is not shipped |

## Web app (JavaScript), shipped inside the built web files

| Package | Version | Licence |
| --- | --- | --- |
| @babel/runtime | 7.29.10 | MIT |
| @floating-ui/core | 1.8.0 | MIT |
| @floating-ui/dom | 1.8.0 | MIT |
| @floating-ui/react | 0.27.20 | MIT |
| @floating-ui/react-dom | 2.1.9 | MIT |
| @floating-ui/utils | 0.2.12 | MIT |
| @fontsource/ibm-plex-mono | 5.3.0 | OFL-1.1 |
| @fontsource/ibm-plex-sans | 5.3.0 | OFL-1.1 |
| @fontsource/source-serif-4 | 5.3.1 | OFL-1.1 |
| @mantine/core | 9.7.5 | MIT |
| @mantine/dates | 9.7.5 | MIT |
| @mantine/dropzone | 9.7.5 | MIT |
| @mantine/form | 9.7.5 | MIT |
| @mantine/hooks | 9.7.5 | MIT |
| @mantine/notifications | 9.7.5 | MIT |
| @mantine/store | 9.7.5 | MIT |
| @remix-run/route-pattern | 0.22.1 | MIT |
| @standard-schema/spec | 1.1.0 | MIT |
| @tabler/icons | 3.50.0 | MIT |
| @tabler/icons-react | 3.50.0 | MIT |
| @tanstack/query-core | 5.105.3 | MIT |
| @tanstack/react-query | 5.105.3 | MIT |
| attr-accept | 4.0.1 | MIT |
| clsx | 2.1.1 | MIT |
| cookie-es | 3.1.1 | MIT |
| csstype | 3.2.3 | MIT |
| dayjs | 1.11.23 | MIT |
| detect-node-es | 1.1.0 | MIT |
| dom-helpers | 5.2.1 | MIT |
| fast-deep-equal | 3.1.3 | MIT |
| file-selector | 5.0.2 | MIT |
| get-nonce | 1.0.1 | MIT |
| js-tokens | 4.0.0 | MIT |
| klona | 2.0.6 | MIT |
| loose-envify | 1.4.0 | MIT |
| object-assign | 4.1.1 | MIT |
| prop-types | 15.8.1 | MIT |
| qrcode.react | 4.2.0 | ISC |
| react | 19.3.1 | MIT |
| react-dom | 19.3.1 | MIT |
| react-dropzone | 20.1.2 | MIT |
| react-is | 16.13.1 | MIT |
| react-number-format | 5.4.5 | MIT |
| react-remove-scroll | 2.7.2 | MIT |
| react-remove-scroll-bar | 2.3.8 | MIT |
| react-router | 8.5.1 | MIT |
| react-style-singleton | 2.2.3 | MIT |
| react-transition-group | 4.4.5 | BSD-3-Clause |
| scheduler | 0.28.0 | MIT |
| tabbable | 6.5.1 | MIT |
| tagged-tag | 1.0.0 | MIT |
| tslib | 2.9.1 | 0BSD |
| type-fest | 5.10.0 | (MIT OR CC0-1.0) |
| use-callback-ref | 1.3.3 | MIT |
| use-sidecar | 1.1.3 | MIT |
