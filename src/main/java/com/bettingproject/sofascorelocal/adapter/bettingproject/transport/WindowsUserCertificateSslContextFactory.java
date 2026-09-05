package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.net.ssl.SSLEngine;

/**
 * Creates a client TLS context from one exact private key in the Windows current-user certificate
 * store, provided that the Java security provider exposes no encoded key material. This check is
 * fail-closed but is not an attestation of the native Windows provisioning policy; that evidence
 * remains mandatory before any real target. The factory is deliberately not a Spring bean: the
 * WO-027 runtime gate must be evaluated before this class is called.
 */
public final class WindowsUserCertificateSslContextFactory {

    public static final String KEY_STORE_TYPE = "Windows-MY";
    public static final String KEY_STORE_PROVIDER = "SunMSCAPI";
    public static final String TRUST_STORE_TYPE = "Windows-ROOT";
    public static final String TRUST_STORE_PROVIDER = "SunMSCAPI";
    public static final String CLIENT_AUTH_EKU = "1.3.6.1.5.5.7.3.2";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public SSLContext create(String certificateSha256) {
        String normalizedSha256 = normalizeSha256(certificateSha256);
        try {
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE, KEY_STORE_PROVIDER);
            keyStore.load(null, null);
            String alias = selectExactJavaOpaqueClientAlias(keyStore, normalizedSha256);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, null);
            X509ExtendedKeyManager delegate = extendedKeyManager(
                    keyManagerFactory.getKeyManagers());

