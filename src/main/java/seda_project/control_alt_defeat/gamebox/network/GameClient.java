package seda_project.control_alt_defeat.gamebox.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

// TCP client used by the joining player in a network game.
public class GameClient extends AbstractGameConnection {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /**
     * Connects to a host and starts the background read loop.
     *
     * @param host         IP address or hostname of the game host
     * @param port         TCP port of the game host
     * @param onMessage    callback for each received protocol line
     * @param onDisconnect callback invoked if the host disconnects
     * @throws IOException if the socket connection or streams fail
     */
    public void connect(String host, int port, Consumer<String> onMessage, Runnable onDisconnect) throws IOException {
        if (host == null || host.isBlank()) {
            throw new IOException("Host must not be blank.");
        }
        if (port < 1 || port > 65_535) {
            throw new IOException("Port must be between 1 and 65535.");
        }
        close();
        setMessageListener(onMessage);
        setDisconnectListener(onDisconnect);

        Socket connectedSocket = new Socket();
        socket = connectedSocket;
        try {
            connectedSocket.connect(new InetSocketAddress(host.trim(), port), CONNECT_TIMEOUT_MS);
            if (connectedSocket.isClosed() || socket != connectedSocket) {
                throw new IOException("Connection attempt was cancelled.");
            }
            prepareConnection(connectedSocket);
            log.info("Connected to host {}:{}", host, port);
            startReadLoop("client-reader", "Client");
        } catch (IOException e) {
            try {
                connectedSocket.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            if (socket == connectedSocket) {
                socket = null;
            }
            throw e;
        }
    }
}
