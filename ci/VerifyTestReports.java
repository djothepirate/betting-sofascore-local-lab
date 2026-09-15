import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Mandatory validation evidence, checked separately from documentary exports. Java 25, no dependencies. */
public class VerifyTestReports {
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            require(args.length == 1 || args.length == 3,
                    "Usage: java ci/VerifyTestReports.java standard|integration [--root directory]");
            String mode = args[0];
            require(mode.equals("standard") || mode.equals("integration"), "Unknown validation mode.");
            require(args.length == 1 || args[1].equals("--root"), "Expected --root directory.");
            Path root = args.length == 3 ? Path.of(args[2]) : Path.of(".");
            Counts standard = reports(root.resolve("target/surefire-reports"), "Surefire");
            Counts integration = null;
            if (mode.equals("integration")) {
                Path directory = root.resolve("target/failsafe-reports");
                integration = reports(directory, "Failsafe");
                summary(directory.resolve("failsafe-summary.xml"), integration);
            }
            out.println("Surefire: " + standard);
            if (integration != null) {
                out.println("Failsafe: " + integration + "; summary verified");
            }
            out.println("Required test reports: PASS mode=" + mode);
            return 0;
        } catch (InvalidEvidence e) {
            err.println("Required test reports: FAIL: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            // Do not echo parser, filesystem or report content: reports can contain sensitive data.
            err.println("Required test reports: FAIL: Validation could not read the required evidence.");
            return 1;
        }
    }

    private static Counts reports(Path directory, String family) throws Exception {
        require(Files.isDirectory(directory), family + " report directory is missing.");
        List<Path> files;
        try (var entries = Files.list(directory)) {
            files = entries.filter(path -> path.getFileName().toString().startsWith("TEST-")
                            && path.getFileName().toString().endsWith(".xml"))
                    .sorted().toList();
        }
        require(!files.isEmpty(), family + " TEST-*.xml reports are missing.");
        Counts total = new Counts(0, 0, 0, 0);
        for (int index = 0; index < files.size(); index++) {
            String label = family + " report #" + (index + 1);
            Element suite = xml(files.get(index), label);
            require(suite.getTagName().equals("testsuite"), label + " is not a testsuite.");
            Counts declared = new Counts(attribute(suite, "tests", label), attribute(suite, "failures", label),
                    attribute(suite, "errors", label), attribute(suite, "skipped", label));
            List<Element> cases = children(suite, "testcase");
            long failures = 0;
            long errors = 0;
            long skipped = 0;
            for (Element test : cases) {
                int testFailures = children(test, "failure").size();
                int testErrors = children(test, "error").size();
                int testSkipped = children(test, "skipped").size();
                require(testFailures + testErrors + testSkipped <= 1,
                        label + " contains an inconsistent testcase outcome.");
                failures += testFailures;
                errors += testErrors;
                skipped += testSkipped;
            }
            Counts observed = new Counts(cases.size(), failures, errors, skipped);
            require(declared.equals(observed), label + " counters do not match its testcases.");
            require(declared.failures == 0 && declared.errors == 0, label + " records failed tests.");
            total = total.plus(declared);
        }
        require(total.tests - total.skipped > 0, family + " executed no tests (empty or entirely skipped).");
        return total;
    }

    private static void summary(Path file, Counts actual) throws Exception {
        String label = "Failsafe summary";
        Element summary = xml(file, label);
        require(summary.getTagName().equals("failsafe-summary"), label + " has an invalid root.");
        String result = summary.getAttribute("result");
        // Surefire writes the literal "null" when there is no failure exit code.
        require(result.equals("null") || result.equals("0"), label + " does not report success.");
        require(summary.getAttribute("timeout").equals("false"), label + " timed out or lacks timeout status.");
        Counts declared = new Counts(childNumber(summary, "completed", label),
                childNumber(summary, "failures", label), childNumber(summary, "errors", label),
                childNumber(summary, "skipped", label));
        require(declared.equals(actual), label + " counters do not match the Failsafe test reports.");
        for (Element failureMessage : children(summary, "failureMessage")) {
            require(failureMessage.getTextContent().isBlank(), label + " contains a failure message.");
        }
    }

    private static Element xml(Path file, String label) throws Exception {
        require(Files.isRegularFile(file) && Files.size(file) > 0, label + " is missing or empty.");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
        });
        try (var input = Files.newInputStream(file)) {
            return builder.parse(input).getDocumentElement();
        } catch (SAXException | IOException e) {
            throw new InvalidEvidence(label + " is unreadable or invalid XML.");
        }
    }

    private static long attribute(Element element, String name, String label) throws InvalidEvidence {
        return number(element.getAttribute(name), label);
    }

    private static long childNumber(Element element, String name, String label) throws InvalidEvidence {
        List<Element> matching = children(element, name);
        require(matching.size() == 1, label + " is missing a counter or contains duplicate counters.");
        return number(matching.getFirst().getTextContent().trim(), label);
    }

    private static long number(String value, String label) throws InvalidEvidence {
        require(value.matches("[0-9]+"), label + " contains a missing or invalid counter.");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidEvidence(label + " contains an out-of-range counter.");
        }
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) {
                result.add(element);
            }
        }
        return result;
    }

    private static void require(boolean condition, String message) throws InvalidEvidence {
        if (!condition) {
            throw new InvalidEvidence(message);
        }
    }

    private record Counts(long tests, long failures, long errors, long skipped) {
        Counts plus(Counts other) {
            return new Counts(Math.addExact(tests, other.tests), Math.addExact(failures, other.failures),
                    Math.addExact(errors, other.errors), Math.addExact(skipped, other.skipped));
        }

        @Override public String toString() {
            return "tests=" + tests + ", executed=" + (tests - skipped) + ", failures=" + failures
                    + ", errors=" + errors + ", skipped=" + skipped;
        }
    }

    private static final class InvalidEvidence extends Exception {
        InvalidEvidence(String message) { super(message); }
    }
}
