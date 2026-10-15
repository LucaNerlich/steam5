package org.steam5.http;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pinned factory hands out sockets that are already connected, but
 * HttpsURLConnection's NetworkClient connects whatever the factory returned a
 * second time; SSLSocketImpl rejects that on a layered socket with
 * "Already connected", which made every pinned fetch fail.
 */
class PinnedSslSocketTest {

    private final SSLSocket delegate = mock(SSLSocket.class);
    private final PinnedSslSocket socket = new PinnedSslSocket(delegate);

    @Test
    void connectIsSwallowedBecauseTheSocketIsAlreadyConnected() throws IOException {
        socket.connect(new InetSocketAddress("203.0.113.10", 443));
        socket.connect(new InetSocketAddress("203.0.113.10", 443), 10_000);

        verify(delegate, never()).connect(any(SocketAddress.class));
        verify(delegate, never()).connect(any(SocketAddress.class), anyInt());
    }

    @Test
    void delegatesStreamsAndStateToTheWrappedSocket() throws IOException {
        final InputStream in = mock(InputStream.class);
        final SSLSession session = mock(SSLSession.class);
        when(delegate.getInputStream()).thenReturn(in);
        when(delegate.isConnected()).thenReturn(true);
        when(delegate.getSession()).thenReturn(session);

        assertSame(in, socket.getInputStream());
        assertTrue(socket.isConnected());
        assertSame(session, socket.getSession());
    }
}
