/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.build;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.h2.build.doc.XMLParser;

/**
 * The build definition.
 */
public class Build extends BuildBase {

    private static final String ASM_VERSION = "9.5";

    private static final String ARGS4J_VERSION = "2.33";

    private static final String DERBY_VERSION = "10.15.2.0";

    private static final String HSQLDB_VERSION = "2.7.2";

    private static final String JACOCO_VERSION = "0.8.10";

    private static final String JTS_VERSION = "1.19.0";

    private static final String JUNIT_VERSION = "5.10.0";

    private static final String LUCENE_VERSION = "9.7.0";

    private static final String MYSQL_CONNECTOR_VERSION = "8.1.0";

    private static final String OSGI_VERSION = "5.0.0";

    private static final String OSGI_JDBC_VERSION = "1.1.0";

    private static final String PGJDBC_VERSION = "42.7.2";

    private static final String PGJDBC_HASH = "86ed42574cd68662b05d3b00432a34e9a34cb12c";

    private static final String JAVAX_SERVLET_VERSION = "4.0.1";

    private static final String JAKARTA_SERVLET_VERSION = "5.0.0";

    private static final String SLF4J_VERSION = "2.0.7";

    private static final String APIGUARDIAN_VERSION = "1.1.2";

    private static final String SQLITE_VERSION = "3.36.0.3";

    private static final String NASHORN_VERSION = "15.4";

    private boolean filesMissing;

    /**
     * Run the build.
     *
     * @param args the command line arguments
     */
    public static void main(String... args) {
        new Build().run(args);
    }

    /**
     * Run the benchmarks.
     */
    @Description(summary = "Run the benchmarks.")
    public void benchmark() {
        downloadUsingMaven("ext/hsqldb-" + HSQLDB_VERSION + ".jar",
                "org.hsqldb", "hsqldb", HSQLDB_VERSION,
                "d92d4d2aa515714da2165c9d640d584c2896c9df");
        downloadUsingMaven("ext/derby-" + DERBY_VERSION + ".jar",
                "org.apache.derby", "derby", DERBY_VERSION,
                "b64da6681994f33ba5783ffae55cdb44885b9e70");
        downloadUsingMaven("ext/derbyclient-" + DERBY_VERSION + ".jar",
                "org.apache.derby", "derbyclient", DERBY_VERSION,
                "60ad423e9d7acba99a13b8684927206e94c31e03");
        downloadUsingMaven("ext/derbynet-" + DERBY_VERSION + ".jar",
                "org.apache.derby", "derbynet", DERBY_VERSION,
                "072c8fb0870227477b64edb2d7a5eccdac5de2af");
        downloadUsingMaven("ext/derbyshared-" + DERBY_VERSION + ".jar",
                "org.apache.derby", "derbyshared", DERBY_VERSION,
                "ff2dfb3e2a92d593cf111baad242d156947abbc1");
        downloadUsingMaven("ext/postgresql-" + PGJDBC_VERSION + ".jar",
                "org.postgresql", "postgresql", PGJDBC_VERSION, PGJDBC_HASH);
        downloadUsingMaven("ext/mysql-connector-j-" + MYSQL_CONNECTOR_VERSION + ".jar",
                "com.mysql", "mysql-connector-j", MYSQL_CONNECTOR_VERSION,
                "3f78d2963935f44a61edb3961a591cdc392c8941");
        downloadUsingMaven("ext/sqlite-" + SQLITE_VERSION + ".jar",
            "org.xerial", "sqlite-jdbc", SQLITE_VERSION, "7fa71c4dfab806490cb909714fb41373ec552c29");
        compile();

        String cp = "temp" +
                File.pathSeparator + "bin/h2" + getJarSuffix() +
                File.pathSeparator + "ext/hsqldb-" + HSQLDB_VERSION + ".jar" +
                File.pathSeparator + "ext/derby-" + DERBY_VERSION + ".jar" +
                File.pathSeparator + "ext/derbyclient-" + DERBY_VERSION + ".jar" +
                File.pathSeparator + "ext/derbynet-" + DERBY_VERSION + ".jar" +
                File.pathSeparator + "ext/derbyshared-" + DERBY_VERSION + ".jar" +
                File.pathSeparator + "ext/postgresql-" + PGJDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/mysql-connector-j-" + MYSQL_CONNECTOR_VERSION + ".jar" +
                File.pathSeparator + "ext/sqlite-" + SQLITE_VERSION + ".jar";
        StringList args = args("-Xmx128m",
                "-cp", cp, "-Dderby.system.durability=test", "org.h2.test.bench.TestPerformance");
        execJava(args.plus("-init", "-db", "1"));
        execJava(args.plus("-db", "2"));
        execJava(args.plus("-db", "3", "-out", "pe.html"));
        execJava(args.plus("-init", "-db", "4"));
        execJava(args.plus("-db", "5", "-exit"));
        execJava(args.plus("-db", "6"));
        execJava(args.plus("-db", "7"));
        execJava(args.plus("-db", "8", "-out", "ps.html"));
        // Disable SQLite because it doesn't work with multi-threaded benchmark, BenchB
        // execJava(args.plus("-db", "9"));
    }

    /**
     * Clean all jar files, classes, and generated documentation.
     */
    @Description(summary = "Clean all jar files, classes, and generated documentation.")
    public void clean() {
        delete("temp");
        delete("docs");
        mkdir("docs");
        mkdir("bin");
        delete(files(".").keep("*/Thumbs.db"));
    }

