package com.glux.proxyswitcher.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Date;

public class CertificateGenerator {
    
    public static void main(String[] args) {
        if (args.length > 0 && "cert".equals(args[0])) {
            generateCertificates("Microsoft Azure", "auth.azure.com","u1008543.azure.com");
        } else {
            System.out.println("使用方法: java CertificateGenerator cert");
        }
    }
    
    public static void generateCertificates(String caName, String serverName, String clientName) {
        System.out.println("生成自签名证书用于QUIC连接...");
        
        try {
            // 创建证书目录
            File certsDir = new File("certs");
            if (!certsDir.exists()) {
                certsDir.mkdir();
            }
            
            // 生成CA密钥对和证书
            KeyPair caKeyPair = generateKeyPair();
            X509Certificate caCert = generateCACertificate(caKeyPair, caName);
            
            // 生成服务器密钥对和证书
            KeyPair serverKeyPair = generateKeyPair();
            X509Certificate serverCert = generateCertificate(serverKeyPair, caKeyPair.getPrivate(), caCert, serverName);
            
            // 生成客户端密钥对和证书
            KeyPair clientKeyPair = generateKeyPair();
            X509Certificate clientCert = generateCertificate(clientKeyPair, caKeyPair.getPrivate(), caCert, clientName);
            
            // 保存证书和私钥
            savePEM("certs/ca-key.pem", caKeyPair.getPrivate());
            savePEM("certs/ca-cert.pem", caCert);
            savePEM("certs/server-key.pem", serverKeyPair.getPrivate());
            savePEM("certs/server-cert.pem", serverCert);
            savePEM("certs/client-key.pem", clientKeyPair.getPrivate());
            savePEM("certs/client-cert.pem", clientCert);
            
            System.out.println("证书生成完成！");
            System.out.println("文件位置：");
            System.out.println("- CA证书: certs/ca-cert.pem");
            System.out.println("- 服务器证书 (auth.azure.com): certs/server-cert.pem");
            System.out.println("- 服务器私钥: certs/server-key.pem");
            System.out.println("- 客户端证书: certs/client-cert.pem");
            System.out.println("- 客户端私钥: certs/client-key.pem");
            
        } catch (Exception e) {
            System.err.println("证书生成失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        return keyGen.generateKeyPair();
    }
    
    private static X509Certificate generateCACertificate(KeyPair keyPair, String cn) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        
        X500Name subject = new X500Name("CN=" + cn);
        BigInteger serial = new BigInteger(64, new SecureRandom());
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);
        
        SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
        
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, publicKeyInfo);

        // 添加BasicConstraints扩展，标识为CA证书
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.getPrivate());
        
        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder);
    }
    
    private static X509Certificate generateCertificate(KeyPair keyPair, PrivateKey caPrivateKey, X509Certificate caCert, String cn) throws Exception {
        X500Name subject = new X500Name("CN=" + cn);
        X500Name issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
        BigInteger serial = new BigInteger(64, new SecureRandom());
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);
        
        SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
        
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, publicKeyInfo);
        
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(caPrivateKey);
        
        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder);
    }
    
    private static void savePEM(String filename, Object obj) throws Exception {
        try (FileWriter writer = new FileWriter(filename)) {
            if (obj instanceof PrivateKey) {
                writer.write("-----BEGIN PRIVATE KEY-----\n");
                writer.write(java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(((PrivateKey) obj).getEncoded()));
                writer.write("\n-----END PRIVATE KEY-----\n");
            } else if (obj instanceof X509Certificate) {
                writer.write("-----BEGIN CERTIFICATE-----\n");
                writer.write(java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(((X509Certificate) obj).getEncoded()));
                writer.write("\n-----END CERTIFICATE-----\n");
            }
        }
    }
}