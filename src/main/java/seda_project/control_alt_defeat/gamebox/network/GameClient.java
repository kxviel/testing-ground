package seda_project.control_alt_defeat.gamebox.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

// TCP client used by the joining player in a network game.
public class GameClient extends AbstractGameConnection {

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
        close();
        setMessageListener(onMessage);
        setDisconnectListener(onDisconnect);

        Socket connectedSocket = new Socket();
        try {
            connectedSocket.connect(new InetSocketAddress(host, port), 120_000);
            prepareConnection(connectedSocket);
            log.info("Connected to host {}:{}", host, port);
            startReadLoop("client-reader", "Client");
        } catch (IOException e) {
            closeQuietly(connectedSocket);
            throw e;
        }
    }
}