    /**
     * Compile all classes.
     */
    @Description(summary = "Compile all classes.")
    public void compile() {
        clean();
        mkdir("temp");
        download();
        String classpath = "temp" +
                File.pathSeparator + "ext/javax.servlet-api-" + JAVAX_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/jakarta.servlet-api-" + JAKARTA_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/slf4j-api-" + SLF4J_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar" +
                File.pathSeparator + "ext/asm-" + ASM_VERSION + ".jar" +
                File.pathSeparator + javaToolsJar;
        FileList files = files("src/main");
        StringList args = args("-Xlint:unchecked", "-d", "temp", "-sourcepath", "src/main", "-classpath", classpath);
        String version = getTargetJavaVersion();
        if (version != null) {
            args = args.plus("-target", version, "-source", version);
        }
        javac(args, files);

        files = files("src/main/META-INF/native-image");
        files.addAll(files("src/main/META-INF/services"));
        copy("temp", files, "src/main");

        files = files("src/test");
        files.addAll(files("src/tools"));
        // we don't use Junit for this test framework
        files = files.exclude("src/test/org/h2/test/TestAllJunit.java");
        args = args("-Xlint:unchecked", "-Xlint:deprecation",
                "-d", "temp", "-sourcepath", "src/test" + File.pathSeparator + "src/tools",
                "-classpath", classpath);
        if (version != null) {
            args = args.plus("-target", version, "-source", version);
        }
        javac(args, files);

        files = files("src/test").
            exclude("*.java").
            exclude("*/package.html");
        copy("temp", files, "src/test");

        javadoc("-sourcepath", "src/main",
                "-d", "docs/javadoc",
                "org.h2.tools", "org.h2.jmx",
                "-classpath",
                "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar");

        files = files("src/main").
            exclude("*.MF").
            exclude("*.java").
            exclude("*/package.html").
            exclude("*/java.sql.Driver").
            exclude("*.DS_Store");
        zip("temp/org/h2/util/data.zip", files, "src/main", true, false);
    }

    private void compileTools() {
        mkdir("temp");
        FileList files = files("src/tools").keep("src/tools/org/h2/build/*");
        StringList args = args("-d", "temp", "-sourcepath", "src/tools" +
                File.pathSeparator + "src/test" +
                File.pathSeparator + "src/main");
        String version = getTargetJavaVersion();
        if (version != null) {
            args = args.plus("-target", version, "-source", version);
        }
        javac(args, files);
    }

