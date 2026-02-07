package com.yosefario.nclientv3.components.classes;

import android.os.Build;

import com.yosefario.nclientv3.utility.LogUtility;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

public class CustomSSLSocketFactory extends SSLSocketFactory {
    private static final String[] TLS_V12_ONLY = {"TLSv1.2"};

    private final SSLSocketFactory delegate;

    public CustomSSLSocketFactory(SSLSocketFactory base) {
        this.delegate = base;
    }

    /**
     * No-op: TLS 1.2 is always available on API 24+.
     * Kept for API compatibility; the pre-Lollipop workaround was removed
     * because OkHttp 4.x dropped the single-arg sslSocketFactory() method.
     */
    public static OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
        return client;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return patch(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return patch(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return patch(delegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket patch(Socket s) {
        if (s instanceof SSLSocket) {
            ((SSLSocket) s).setEnabledProtocols(TLS_V12_ONLY);
        }
        return s;
    }
}
