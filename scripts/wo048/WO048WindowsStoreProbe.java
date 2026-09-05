import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** WO-048 offline Windows-store qualification. It deliberately creates no TLS or socket object. */
public final class WO048WindowsStoreProbe {
    private static final String PROVIDER = "SunMSCAPI";
    private static final String CLIENT_AUTH = "1.3.6.1.5.5.7.3.2";
    private static final String SERVER_AUTH = "1.3.6.1.5.5.7.3.1";
    private static final String SHA256_WITH_RSA = "1.2.840.113549.1.1.11";
    private static final Set<String> CRITICAL_CLIENT = Set.of("2.5.29.19", "2.5.29.37");
    private static final String[] NEUTRAL_ENVIRONMENT = {
        "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JDK_JAVAC_OPTIONS",
        "JAVA_OPTS",
        "CLASSPATH", "MAVEN_OPTS", "GRADLE_OPTS", "HTTP_PROXY", "HTTPS_PROXY",
        "ALL_PROXY", "NO_PROXY", "FTP_PROXY", "SOCKS_PROXY", "GIT_HTTP_PROXY",
        "GIT_HTTPS_PROXY",
        "SSLKEYLOGFILE", "NSS_SSLKEYLOGFILE", "JDK_TLS_KEYLOGGER", "JAVAX_NET_DEBUG",
        "JDK_HTTPCLIENT_HTTPCLIENT_LOG"
    };

    private WO048WindowsStoreProbe() {
    }

    public static void main(String[] args) {
        try {
            assertRuntimeBoundary();
            if (args.length == 1 && "--qualification-output-flood".equals(args[0])) {
                System.out.print("X".repeat(20000));
                return;
            }
            if (args.length == 1 && "--runtime-only".equals(args[0])) {
                System.out.println("WO048_JAVA_RUNTIME_PROVENANCE=PASS_JAVA_25");
                System.out.println("WO048_JAVA_ENVIRONMENT_SANITIZED=YES");
                System.out.println("WO048_JAVA_TLS_HANDSHAKES=0");
                System.out.println("WO048_JAVA_SOCKETS_OPENED=0");
                return;
            }
            if (args.length != 2 || !"--stores".equals(args[0])) {
                throw new IllegalArgumentException("invalid mode");
            }
            Map<String, String> expected = readStrictInput(Path.of(args[1]));
            assertWindowsStores(expected);
            System.out.println("WO048_JAVA_STORE_PROBE=PASS");
            System.out.println("WO048_JAVA_PROVIDER=SunMSCAPI");
            System.out.println("WO048_JAVA_WINDOWS_MY_EXACT_MATCH_COUNT=1");
            System.out.println("WO048_JAVA_WINDOWS_ROOT_EXACT_MATCH_COUNT=1");
            System.out.println("WO048_JAVA_CLIENT_PRIVATE_KEY_ENCODING=NULL");
            System.out.println("WO048_JAVA_TLS_HANDSHAKES=0");
            System.out.println("WO048_JAVA_SOCKETS_OPENED=0");
        } catch (Throwable failure) {
            System.err.println("WO048_JAVA_STORE_PROBE=FAIL_CLOSED");
            System.exit(1);
        }
    }

    private static void assertRuntimeBoundary() throws IOException {
        require(Runtime.version().feature() == 25, "Java feature");
        String expectedHome = System.getenv("WO048_EXPECTED_JAVA_HOME");
        require(expectedHome != null && !expectedHome.isBlank(), "expected java home");
        Path expected = Path.of(expectedHome).toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path actual = Path.of(System.getProperty("java.home")).toRealPath(LinkOption.NOFOLLOW_LINKS);
        require(expected.equals(actual), "java home provenance");
        require(Security.getProvider(PROVIDER) != null, "SunMSCAPI provider");
        for (String name : NEUTRAL_ENVIRONMENT) {
            String value = System.getenv(name);
            require(value == null || value.isEmpty(), "injected environment");
        }
        require("false".equals(System.getProperty("java.net.useSystemProxies")), "system proxy");
        require(System.getProperty("http.proxyHost") == null, "http proxy property");
        require(System.getProperty("https.proxyHost") == null, "https proxy property");
        require(System.getProperty("socksProxyHost") == null, "socks proxy property");
        for (String option : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String lowered = option.toLowerCase(Locale.ROOT);
            require(!lowered.startsWith("-javaagent:") && !lowered.startsWith("-agentlib:")
                    && !lowered.startsWith("-agentpath:") && !lowered.startsWith("-xbootclasspath")
                    && !lowered.contains("proxyhost") && !lowered.contains("proxyport"),
                    "injected JVM option");
        }
    }