            KeyStore trustStore = KeyStore.getInstance(
                    TRUST_STORE_TYPE,
                    TRUST_STORE_PROVIDER);
            trustStore.load(null, null);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            initializeExplicitTrustStore(trustManagerFactory, trustStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(
                    new KeyManager[] {new ExactClientAliasKeyManager(delegate, alias)},
                    trustManagerFactory.getTrustManagers(),
                    null);
            return context;
        }
        catch (GeneralSecurityException | java.io.IOException exception) {
            throw new WindowsUserCertificateException(
                    WindowsUserCertificateError.CERTIFICATE_STORE_UNAVAILABLE,
                    exception);
        }
    }

    static void initializeExplicitTrustStore(
            TrustManagerFactory trustManagerFactory,
            KeyStore trustStore) throws GeneralSecurityException {
        Objects.requireNonNull(trustManagerFactory, "trustManagerFactory");
        Objects.requireNonNull(trustStore, "trustStore");
        trustManagerFactory.init(trustStore);
    }

    static String selectExactJavaOpaqueClientAlias(
            KeyStore keyStore,
            String certificateSha256) throws GeneralSecurityException {
        Objects.requireNonNull(keyStore, "keyStore");
        String normalizedSha256 = normalizeSha256(certificateSha256);
        List<String> matches = new ArrayList<>();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }
            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509Certificate)
                    || !normalizedSha256.equals(certificateSha256(x509Certificate))) {
                continue;
            }
            requireUsableClientCertificate(x509Certificate);
            java.security.Key key = keyStore.getKey(alias, null);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new WindowsUserCertificateException(
                        WindowsUserCertificateError.PRIVATE_KEY_UNAVAILABLE);
            }
            if (privateKey.getEncoded() != null) {
                throw new WindowsUserCertificateException(
                        WindowsUserCertificateError.PRIVATE_KEY_ENCODING_EXPOSED);
            }
            matches.add(alias);
        }
        if (matches.isEmpty()) {
            throw new WindowsUserCertificateException(
                    WindowsUserCertificateError.CERTIFICATE_NOT_FOUND);
        }
        if (matches.size() != 1) {
            throw new WindowsUserCertificateException(
                    WindowsUserCertificateError.CERTIFICATE_AMBIGUOUS);
        }
        return matches.getFirst();
    }

    static String certificateSha256(X509Certificate certificate)
            throws CertificateEncodingException {
        Objects.requireNonNull(certificate, "certificate");
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        }
        catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireUsableClientCertificate(X509Certificate certificate)
            throws GeneralSecurityException {
        try {
            certificate.checkValidity();
        }
        catch (CertificateExpiredException | CertificateNotYetValidException exception) {
            throw new WindowsUserCertificateException(
                    WindowsUserCertificateError.CLIENT_CERTIFICATE_NOT_CURRENT,
                    exception);
        }
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        if (extendedKeyUsage == null || !extendedKeyUsage.contains(CLIENT_AUTH_EKU)) {
            throw new WindowsUserCertificateException(
                    WindowsUserCertificateError.CLIENT_AUTH_USAGE_MISSING);
        }
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage == null || keyUsage.length == 0 || !keyUsage[0]) {
            throw new WindowsUserCertificateException(
                    WindowsUserCertificateError.DIGITAL_SIGNATURE_USAGE_MISSING);
        }
    }

    private static X509ExtendedKeyManager extendedKeyManager(KeyManager[] keyManagers) {
        for (KeyManager keyManager : keyManagers) {
            if (keyManager instanceof X509ExtendedKeyManager extendedKeyManager) {
                return extendedKeyManager;
            }
        }
        throw new WindowsUserCertificateException(
                WindowsUserCertificateError.PRIVATE_KEY_UNAVAILABLE);
    }

    private static String normalizeSha256(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(normalized).matches()) {
            throw new WindowsUserCertificateException(
                    WindowsUserCertificateError.INVALID_CERTIFICATE_FINGERPRINT);
        }
        return normalized;
    }

    enum WindowsUserCertificateError {
        INVALID_CERTIFICATE_FINGERPRINT,
        CERTIFICATE_STORE_UNAVAILABLE,
        CERTIFICATE_NOT_FOUND,
        CERTIFICATE_AMBIGUOUS,
        CLIENT_CERTIFICATE_NOT_CURRENT,
        CLIENT_AUTH_USAGE_MISSING,
        DIGITAL_SIGNATURE_USAGE_MISSING,
        PRIVATE_KEY_UNAVAILABLE,
        PRIVATE_KEY_ENCODING_EXPOSED
    }

    static final class WindowsUserCertificateException extends RuntimeException {

        private final WindowsUserCertificateError error;

        WindowsUserCertificateException(WindowsUserCertificateError error) {
            super(Objects.requireNonNull(error, "error").name());
            this.error = error;
        }

        WindowsUserCertificateException(
                WindowsUserCertificateError error,
                Throwable cause) {
            super(Objects.requireNonNull(error, "error").name(), cause);
            this.error = error;
        }

        WindowsUserCertificateError error() {
            return error;
        }
    }

    static final class ExactClientAliasKeyManager extends X509ExtendedKeyManager {

        private final X509ExtendedKeyManager delegate;
        private final String alias;

        ExactClientAliasKeyManager(X509ExtendedKeyManager delegate, String alias) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.alias = Objects.requireNonNull(alias, "alias");
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return supports(keyType, issuers) ? new String[] {alias} : null;
        }

        @Override
        public String chooseClientAlias(
                String[] keyTypes,
                Principal[] issuers,
                Socket socket) {
            return supportsAny(keyTypes, issuers) ? alias : null;
        }

        @Override
        public String chooseEngineClientAlias(
                String[] keyTypes,
                Principal[] issuers,
                SSLEngine engine) {
            return supportsAny(keyTypes, issuers) ? alias : null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public String chooseServerAlias(
                String keyType,
                Principal[] issuers,
                Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String requestedAlias) {
            return alias.equals(requestedAlias)
                    ? delegate.getCertificateChain(alias)
                    : null;
        }

        @Override
        public PrivateKey getPrivateKey(String requestedAlias) {
            return alias.equals(requestedAlias)
                    ? delegate.getPrivateKey(alias)
                    : null;
        }

        private boolean supportsAny(String[] keyTypes, Principal[] issuers) {
            if (keyTypes == null) {
                return false;
            }
            for (String keyType : keyTypes) {
                if (supports(keyType, issuers)) {
                    return true;
                }
            }
            return false;
        }

        private boolean supports(String keyType, Principal[] issuers) {
            String[] aliases = delegate.getClientAliases(keyType, issuers);
            if (aliases == null) {
                return false;
            }
            for (String candidate : aliases) {
                if (alias.equals(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }
}
