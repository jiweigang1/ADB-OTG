package io.github.muntashirakon;

import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public class AdbConnectionManager extends AbsAdbConnectionManager {
    private String keyPath;
    private static AdbConnectionManager INSTANCE;
    public static AdbConnectionManager getInstance(String keyPath)  {
        if (INSTANCE == null) {
            try {
                INSTANCE = new AdbConnectionManager(keyPath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return INSTANCE;
    }

    private PrivateKey mPrivateKey;
    private Certificate mCertificate;

    private AdbConnectionManager(String keyPath) throws Exception {
        this.keyPath = keyPath+"/";
        mPrivateKey  = readPrivateKeyFromFile();
        mCertificate = readCertificateFromFile();
        if (mPrivateKey == null) {
            // Generate a new key pair
            int keySize = 2048;
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"));
            KeyPair generateKeyPair = keyPairGenerator.generateKeyPair();
            PublicKey publicKey = generateKeyPair.getPublic();
            mPrivateKey = generateKeyPair.getPrivate();
            // Generate a new certificate
            String subject = "CN=My Awesome App";
            String algorithmName = "SHA512withRSA";
            long expiryDate = System.currentTimeMillis() + 86400000;
            CertificateExtensions certificateExtensions = new CertificateExtensions();
            certificateExtensions.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(
                    new KeyIdentifier(publicKey).getIdentifier()));
            X500Name x500Name = new X500Name(subject);
            Date notBefore = new Date();
            Date notAfter = new Date(expiryDate);
            certificateExtensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
            CertificateValidity certificateValidity = new CertificateValidity(notBefore, notAfter);
            X509CertInfo x509CertInfo = new X509CertInfo();
            x509CertInfo.set("version", new CertificateVersion(2));
            x509CertInfo.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            x509CertInfo.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithmName)));
            x509CertInfo.set("subject", new CertificateSubjectName(x500Name));
            x509CertInfo.set("key", new CertificateX509Key(publicKey));
            x509CertInfo.set("validity", certificateValidity);
            x509CertInfo.set("issuer", new CertificateIssuerName(x500Name));
            x509CertInfo.set("extensions", certificateExtensions);
            X509CertImpl x509CertImpl = new X509CertImpl(x509CertInfo);
            x509CertImpl.sign(mPrivateKey, algorithmName);
            mCertificate = x509CertImpl;
            // Write files
            writePrivateKeyToFile(mPrivateKey);
            writeCertificateToFile(mCertificate);
        }
    }


    @Override
    protected PrivateKey getPrivateKey() {
        return mPrivateKey;
    }


    @Override
    protected Certificate getCertificate() {
        return mCertificate;
    }


    @Override
    protected String getDeviceName() {
        return "MyAwesomeApp";
    }


    private  Certificate readCertificateFromFile()
            throws IOException, CertificateException {
        File certFile = new File(keyPath, "cert.pem");
        if (!certFile.exists()) return null;
        try (InputStream cert = new FileInputStream(certFile)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(cert);
        }
    }

    private  void writeCertificateToFile( Certificate certificate)
            throws CertificateEncodingException, IOException {
        File certFile = new File(keyPath, "cert.pem");
        BASE64Encoder encoder = new BASE64Encoder();
        try (OutputStream os = new FileOutputStream(certFile)) {
            os.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            os.write('\n');
            encoder.encode(certificate.getEncoded(), os);
            os.write('\n');
            os.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
        }
    }


    private  PrivateKey readPrivateKeyFromFile()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        File privateKeyFile = new File(keyPath, "private.key");
        if (!privateKeyFile.exists()) return null;
        byte[] privKeyBytes = new byte[(int) privateKeyFile.length()];
        try (InputStream is = new FileInputStream(privateKeyFile)) {
            is.read(privKeyBytes);
        }
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);
        return keyFactory.generatePrivate(privateKeySpec);
    }

    private  void writePrivateKeyToFile( PrivateKey privateKey)
            throws IOException {
        File privateKeyFile = new File(keyPath, "private.key");
        try (OutputStream os = new FileOutputStream(privateKeyFile)) {
           os.write(privateKey.getEncoded());
        }
    }
}