package com.glux.proxyswitcher.util;

import io.netty.handler.ssl.util.TrustManagerFactoryWrapper;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CertificateUtil {
    private static final Logger logger = LoggerFactory.getLogger(CertificateUtil.class);

    public static QuicSslContext createServerSslContext() throws Exception {
        return QuicSslContextBuilder.forServer(
                        new File("certs/server-key.pem"),
                        null,
                        new File("certs/server-cert.pem"))
                .trustManager(new File("certs/ca-cert.pem"))
                .clientAuth(io.netty.handler.ssl.ClientAuth.REQUIRE)
                .applicationProtocols("http")
                .build();
    }

    public static QuicSslContext createClientSslContext() throws Exception {

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        trustStore.setCertificateEntry("ca", cf.generateCertificate(new FileInputStream("certs/ca-cert.pem")));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        final X509TrustManager tm = (X509TrustManager)tmf.getTrustManagers()[0];

        TrustManager ts = new X509TrustManager() {
            private X509Certificate[] trustedCerts;
            {
                try {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    trustedCerts = new X509Certificate[]{(X509Certificate) cf.generateCertificate(new FileInputStream("certs/ca-cert.pem"))};
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                tm.checkClientTrusted(chain, authType);
            }
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                tm.checkServerTrusted(chain, authType);
            }
            public X509Certificate[] getAcceptedIssuers() { return trustedCerts; }
        };

        return QuicSslContextBuilder.forClient()
                .keyManager(new File("certs/client-key.pem"),null,new File("certs/client-cert.pem"))
                .trustManager(new File("certs/ca-cert.pem"))
                .applicationProtocols("http")
                .build();
    }
}