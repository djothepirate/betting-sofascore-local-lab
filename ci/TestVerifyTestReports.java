import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

/** Run with Java 25: java ci/TestVerifyTestReports.java. Fixtures never launch Maven or a browser. */
public class TestVerifyTestReports {
    private static final String GOOD = suite("2", "0", "0", "1",
            "<testcase name=\"pass\"/><testcase name=\"skip\"><skipped/></testcase>");
    private static final String SKIPPED = suite("1", "0", "0", "1",
            "<testcase name=\"skip\"><skipped/></testcase>");
    private static final String SUMMARY = """
            <failsafe-summary result="null" timeout="false">
              <completed>2</completed><errors>0</errors><failures>0</failures><skipped>1</skipped>
              <flakes>0</flakes><failureMessage/>
            </failsafe-summary>
            """;
    private static int passed;

    public static void main(String[] args) throws Exception {
        check("standard passes without Failsafe", "standard", true, root -> standard(root, GOOD));
        check("integration passes with matching summary", "integration", true, TestVerifyTestReports::integration);
        check("missing directory", "standard", false, root -> {});
        check("missing TEST XML", "standard", false,
                root -> write(root, "target/surefire-reports/ordinary.txt", "not evidence"));
        check("empty XML", "standard", false, root -> standard(root, ""));
        check("corrupt XML", "standard", false, root -> standard(root, "<testsuite><sensitive-unclosed>"));
        check("wrong root", "standard", false, root -> standard(root, "<testsuites/>"));
        check("failed testcase", "standard", false, root -> standard(root,
                suite("1", "1", "0", "0", "<testcase><failure>DO_NOT_LOG_PAYLOAD</failure></testcase>")));
        check("errored testcase", "standard", false, root -> standard(root,
                suite("1", "0", "1", "0", "<testcase><error/></testcase>")));
        check("all tests skipped", "standard", false, root -> standard(root, SKIPPED));
        check("zero tests", "standard", false, root -> standard(root, suite("0", "0", "0", "0", "")));
        check("fabricated test count", "standard", false, root -> standard(root,
                suite("1", "0", "0", "0", "")));
        check("failure hidden by counters", "standard", false, root -> standard(root,
                suite("1", "0", "0", "0", "<testcase><failure/></testcase>")));
        check("invalid negative counter", "standard", false, root -> standard(root,
                suite("-1", "0", "0", "0", "")));
        check("missing counter", "standard", false, root -> standard(root, GOOD.replace(" skipped=\"1\"", "")));
        check("corrupt XML among good reports", "standard", false, root -> {
            standard(root, GOOD);
            write(root, "target/surefire-reports/TEST-corrupt.xml", "broken");
        });
        check("DOCTYPE and external entities forbidden", "standard", false, root -> standard(root,
                "<!DOCTYPE testsuite [<!ENTITY leak SYSTEM \"file:///DO_NOT_LOG_PAYLOAD\">]>" + GOOD));
        check("integration missing Failsafe", "integration", false, root -> standard(root, GOOD));
        check("summary alone is insufficient", "integration", false, root -> {
            standard(root, GOOD);
            write(root, "target/failsafe-reports/failsafe-summary.xml", SUMMARY);
        });
        check("integration missing Surefire", "integration", false, root -> {
            write(root, "target/failsafe-reports/TEST-fixture.xml", GOOD);
            write(root, "target/failsafe-reports/failsafe-summary.xml", SUMMARY);
        });
        check("integration missing summary", "integration", false, root -> {
            standard(root, GOOD);
            write(root, "target/failsafe-reports/TEST-fixture.xml", GOOD);
        });
        check("integration entirely skipped", "integration", false, root -> {
            integration(root);
            write(root, "target/failsafe-reports/TEST-fixture.xml", SKIPPED);
        });
        check("integration failed exit result", "integration", false, root -> {
            integration(root);
            write(root, "target/failsafe-reports/failsafe-summary.xml", SUMMARY.replace("result=\"null\"", "result=\"255\""));
        });
        check("integration timeout", "integration", false, root -> {
            integration(root);
            write(root, "target/failsafe-reports/failsafe-summary.xml", SUMMARY.replace("timeout=\"false\"", "timeout=\"true\""));
        });
        check("integration summary mismatch", "integration", false, root -> {
            integration(root);
            write(root, "target/failsafe-reports/failsafe-summary.xml", SUMMARY.replace("<completed>2", "<completed>3"));
        });
        check("integration fork failure message", "integration", false, root -> {
            integration(root);
            write(root, "target/failsafe-reports/failsafe-summary.xml",
                    SUMMARY.replace("<failureMessage/>", "<failureMessage>DO_NOT_LOG_PAYLOAD</failureMessage>"));
        });
        System.out.println("Required report guard fixtures: PASS (" + passed + " cases)");
    }

    private static void check(String name, String mode, boolean expected, Consumer<Path> setup) throws Exception {
        Path root = Files.createTempDirectory("lab-report-fixture-");
        try {
            setup.accept(root);
            var output = new ByteArrayOutputStream();
            int status;
            try (var stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
                status = VerifyTestReports.run(new String[] {mode, "--root", root.toString()}, stream, stream);
            }
            String text = output.toString(StandardCharsets.UTF_8);
            if ((status == 0) != expected || text.contains("DO_NOT_LOG_PAYLOAD") || text.contains("sensitive-unclosed")) {
                throw new AssertionError("Fixture failed: " + name + "; exit=" + status);
            }
            if (expected && (!text.contains("executed=1") || !text.contains("Required test reports: PASS"))) {
                throw new AssertionError("Missing successful validation counts: " + name);
            }
            if (!expected && !text.contains("Required test reports: FAIL")) {
                throw new AssertionError("Missing failure verdict: " + name);
            }
            passed++;
        } finally {
            // Only this exact, freshly created fixture directory is removed.
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private static String suite(String tests, String failures, String errors, String skipped, String cases) {
        return "<testsuite tests=\"" + tests + "\" failures=\"" + failures + "\" errors=\"" + errors
                + "\" skipped=\"" + skipped + "\">" + cases + "</testsuite>";
    }

    private static void standard(Path root, String content) {
        write(root, "target/surefire-reports/TEST-fixture.xml", content);
    }

    private static void integration(Path root) {
        standard(root, GOOD);
        write(root, "target/failsafe-reports/TEST-fixture.xml", GOOD);
        write(root, "target/failsafe-reports/failsafe-summary.xml", SUMMARY);
    }

    private static void write(Path root, String relative, String content) {
        try {
            Path file = root.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create report fixture", e);
        }
    }
}