    /**
     * Run the JaCoco code coverage.
     */
    @Description(summary = "Run the JaCoco code coverage.")
    public void coverage() {
        compile();
        downloadTest();
        downloadUsingMaven("ext/org.jacoco.agent-" + JACOCO_VERSION + ".jar",
                "org.jacoco", "org.jacoco.agent", JACOCO_VERSION,
                "ffdd953dfe502cd7678743c75905bc3304ae2eb7");
        URI uri = URI.create("jar:"
                + Paths.get("ext/org.jacoco.agent-" + JACOCO_VERSION + ".jar").toAbsolutePath().toUri());
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Files.copy(fs.getPath("jacocoagent.jar"), Paths.get("ext/jacocoagent.jar"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        downloadUsingMaven("ext/org.jacoco.cli-" + JACOCO_VERSION + ".jar",
                "org.jacoco", "org.jacoco.cli", JACOCO_VERSION,
                "b2e14234f8ab0c72d9ed599d2f01d21f453fecc0");
        downloadUsingMaven("ext/org.jacoco.core-" + JACOCO_VERSION + ".jar",
                "org.jacoco", "org.jacoco.core", JACOCO_VERSION,
                "669a338279c3f40b154a64c624bab625664a00e6");
        downloadUsingMaven("ext/org.jacoco.report-" + JACOCO_VERSION + ".jar",
                "org.jacoco", "org.jacoco.report", JACOCO_VERSION,
                "c361019431d1c88e7004ba5a722e7c3f7c22194b");
        downloadUsingMaven("ext/args4j-" + ARGS4J_VERSION + ".jar",
                "args4j", "args4j", ARGS4J_VERSION,
                "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9");

        delete(files("coverage"));
        // Use own copy
        copy("coverage/bin", files("temp"), "temp");
        // JaCoCo does not support multiple versions of the same classes
        delete(files("coverage/bin/META-INF/versions"));
        String cp = "coverage/bin" +
            File.pathSeparator + "ext/postgresql-" + PGJDBC_VERSION + ".jar" +
            File.pathSeparator + "ext/javax.servlet-api-" + JAVAX_SERVLET_VERSION + ".jar" +
            File.pathSeparator + "ext/jakarta.servlet-api-" + JAKARTA_SERVLET_VERSION + ".jar" +
            File.pathSeparator + "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
            File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
            File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
            File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
            File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
            File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar" +
            File.pathSeparator + "ext/slf4j-api-" + SLF4J_VERSION + ".jar" +
            File.pathSeparator + "ext/slf4j-nop-" + SLF4J_VERSION + ".jar" +
            File.pathSeparator + javaToolsJar;
        cp = addNashornJavaScriptEngineIfNecessary(cp);
        // Run tests
        execJava(args(
                "-Xmx128m",
                "-javaagent:ext/jacocoagent.jar=destfile=coverage/jacoco.exec,"
                        + "excludes=org.h2.test.*:org.h2.tools.*:org.h2.sample.*",
                "-cp", cp,
                "org.h2.test.TestAll", "codeCoverage"));
        // Remove classes that we don't want to include in report
        delete(files("coverage/bin/org/h2/test"));
        delete(files("coverage/bin/org/h2/tools"));
        delete(files("coverage/bin/org/h2/sample"));
        // Generate report
        execJava(args("-cp",
                "ext/org.jacoco.cli-" + JACOCO_VERSION + ".jar" + File.pathSeparator
                + "ext/org.jacoco.core-" + JACOCO_VERSION + ".jar" + File.pathSeparator
                + "ext/org.jacoco.report-" + JACOCO_VERSION + ".jar" + File.pathSeparator
                + "ext/asm-" + ASM_VERSION + ".jar" + File.pathSeparator
                + "ext/asm-commons-" + ASM_VERSION + ".jar" + File.pathSeparator
                + "ext/asm-tree-" + ASM_VERSION + ".jar" + File.pathSeparator
                + "ext/args4j-" + ARGS4J_VERSION + ".jar",
                "org.jacoco.cli.internal.Main", "report", "coverage/jacoco.exec",
                "--classfiles", "coverage/bin",
                "--html", "coverage/report", "--sourcefiles", "h2/src/main"));
        try {
            tryOpenCoverageInBrowser();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void tryOpenCoverageInBrowser() throws Exception {
        Class<?> desktop = Class.forName("java.awt.Desktop");
        Method m = desktop.getMethod("getDesktop");
        Object d = m.invoke(null);
        m = d.getClass().getMethod("open", File.class);
        m.invoke(d, new File("coverage/report/index.html"));
    }

    private static String getTargetJavaVersion() {
        return System.getProperty("version");
    }

    private void compileMVStore(boolean debugInfo) {
        clean();
        mkdir("temp");
        String classpath = "temp" +
            File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar";
        FileList files = files("src/main/org/h2/mvstore").
                exclude("src/main/org/h2/mvstore/db/*");
        StringList args = args();
        if (debugInfo) {
            args = args.plus("-Xlint:unchecked", "-d", "temp", "-sourcepath",
                    "src/main", "-classpath", classpath);
        } else {
            args = args.plus("-Xlint:unchecked", "-g:none", "-d", "temp",
                    "-sourcepath", "src/main", "-classpath", classpath);
        }
        String version = getTargetJavaVersion();
        if (version != null) {
            args = args.plus("-target", version, "-source", version);
        }
        javac(args, files);
    }

    private static void filter(String source, String target, String old,
            String replacement) {
        String text = new String(readFile(Paths.get(source)));
        text = replaceAll(text, old, replacement);
        writeFile(Paths.get(target), text.getBytes());
    }

    /**
     * Create the documentation from the documentation sources. API Javadocs are
     * created as well.
     */
    @Description(summary = "Create the documentation from sources (incl. API Javadocs).")
    public void docs() {
        javadoc();
        copy("docs", files("src/docsrc/index.html"), "src/docsrc");
        java("org.h2.build.doc.XMLChecker", null);
        java("org.h2.build.code.CheckJavadoc", null);
        java("org.h2.build.code.CheckTextFiles", null);
        java("org.h2.build.doc.GenerateDoc", null);
        java("org.h2.build.indexer.Indexer", null);
        java("org.h2.build.doc.MergeDocs", null);
        java("org.h2.build.doc.WebSite", null);
        java("org.h2.build.doc.LinkChecker", null);
        java("org.h2.build.doc.XMLChecker", null);
        java("org.h2.build.doc.SpellChecker", null);
        java("org.h2.build.code.CheckTextFiles", null);
        beep();
    }

    /**
     * Download all required jar files. Actually those are only compile time
     * dependencies. The database can be used without any dependencies.
     */
    @Description(summary = "Download all required jar files.")
    public void download() {
        downloadOrVerify(false);
    }

    private void downloadOrVerify(boolean offline) {
        downloadOrVerify("ext/javax.servlet-api-" + JAVAX_SERVLET_VERSION + ".jar",
                "javax/servlet", "javax.servlet-api", JAVAX_SERVLET_VERSION,
                "a27082684a2ff0bf397666c3943496c44541d1ca", offline);
        downloadOrVerify("ext/jakarta.servlet-api-" + JAKARTA_SERVLET_VERSION + ".jar",
                "jakarta/servlet", "jakarta.servlet-api", JAKARTA_SERVLET_VERSION,
                "2e6b8ccde55522c879434ddec3714683ccae6867", offline);
        downloadOrVerify("ext/lucene-core-" + LUCENE_VERSION + ".jar",
                "org/apache/lucene", "lucene-core", LUCENE_VERSION,
                "ad391210ffd806931334be9670a35af00c56f959", offline);
        downloadOrVerify("ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar",
                "org/apache/lucene", "lucene-analysis-common", LUCENE_VERSION,
                "27ba6caaa4587a982cd451f7217b5a982bcfc44a", offline);
        downloadOrVerify("ext/lucene-queryparser-" + LUCENE_VERSION + ".jar",
                "org/apache/lucene", "lucene-queryparser", LUCENE_VERSION,
                "6e77bde908ff698354e4a2149e6dd4658b56d7b0", offline);
        downloadOrVerify("ext/slf4j-api-" + SLF4J_VERSION + ".jar",
                "org/slf4j", "slf4j-api", SLF4J_VERSION,
                "41eb7184ea9d556f23e18b5cb99cad1f8581fc00", offline);
        downloadOrVerify("ext/org.osgi.core-" + OSGI_VERSION + ".jar",
                "org/osgi", "org.osgi.core", OSGI_VERSION,
                "6e5e8cd3c9059c08e1085540442a490b59a7783c", offline);
        downloadOrVerify("ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar",
                "org/osgi", "org.osgi.service.jdbc", OSGI_JDBC_VERSION,
                "07673601d60c98d876b82530ff4363ed9e428c1e", offline);
        downloadOrVerify("ext/jts-core-" + JTS_VERSION + ".jar",
                "org/locationtech/jts", "jts-core", JTS_VERSION,
                "3ff3baa0074445384f9e0068df81fbd0a168395a", offline);
        downloadOrVerify("ext/junit-jupiter-api-" + JUNIT_VERSION + ".jar",
                "org.junit.jupiter", "junit-jupiter-api", JUNIT_VERSION,
                "2fe4ba3d31d5067878e468c96aa039005a9134d3", offline);
        downloadUsingMaven("ext/asm-" + ASM_VERSION + ".jar",
                "org.ow2.asm", "asm", ASM_VERSION,
                "dc6ea1875f4d64fbc85e1691c95b96a3d8569c90");
        downloadUsingMaven("ext/apiguardian-" + APIGUARDIAN_VERSION + ".jar",
                "org.apiguardian", "apiguardian-api", APIGUARDIAN_VERSION,
                "a231e0d844d2721b0fa1b238006d15c6ded6842a");
    }

    private void downloadOrVerify(String target, String group, String artifact,
            String version, String sha1Checksum, boolean offline) {
        if (offline) {
            Path targetFile = Paths.get(target);
            if (Files.exists(targetFile)) {
                return;
            }
            println("Missing file: " + target);
            filesMissing = true;
        } else {
            downloadUsingMaven(target, group, artifact, version, sha1Checksum);
        }
    }

    private void downloadTest() {
        // for TestOldVersion
        downloadUsingMaven("ext/h2-1.2.127.jar",
                "com/h2database", "h2", "1.2.127",
                "056e784c7cf009483366ab9cd8d21d02fe47031a");
        // for TestPgServer
        downloadUsingMaven("ext/postgresql-" + PGJDBC_VERSION + ".jar",
                "org.postgresql", "postgresql", PGJDBC_VERSION, PGJDBC_HASH);
        // for TestTraceSystem
        downloadUsingMaven("ext/slf4j-nop-" + SLF4J_VERSION + ".jar",
                "org/slf4j", "slf4j-nop", SLF4J_VERSION,
                "a5b48a1a935615f0cc70148267bc0f6c4e437239");
        // for TestTriggersConstraints
        if (requiresNashornJavaScriptEngine()) {
            downloadUsingMaven("ext/nashorn-core-" + NASHORN_VERSION + ".jar",
                    "org/openjdk/nashorn", "nashorn-core", NASHORN_VERSION,
                    "f67f5ffaa5f5130cf6fb9b133da00c7df3b532a5");
            downloadUsingMaven("ext/asm-util-" + ASM_VERSION + ".jar",
                    "org.ow2.asm", "asm-util", ASM_VERSION,
                    "64b5a1fc8c1b15ed2efd6a063e976bc8d3dc5ffe");
        }
        downloadUsingMaven("ext/asm-commons-" + ASM_VERSION + ".jar",
                "org.ow2.asm", "asm-commons", ASM_VERSION,
                "19ab5b5800a3910d30d3a3e64fdb00fd0cb42de0");
        downloadUsingMaven("ext/asm-tree-" + ASM_VERSION + ".jar",
                "org.ow2.asm", "asm-tree", ASM_VERSION,
                "fd33c8b6373abaa675be407082fdfda35021254a");
    }

    private static String getVersion() {
        return getStaticField("org.h2.engine.Constants", "VERSION");
    }

    private static String getJarSuffix() {
        return "-" + getVersion() + ".jar";
    }

    /**
     * Create the h2.zip file and the Windows installer.
     */
    @Description(summary = "Create the h2.zip file and the Windows installer.")
    public void installer() {
        delete(files("bin").keep("*.jar"));
        jar();
        docs();
        try {
            exec("soffice", args("--invisible", "macro:///Standard.Module1.H2Pdf"));
            copy("docs", files("../h2web/h2.pdf"), "../h2web");
        } catch (Exception e) {
            println("OpenOffice / LibreOffice is not available or macros H2Pdf is not installed:");
            println(e.toString());
            println("********************************************************************************");
            println("Install and run LibreOffice or OpenOffice.");
            println("Open Tools - Macros - Organize Macros - LibreOffice Basic...");
            println("Navigate to My Macros / Standard / Module1 and press Edit button.");
            println("Put content of h2/src/installer/openoffice.txt here.");
            println("Edit BaseDir variable value:");

            println("    BaseDir = \"" + Paths.get(System.getProperty("user.dir")).getParent().toUri() + '"');
            println("Close office application and try to build installer again.");
            println("********************************************************************************");
        }
        delete("docs/html/onePage.html");
        FileList files = files("../h2").keep("../h2/build.*");
        files.addAll(files("../h2/bin").keep("../h2/bin/h2*"));
        files.addAll(files("../h2/docs").exclude("*.jar"));
        files.addAll(files("../h2/service"));
        files.addAll(files("../h2/src"));
        zip("../h2web/h2.zip", files, "../", false, false);
        boolean installer = false;
        try {
            exec("makensis", args(isWindows() ? "/V2" : "-V2", "src/installer/h2.nsi"));
            installer = true;
        } catch (Exception e) {
            println("NSIS is not available: " + e);
        }
        String buildDate = getStaticField("org.h2.engine.Constants", "BUILD_DATE");
        byte[] data = readFile(Paths.get("../h2web/h2.zip"));
        String sha1Zip = getSHA1(data), sha1Exe = null;
        writeFile(Paths.get("../h2web/h2-" + buildDate + ".zip"), data);
        if (installer) {
            data = readFile(Paths.get("../h2web/h2-setup.exe"));
            sha1Exe = getSHA1(data);
            writeFile(Paths.get("../h2web/h2-setup-" + buildDate + ".exe"), data);
        }
        updateChecksum("../h2web/html/download.html", sha1Zip, sha1Exe);
    }

    private static void updateChecksum(String fileName, String sha1Zip, String sha1Exe) {
        Path file = Paths.get(fileName);
        String checksums = new String(readFile(file));
        checksums = replaceAll(checksums, "<!-- sha1Zip -->",
                "(SHA1 checksum: " + sha1Zip + ")");
        if (sha1Exe != null) {
            checksums = replaceAll(checksums, "<!-- sha1Exe -->",
                    "(SHA1 checksum: " + sha1Exe + ")");
        }
        writeFile(file, checksums.getBytes());
    }

    private static String canonicalPath(Path file) {
        try {
            return file.toRealPath().toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private FileList excludeTestMetaInfFiles(FileList files) {
        FileList testMetaInfFiles = files("src/test/META-INF");
        int basePathLength = canonicalPath(Paths.get("src/test")).length();
        for (Path file : testMetaInfFiles) {
            files = files.exclude(canonicalPath(file).substring(basePathLength + 1));
        }
        return files;
    }

    /**
     * Add META-INF/versions for newer versions of Java.
     */
    private void addVersions() {
        copy("temp/META-INF/versions/21", files("src/java21/precompiled"), "src/java21/precompiled");
    }

    /**
     * Create the regular h2.jar file.
     */
    @Description(summary = "Create the regular h2.jar file.")
    public void jar() {
        compile();
        addVersions();
        manifest("src/main/META-INF/MANIFEST.MF");
        FileList files = files("temp").
            exclude("temp/org/h2/build/*").
            exclude("temp/org/h2/dev/*").
            exclude("temp/org/h2/jcr/*").
            exclude("temp/org/h2/java/*").
            exclude("temp/org/h2/jcr/*").
            exclude("temp/org/h2/samples/*").
            exclude("temp/org/h2/server/ftp/*").
            exclude("temp/org/h2/test/*").
            exclude("*.bat").
            exclude("*.sh").
            exclude("*.txt").
            exclude("*.DS_Store");
        files = excludeTestMetaInfFiles(files);
        jar("bin/h2" + getJarSuffix(), files, "temp");
        filter("src/installer/h2.sh", "bin/h2.sh", "h2.jar", "h2" + getJarSuffix());
        filter("src/installer/h2.bat", "bin/h2.bat", "h2.jar", "h2" + getJarSuffix());
        filter("src/installer/h2w.bat", "bin/h2w.bat", "h2.jar", "h2" + getJarSuffix());
    }

    /**
     * Create the file h2mvstore.jar. This only contains the MVStore.
     */
    @Description(summary = "Create h2mvstore.jar containing only the MVStore.")
    public void jarMVStore() {
        compileMVStore(true);
        addVersions();
        manifest("src/installer/mvstore/MANIFEST.MF");
        FileList files = files("temp");
        files.exclude("*.DS_Store");
        files = excludeTestMetaInfFiles(files);
        jar("bin/h2-mvstore" + getJarSuffix(), files, "temp");
    }

    /**
     * Create the Javadocs of the API (incl. the JDBC API) and tools.
     */
    @Description(summary = "Create the API Javadocs (incl. JDBC API and tools).")
    public void javadoc() {
        compileTools();
        delete("docs");
        mkdir("docs/javadoc");
        javadoc("-sourcepath", "src/main",
                "-d", "docs/javadoc",
                "org.h2.jdbc", "org.h2.jdbcx",
                "org.h2.tools", "org.h2.api", "org.h2.engine", "org.h2.fulltext",
                "-classpath",
                "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar");
    }

    /**
     * Create the Javadocs of the implementation.
     */
    @Description(summary = "Create the Javadocs of the implementation.")
    public void javadocImpl() {
        compileTools();
        mkdir("docs/javadocImpl2");
        javadoc("-sourcepath", "src/main" +
                // need to be disabled if not enough memory
                File.pathSeparator + "src/test" +
                File.pathSeparator + "src/tools",
                "-noindex",
                "-d", "docs/javadocImpl2",
                "-classpath", javaToolsJar +
                File.pathSeparator + "ext/slf4j-api-" + SLF4J_VERSION + ".jar" +
                File.pathSeparator + "ext/javax.servlet-api-" + JAVAX_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/jakarta.servlet-api-" + JAKARTA_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar" +
                File.pathSeparator + "ext/asm-" + ASM_VERSION + ".jar" +
                File.pathSeparator + "ext/junit-jupiter-api-" + JUNIT_VERSION + ".jar" +
                File.pathSeparator + "ext/apiguardian-api-" + APIGUARDIAN_VERSION + ".jar",
                "-subpackages", "org.h2",
                "-exclude", "org.h2.dev:org.h2.java:org.h2.test:org.h2.build.code:org.h2.build.doc");

        mkdir("docs/javadocImpl3");
        javadoc("-sourcepath", "src/main",
                "-noindex",
                "-d", "docs/javadocImpl3",
                "-classpath", javaToolsJar +
                File.pathSeparator + "ext/slf4j-api-" + SLF4J_VERSION + ".jar" +
                File.pathSeparator + "ext/javax.servlet-api-" + JAVAX_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/jakarta.servlet-api-" + JAKARTA_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar",
                "-subpackages", "org.h2.mvstore",
                "-exclude", "org.h2.mvstore.db");

        System.setProperty("h2.interfacesOnly", "false");
        System.setProperty("h2.javadocDestDir", "docs/javadocImpl");
        javadoc("-sourcepath", "src/main" +
                File.pathSeparator + "src/test" +
                File.pathSeparator + "src/tools",
                "-Xdoclint:all,-missing",
                "-d", "docs/javadoc",
                "-classpath", javaToolsJar +
                File.pathSeparator + "ext/slf4j-api-" + SLF4J_VERSION + ".jar" +
                File.pathSeparator + "ext/javax.servlet-api-" + JAVAX_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/jakarta.servlet-api-" + JAKARTA_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar" +
                File.pathSeparator + "ext/asm-" + ASM_VERSION + ".jar" +
                File.pathSeparator + "ext/junit-jupiter-api-" + JUNIT_VERSION + ".jar" +
                File.pathSeparator + "ext/apiguardian-api-" + APIGUARDIAN_VERSION + ".jar",
                "-subpackages", "org.h2",
                "-package");
    }

    private static void manifest(String path) {
        String manifest = new String(readFile(Paths.get(path)), StandardCharsets.UTF_8);
        manifest = replaceAll(manifest, "${version}", getVersion());
        manifest = replaceAll(manifest, "${buildJdk}", getJavaSpecVersion());
        String createdBy = System.getProperty("java.runtime.version") +
            " (" + System.getProperty("java.vm.vendor") + ")";
        manifest = replaceAll(manifest, "${createdBy}", createdBy);
        mkdir("temp/META-INF");
        writeFile(Paths.get("temp/META-INF/MANIFEST.MF"), manifest.getBytes());
    }

    /**
     * This will build a release of the H2 .jar files and upload it to
     * file:///data/h2database/m2-repo. This is only required when
     * a new H2 version is made.
     */
    @Description(summary = "Build H2 release jars and upload to file:///data/h2database/m2-repo.")
    public void mavenDeployCentral() {
        // generate and deploy h2*-sources.jar file
        FileList files = files("src/main");
        copy("docs", files, "src/main");
        files = files("docs").keep("docs/org/*").keep("*.java");
        files.addAll(files("docs").keep("docs/META-INF/*"));
        String manifest = new String(readFile(Paths.get("src/installer/source-manifest.mf")));
        manifest = replaceAll(manifest, "${version}", getVersion());
        writeFile(Paths.get("docs/META-INF/MANIFEST.MF"), manifest.getBytes());
        jar("docs/h2-" + getVersion() + "-sources.jar", files, "docs");
        delete("docs/org");
        delete("docs/META-INF");
        // the option -DgeneratePom=false doesn't work with some versions of
        // Maven because of bug http://jira.codehaus.org/browse/MDEPLOY-84
        // as a workaround we generate the pom, but overwrite it later on
        // (that's why the regular jar is created at the very end)
        execScript("mvn", args(
                "deploy:deploy-file",
                "-Dfile=docs/h2-" + getVersion() + "-sources.jar",
                "-Durl=file:///data/h2database/m2-repo",
                "-Dpackaging=jar",
                "-Dclassifier=sources",
                "-Dversion=" + getVersion(),
                "-DartifactId=h2",
                "-DgroupId=com.h2database"
                // ,"-DgeneratePom=false"
                ));

        // generate and deploy the h2*-javadoc.jar file
        javadocImpl();
        files = files("docs/javadocImpl2");
        jar("docs/h2-" + getVersion() + "-javadoc.jar", files, "docs/javadocImpl2");
        execScript("mvn", args(
                "deploy:deploy-file",
                "-Dfile=docs/h2-" + getVersion() + "-javadoc.jar",
                "-Durl=file:///data/h2database/m2-repo",
                "-Dpackaging=jar",
                "-Dclassifier=javadoc",
                "-Dversion=" + getVersion(),
                "-DartifactId=h2",
                "-DgroupId=com.h2database"
                // ,"-DgeneratePom=false"
                ));

        // generate and deploy the h2*.jar file
        jar();
        String pom = new String(readFile(Paths.get("src/installer/pom-template.xml")));
        pom = replaceAll(pom, "@version@", getVersion());
        writeFile(Paths.get("bin/pom.xml"), pom.getBytes());
        execScript("mvn", args(
                "deploy:deploy-file",
                "-Dfile=bin/h2" + getJarSuffix(),
                "-Durl=file:///data/h2database/m2-repo",
                "-Dpackaging=jar",
                "-Dversion=" + getVersion(),
                "-DpomFile=bin/pom.xml",
                "-DartifactId=h2",
                "-DgroupId=com.h2database"));

        // generate the h2-mvstore-*-sources.jar file
        files = files("src/main");
        copy("docs", files, "src/main");
        files = files("docs").keep("docs/org/h2/mvstore/*").
                exclude("docs/org/h2/mvstore/db/*").
                keep("*.java");
        files.addAll(files("docs").keep("docs/META-INF/*"));
        manifest = new String(readFile(Paths.get("src/installer/source-mvstore-manifest.mf")));
        manifest = replaceAll(manifest, "${version}", getVersion());
        writeFile(Paths.get("docs/META-INF/MANIFEST.MF"), manifest.getBytes());
        jar("docs/h2-mvstore-" + getVersion() + "-sources.jar", files, "docs");
        delete("docs/org");
        delete("docs/META-INF");

        // deploy the h2-mvstore-*-source.jar file
        execScript("mvn", args(
                "deploy:deploy-file",
                "-Dfile=docs/h2-mvstore-" + getVersion() + "-sources.jar",
                "-Durl=file:///data/h2database/m2-repo",
                "-Dpackaging=jar",
                "-Dclassifier=sources",
                "-Dversion=" + getVersion(),
                "-DartifactId=h2-mvstore",
                "-DgroupId=com.h2database"
                // ,"-DgeneratePom=false"
                ));

        // generate and deploy the h2-mvstore-*-javadoc.jar file
        javadocImpl();
        files = files("docs/javadocImpl3");
        jar("docs/h2-mvstore-" + getVersion() + "-javadoc.jar", files, "docs/javadocImpl3");
        execScript("mvn", args(
                "deploy:deploy-file",
                "-Dfile=docs/h2-mvstore-" + getVersion() + "-javadoc.jar",
                "-Durl=file:///data/h2database/m2-repo",
                "-Dpackaging=jar",
                "-Dclassifier=javadoc",
                "-Dversion=" + getVersion(),
                "-DartifactId=h2-mvstore",
                "-DgroupId=com.h2database"
                // ,"-DgeneratePom=false"
                ));

        // generate and deploy the h2-mvstore-*.jar file
        jarMVStore();
        pom = new String(readFile(Paths.get("src/installer/pom-mvstore-template.xml")));
        pom = replaceAll(pom, "@version@", getVersion());
        writeFile(Paths.get("bin/pom.xml"), pom.getBytes());
        execScript("mvn", args(
                "deploy:deploy-file",
                "-Dfile=bin/h2-mvstore" + getJarSuffix(),
                "-Durl=file:///data/h2database/m2-repo",
                "-Dpackaging=jar",
                "-Dversion=" + getVersion(),
                "-DpomFile=bin/pom.xml",
                "-DartifactId=h2-mvstore",
                "-DgroupId=com.h2database"));
    }

    /**
     * This will build a 'snapshot' H2 .jar file and upload it to the local
     * Maven 2 repository.
     */
    @Description(summary = "Build a snapshot H2 jar and upload to local Maven 2 repo.")
    public void mavenInstallLocal() {
        // MVStore
        jarMVStore();
        String pom = new String(readFile(Paths.get("src/installer/pom-mvstore-template.xml")));
        pom = replaceAll(pom, "@version@", getVersion());
        writeFile(Paths.get("bin/pom.xml"), pom.getBytes());
        execScript("mvn", args(
                "install:install-file",
                "-Dversion=" + getVersion(),
                "-Dfile=bin/h2-mvstore" + getJarSuffix(),
                "-Dpackaging=jar",
                "-DpomFile=bin/pom.xml",
                "-DartifactId=h2-mvstore",
                "-DgroupId=com.h2database"));
        // database
        jar();
        pom = new String(readFile(Paths.get("src/installer/pom-template.xml")));
        pom = replaceAll(pom, "@version@", getVersion());
        writeFile(Paths.get("bin/pom.xml"), pom.getBytes());
        execScript("mvn", args(
                "install:install-file",
                "-Dversion=" + getVersion(),
                "-Dfile=bin/h2" + getJarSuffix(),
                "-Dpackaging=jar",
                "-DpomFile=bin/pom.xml",
                "-DartifactId=h2",
                "-DgroupId=com.h2database"));
    }

    /**
     * Build the jar file without downloading any files over the network. If the
     * required files are missing, they are listed, and the jar file is not
     * built.
     */
    @Description(summary = "Build H2 jar avoiding downloads (list missing files).")
    public void offline() {
        downloadOrVerify(true);
        if (filesMissing) {
            println("Required files are missing");
        } else {
            jar();
        }
    }

    /**
     * Just run the spellchecker.
     */
    @Description(summary = "Run the spellchecker.")
    public void spellcheck() {
        java("org.h2.build.doc.SpellChecker", null);
    }

    /**
     * Compile and run all tests. This does not include the compile step.
     */
    @Description(summary = "Compile and run all tests (excluding the compile step).")
    public void test() {
        test(false);
    }

    /**
     * Compile and run all fast tests. This does not include the compile step.
     */
    @Description(summary = "Compile and run all tests for CI (excl. the compile step).")
    public void testCI() {
        test(true);
    }

    private void test(boolean ci) {
        downloadTest();
        String cp = "temp" + File.pathSeparator + "bin" +
                File.pathSeparator + "ext/postgresql-" + PGJDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/javax.servlet-api-" + JAVAX_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/jakarta.servlet-api-" + JAKARTA_SERVLET_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-core-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-analysis-common-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/lucene-queryparser-" + LUCENE_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.core-" + OSGI_VERSION + ".jar" +
                File.pathSeparator + "ext/org.osgi.service.jdbc-" + OSGI_JDBC_VERSION + ".jar" +
                File.pathSeparator + "ext/jts-core-" + JTS_VERSION + ".jar" +
                File.pathSeparator + "ext/slf4j-api-" + SLF4J_VERSION + ".jar" +
                File.pathSeparator + "ext/slf4j-nop-" + SLF4J_VERSION + ".jar" +
                File.pathSeparator + "ext/asm-" + ASM_VERSION + ".jar" +
                File.pathSeparator + javaToolsJar;
        cp = addNashornJavaScriptEngineIfNecessary(cp);
        int version = getJavaVersion();
        if (version >= 9) {
            cp = "src/java9/precompiled" + File.pathSeparator + cp;
            if (version >= 10) {
                cp = "src/java10/precompiled" + File.pathSeparator + cp;
                if (version >= 21) {
                    cp = "src/java21/precompiled" + File.pathSeparator + cp;
                }
            }
        }
        int ret;
        if (ci) {
            ret = execJava(args(
                    "-ea",
                    "-Xmx128m",
                    "-XX:MaxDirectMemorySize=2g",
                    "-cp", cp,
                    "org.h2.test.TestAll", "ci"));
        } else {
            ret = execJava(args(
                    "-ea",
                    "-Xmx128m",
                    "-cp", cp,
                    "org.h2.test.TestAll"));
        }
        // return a failure code for CI builds
        if (ret != 0) {
            System.exit(ret);
        }
    }

    /**
     * Print the system properties.
     */
    @Description(summary = "Print the system properties.")
    public void testSysProperties() {
        System.out.println("environment settings:");
        for (Entry<Object, Object> e : new TreeMap<>(
                System.getProperties()).entrySet()) {
            System.out.println(e);
        }
    }

    /**
     * Test the local network of this machine.
     */
    @Description(summary = "Test the local network of this machine.")
    public void testNetwork() {
        try {
            long start = System.nanoTime();
            System.out.println("localhost:");
            System.out.println("  " + InetAddress.getByName("localhost"));
            for (InetAddress address : InetAddress.getAllByName("localhost")) {
                System.out.println("  " + address);
            }
            InetAddress localhost = InetAddress.getLocalHost();
            System.out.println("getLocalHost:" + localhost);
            for (InetAddress address : InetAddress.getAllByName(localhost
                    .getHostAddress())) {
                System.out.println("  " + address);
            }
            InetAddress address = InetAddress.getByName(localhost.getHostAddress());
            System.out.println("byName:" + address);
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(0);
            } catch (Exception e) {
                e.printStackTrace();
                serverSocket = new ServerSocket(0);
            }
            System.out.println(serverSocket);
            int port = serverSocket.getLocalPort();
            final ServerSocket accept = serverSocket;
            start = System.nanoTime();
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        System.out.println("server accepting");
                        Socket s = accept.accept();
                        Thread.sleep(100);
                        System.out.println("server accepted:" + s);
                        System.out.println("server read:" + s.getInputStream().read());
                        Thread.sleep(200);
                        s.getOutputStream().write(234);
                        Thread.sleep(100);
                        System.out.println("server closing");
                        s.close();
                        System.out.println("server done");
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            };
            thread.start();
            System.out.println("time: " +
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            Thread.sleep(1000);
            start = System.nanoTime();
            final Socket socket = new Socket();
            socket.setSoTimeout(2000);
            final InetSocketAddress socketAddress = new InetSocketAddress(address, port);
            System.out.println("client:" + socketAddress);
            try {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            socket.connect(socketAddress, 2000);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                t.start();
                t.join(5000);
                if (!socket.isConnected()) {
                    final InetSocketAddress localhostAddress = new InetSocketAddress(
                            "localhost", port);
                    System.out.println("not connected, trying localhost:"
                            + socketAddress);
                    socket.connect(localhostAddress, 2000);
                }
                System.out.println("time: " +
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                Thread.sleep(200);
                start = System.nanoTime();
                System.out.println("client:" + socket.toString());
                socket.getOutputStream().write(123);
                System.out.println("time: " +
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                Thread.sleep(100);
                start = System.nanoTime();
                System.out.println("client read:" + socket.getInputStream().read());
                socket.close();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            thread.join(5000);
            System.out.println("time: " +
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            if (thread.isAlive()) {
                System.out.println("thread is still alive, interrupting");
                thread.interrupt();
            }
            Thread.sleep(100);
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This build target is used for the automated build. It copies the result
     * of the automated build (including test results, newsfeed, code coverage)
     * to the public web site.
     */
    @Description(summary = "Upload all build results to the public website.")
    public void uploadBuild() {
        String password = System.getProperty("h2.ftpPassword");
        if (password == null) {
            throw new RuntimeException("h2.ftpPassword not set");
        }
        downloadTest();
        mkdir("temp");
        FileList files = files("src/tools").keep("*/UploadBuild.java");
        StringList args = args("-d", "temp", "-sourcepath", "src/tools" +
                File.pathSeparator + "src/test" + File.pathSeparator + "src/main");
        String version = getTargetJavaVersion();
        if (version != null) {
            args = args.plus("-target", version, "-source", version);
        }
        javac(args, files);
        String cp = "bin" + File.pathSeparator + "temp";
        execJava(args("-Xmx512m", "-cp", cp,
                "-Dh2.ftpPassword=" + password,
                "org.h2.build.doc.UploadBuild"));
    }

    /**
     * Build the h2console.war file.
     */
    @Description(summary = "Build the h2console.war file.")
    public void warConsole() {
        jar();
        copy("temp/WEB-INF", files("src/tools/WEB-INF/web.xml"), "src/tools/WEB-INF");
        copy("temp", files("src/tools/WEB-INF/console.html"), "src/tools/WEB-INF");
        copy("temp/WEB-INF/lib", files("bin/h2" + getJarSuffix()), "bin");
        FileList files = files("temp").exclude("temp/org*").exclude("temp/META-INF*");
        files.exclude("*.DS_Store");
        jar("bin/h2console.war", files, "temp");
    }

    @Override
    protected String getLocalMavenDir() {
        String userHome = System.getProperty("user.home", "");
        Path file = Paths.get(userHome, ".m2/settings.xml");
        if (!Files.exists(file)) {
            return super.getLocalMavenDir();
        }
        XMLParser p = new XMLParser(new String(BuildBase.readFile(file)));
        HashMap<String, String> prop = new HashMap<>();
        for (String name = ""; p.hasNext();) {
            int event = p.next();
            if (event == XMLParser.START_ELEMENT) {
                name += "/" + p.getName();
            } else if (event == XMLParser.END_ELEMENT) {
                name = name.substring(0, name.lastIndexOf('/'));
            } else if (event == XMLParser.CHARACTERS) {
                String text = p.getText().trim();
                if (text.length() > 0) {
                    prop.put(name, text);
                }
            }
        }
        String local = prop.get("/settings/localRepository");
        if (local == null) {
            local = "${user.home}/.m2/repository";
        }
        local = replaceAll(local, "${user.home}", userHome);
        return local;
    }

    private static String addNashornJavaScriptEngineIfNecessary(String cp) {
        if (requiresNashornJavaScriptEngine()) {
            return cp +
                    File.pathSeparator + "ext/nashorn-core-" + NASHORN_VERSION + ".jar" +
                    File.pathSeparator + "ext/asm-commons-" + ASM_VERSION + ".jar" +
                    File.pathSeparator + "ext/asm-tree-" + ASM_VERSION + ".jar" +
                    File.pathSeparator + "ext/asm-util-" + ASM_VERSION + ".jar";
        }
        return cp;
    }

    private static boolean requiresNashornJavaScriptEngine() {
        return getJavaVersion() >= 15; // Nashorn was removed in Java 15
    }

}
