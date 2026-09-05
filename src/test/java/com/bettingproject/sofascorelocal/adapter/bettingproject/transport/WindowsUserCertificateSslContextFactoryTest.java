package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateExpiredException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WindowsUserCertificateSslContextFactoryTest {

    private static final byte[] CERTIFICATE_BYTES = new byte[] {9, 8, 7};
    private static final String FINGERPRINT = sha256(CERTIFICATE_BYTES);

    @Test
    void rejectsInvalidFingerprintBeforeOpeningTheWindowsStore() {
        WindowsUserCertificateSslContextFactory factory =
                new WindowsUserCertificateSslContextFactory();

        assertThatThrownBy(() -> factory.create("not-a-sha"))
                .isInstanceOf(
                        WindowsUserCertificateSslContextFactory
                                .WindowsUserCertificateException.class)
                .extracting(exception -> ((WindowsUserCertificateSslContextFactory
                        .WindowsUserCertificateException) exception).error())
                .isEqualTo(WindowsUserCertificateSslContextFactory
                        .WindowsUserCertificateError.INVALID_CERTIFICATE_FINGERPRINT);
    }

    @Test
    void usesExplicitWindowsRootTrustInsteadOfTheRedirectableJvmDefault() throws Exception {
        TrustManagerFactory trustManagerFactory = mock(TrustManagerFactory.class);
        KeyStore trustStore = mock(KeyStore.class);

        WindowsUserCertificateSslContextFactory.initializeExplicitTrustStore(
                trustManagerFactory,
                trustStore);

        assertThat(WindowsUserCertificateSslContextFactory.TRUST_STORE_TYPE)
                .isEqualTo("Windows-ROOT");
        assertThat(WindowsUserCertificateSslContextFactory.TRUST_STORE_PROVIDER)
                .isEqualTo("SunMSCAPI");
        verify(trustManagerFactory).init(same(trustStore));
    }

    @Test
    void selectsOneExactUsableClientCertificateWhoseKeyHasNoJavaEncoding() throws Exception {
        KeyStore keyStore = keyStoreWithOneMatchingAlias(null);

        assertThat(WindowsUserCertificateSslContextFactory
                .selectExactJavaOpaqueClientAlias(keyStore, FINGERPRINT))
                .isEqualTo("exact-client");
    }

    @Test
    void rejectsAJavaEncodablePrivateKeyEvenWhenTheCertificateMatches() throws Exception {
        KeyStore keyStore = keyStoreWithOneMatchingAlias(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> WindowsUserCertificateSslContextFactory
                .selectExactJavaOpaqueClientAlias(keyStore, FINGERPRINT))
                .isInstanceOf(
                        WindowsUserCertificateSslContextFactory
                                .WindowsUserCertificateException.class)
                .extracting(exception -> ((WindowsUserCertificateSslContextFactory
                        .WindowsUserCertificateException) exception).error())
                .isEqualTo(WindowsUserCertificateSslContextFactory
                        .WindowsUserCertificateError.PRIVATE_KEY_ENCODING_EXPOSED);
    }

    @Test
    void rejectsMissingAmbiguousAndExpiredClientCertificateSelections() throws Exception {
        KeyStore missing = mock(KeyStore.class);
        when(missing.aliases()).thenReturn(Collections.emptyEnumeration());
        assertSelectionError(
                missing,
                WindowsUserCertificateSslContextFactory.WindowsUserCertificateError
                        .CERTIFICATE_NOT_FOUND);

        KeyStore ambiguous = keyStoreWithMatchingAliases(List.of("first", "second"));
        assertSelectionError(
                ambiguous,
                WindowsUserCertificateSslContextFactory.WindowsUserCertificateError
                        .CERTIFICATE_AMBIGUOUS);

        KeyStore expired = keyStoreWithOneMatchingAlias(null);
        X509Certificate certificate = (X509Certificate) expired.getCertificate("exact-client");
        org.mockito.Mockito.doThrow(new CertificateExpiredException("synthetic"))
                .when(certificate).checkValidity();
        assertSelectionError(
                expired,
                WindowsUserCertificateSslContextFactory.WindowsUserCertificateError
                        .CLIENT_CERTIFICATE_NOT_CURRENT);
    }

    @Test
    void rejectsMissingClientAuthAndDigitalSignatureUsage() throws Exception {
        KeyStore missingEku = keyStoreWithOneMatchingAlias(null);
        X509Certificate ekuCertificate =
                (X509Certificate) missingEku.getCertificate("exact-client");
        when(ekuCertificate.getExtendedKeyUsage()).thenReturn(List.of("1.3.6.1.5.5.7.3.1"));
        assertSelectionError(
                missingEku,
                WindowsUserCertificateSslContextFactory.WindowsUserCertificateError
                        .CLIENT_AUTH_USAGE_MISSING);

        KeyStore missingSignature = keyStoreWithOneMatchingAlias(null);
        X509Certificate signatureCertificate =
                (X509Certificate) missingSignature.getCertificate("exact-client");
        when(signatureCertificate.getKeyUsage()).thenReturn(new boolean[] {false});
        assertSelectionError(
                missingSignature,
                WindowsUserCertificateSslContextFactory.WindowsUserCertificateError
                        .DIGITAL_SIGNATURE_USAGE_MISSING);
    }

    @Test
    void exactAliasKeyManagerNeverFallsBackToAnotherClientOrServerAlias() {
        X509ExtendedKeyManager delegate = mock(X509ExtendedKeyManager.class);
        when(delegate.getClientAliases("RSA", null))
                .thenReturn(new String[] {"other", "exact-client"});
        PrivateKey privateKey = mock(PrivateKey.class);
        X509Certificate[] chain = new X509Certificate[] {mock(X509Certificate.class)};
        when(delegate.getPrivateKey("exact-client")).thenReturn(privateKey);
        when(delegate.getCertificateChain("exact-client")).thenReturn(chain);
        var manager = new WindowsUserCertificateSslContextFactory
                .ExactClientAliasKeyManager(delegate, "exact-client");

        assertThat(manager.chooseClientAlias(
                new String[] {"RSA"}, null, (Socket) null)).isEqualTo("exact-client");
        assertThat(manager.chooseClientAlias(
                new String[] {"EC"}, null, (Socket) null)).isNull();
        assertThat(manager.chooseServerAlias("RSA", null, null)).isNull();
        assertThat(manager.getPrivateKey("exact-client")).isSameAs(privateKey);
        assertThat(manager.getPrivateKey("other")).isNull();
        assertThat(manager.getCertificateChain("exact-client")).containsExactly(chain);
        assertThat(manager.getCertificateChain("other")).isNull();
    }

    private static KeyStore keyStoreWithOneMatchingAlias(byte[] encodedPrivateKey)
            throws Exception {
        KeyStore keyStore = mock(KeyStore.class);
        X509Certificate certificate = mock(X509Certificate.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        when(keyStore.aliases()).thenReturn(Collections.enumeration(List.of("exact-client")));
        when(keyStore.isKeyEntry("exact-client")).thenReturn(true);
        when(keyStore.getCertificate("exact-client")).thenReturn(certificate);
        when(keyStore.getKey("exact-client", null)).thenReturn(privateKey);
        when(certificate.getEncoded()).thenReturn(CERTIFICATE_BYTES);
        when(certificate.getExtendedKeyUsage()).thenReturn(List.of(
                WindowsUserCertificateSslContextFactory.CLIENT_AUTH_EKU));
        when(certificate.getKeyUsage()).thenReturn(new boolean[] {true});
        when(privateKey.getEncoded()).thenReturn(encodedPrivateKey);

        return keyStore;
    }

    private static KeyStore keyStoreWithMatchingAliases(List<String> aliases)
            throws Exception {
        KeyStore keyStore = mock(KeyStore.class);
        when(keyStore.aliases()).thenReturn(Collections.enumeration(aliases));
        for (String alias : aliases) {
            X509Certificate certificate = mock(X509Certificate.class);
            PrivateKey privateKey = mock(PrivateKey.class);
            when(keyStore.isKeyEntry(alias)).thenReturn(true);
            when(keyStore.getCertificate(alias)).thenReturn(certificate);
            when(keyStore.getKey(alias, null)).thenReturn(privateKey);
            when(certificate.getEncoded()).thenReturn(CERTIFICATE_BYTES);
            when(certificate.getExtendedKeyUsage()).thenReturn(List.of(
                    WindowsUserCertificateSslContextFactory.CLIENT_AUTH_EKU));
            when(certificate.getKeyUsage()).thenReturn(new boolean[] {true});
            when(privateKey.getEncoded()).thenReturn(null);
        }
        return keyStore;
    }

    private static void assertSelectionError(
            KeyStore keyStore,
            WindowsUserCertificateSslContextFactory.WindowsUserCertificateError expected) {
        assertThatThrownBy(() -> WindowsUserCertificateSslContextFactory
                .selectExactJavaOpaqueClientAlias(keyStore, FINGERPRINT))
                .isInstanceOfSatisfying(
                        WindowsUserCertificateSslContextFactory
                                .WindowsUserCertificateException.class,
                        exception -> assertThat(exception.error()).isEqualTo(expected));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