    private static Map<String, String> readStrictInput(Path input) throws IOException {
        require(input.isAbsolute() && Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(input), "private input path");
        List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
        require(lines.size() == 2, "private input cardinality");
        Map<String, String> values = new HashMap<>();
        for (String line : lines) {
            int separator = line.indexOf('=');
            require(separator > 0 && separator == line.lastIndexOf('='), "private input syntax");
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            require(("clientSha256".equals(key) || "serverSha256".equals(key))
                    && value.matches("[0-9a-f]{64}") && values.put(key, value) == null,
                    "private input value");
        }
        require(values.size() == 2, "private input keys");
        return Collections.unmodifiableMap(values);
    }

    private static void assertWindowsStores(Map<String, String> expected) throws Exception {
        KeyStore my = KeyStore.getInstance("Windows-MY", PROVIDER);
        KeyStore root = KeyStore.getInstance("Windows-ROOT", PROVIDER);
        my.load(null, null);
        root.load(null, null);
        require(PROVIDER.equals(my.getProvider().getName()) && PROVIDER.equals(root.getProvider().getName()),
                "keystore provider");

        List<String> clientAliases = matchingAliases(my, expected.get("clientSha256"));
        List<String> serverAliases = matchingAliases(root, expected.get("serverSha256"));
        require(clientAliases.size() == 1 && serverAliases.size() == 1, "store match cardinality");

        X509Certificate client = (X509Certificate) my.getCertificate(clientAliases.get(0));
        X509Certificate server = (X509Certificate) root.getCertificate(serverAliases.get(0));
        assertClientProfile(client);
        assertServerProfile(server);

        Key key = my.getKey(clientAliases.get(0), null);
        require(key instanceof PrivateKey && "RSA".equals(key.getAlgorithm()), "client private key");
        require(key.getEncoded() == null, "client private key encoding");
    }

    private static List<String> matchingAliases(KeyStore store, String expectedSha256) throws Exception {
        List<String> matches = new ArrayList<>();
        Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            java.security.cert.Certificate certificate = store.getCertificate(alias);
            if (certificate instanceof X509Certificate
                    && expectedSha256.equals(sha256(certificate.getEncoded()))) {
                matches.add(alias);
            }
        }
        return matches;
    }

    private static void assertClientProfile(X509Certificate certificate) throws Exception {
        assertCommonProfile(certificate);
        require(certificate.getCriticalExtensionOIDs() != null
                && certificate.getCriticalExtensionOIDs().containsAll(CRITICAL_CLIENT), "client critical extensions");
        require(List.of(CLIENT_AUTH).equals(certificate.getExtendedKeyUsage()), "client EKU");
        assertExactKeyUsage(certificate.getKeyUsage(), 0);
        Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
        require(sans == null || sans.isEmpty(), "client SAN");
    }

    private static void assertServerProfile(X509Certificate certificate) throws Exception {
        assertCommonProfile(certificate);
        require(List.of(SERVER_AUTH).equals(certificate.getExtendedKeyUsage()), "server EKU");
        assertExactKeyUsage(certificate.getKeyUsage(), 0, 2);
        Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
        require(sans != null && sans.size() == 1, "server SAN cardinality");
        List<?> san = sans.iterator().next();
        require(san.size() == 2 && Integer.valueOf(7).equals(san.get(0))
                && "127.0.0.1".equals(san.get(1)), "server SAN value");
    }

    private static void assertCommonProfile(X509Certificate certificate) throws Exception {
        certificate.checkValidity();
        require(certificate.getBasicConstraints() == -1, "CA false");
        require(certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()), "self issued");
        require(SHA256_WITH_RSA.equals(certificate.getSigAlgOID()), "signature algorithm");
        require(certificate.getPublicKey() instanceof RSAPublicKey, "RSA public key");
        BigInteger modulus = ((RSAPublicKey) certificate.getPublicKey()).getModulus();
        require(modulus.bitLength() == 3072, "RSA key size");
        Duration validity = Duration.between(certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant());
        require(!validity.isNegative() && validity.compareTo(Duration.ofDays(7)) <= 0,
                "certificate validity");
        require(!certificate.getNotBefore().toInstant().isAfter(Instant.now()), "not yet valid");
    }

    private static void assertExactKeyUsage(boolean[] usage, int... expectedIndexes) {
        require(usage != null, "key usage");
        boolean[] expected = new boolean[Math.max(usage.length, 9)];
        for (int index : expectedIndexes) {
            expected[index] = true;
            require(usage.length > index && usage[index], "required key usage bit");
        }
        for (int index = 0; index < usage.length; index++) {
            require(usage[index] == expected[index], "key usage bit");
        }
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder encoded = new StringBuilder(64);
        for (byte item : digest) {
            encoded.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return encoded.toString();
    }

    private static void require(boolean condition, String ignoredDescription) {
        if (!condition) {
            throw new IllegalStateException("WO048 probe rejected input");
        }
    }
}
