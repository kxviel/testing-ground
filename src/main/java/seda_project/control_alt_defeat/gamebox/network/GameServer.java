package seda_project.control_alt_defeat.gamebox.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.function.Consumer;

// TCP server used by the host player in a network game.
public class GameServer extends AbstractGameConnection {

    private static final Logger log = LoggerFactory.getLogger(GameServer.class);
    public static final int DEFAULT_PORT = 54321;

    private ServerSocket serverSocket;

    /**
     * Opens the server socket and stores callbacks for future client messages.
     *
     * @param port         TCP port to bind to
     * @param onMessage    callback for each received protocol line
     * @param onDisconnect callback invoked when the client disconnects
     * @throws IOException if the port cannot be opened
     */
    public void listen(int port, Consumer<String> onMessage, Runnable onDisconnect) throws IOException {
        close();
        setMessageListener(onMessage);
        setDisconnectListener(onDisconnect);
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(120_000);
        log.info("Server listening on port {}", port);
    }

    /**
     * Blocks until one client connects, then starts the background read loop.
     *
     * @throws IOException if accepting the client or opening streams fails
     */
    public void waitForClient() throws IOException {
        if (serverSocket == null) {
            throw new IOException("Server is not listening.");
        }

        Socket clientSocket = serverSocket.accept();
        try {
            prepareConnection(clientSocket);
            log.info("Client connected from {}", clientSocket.getRemoteSocketAddress());
            startReadLoop("server-reader", "Server");
        } catch (IOException e) {
            try {
                clientSocket.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    public int localPort() {
        return serverSocket == null ? DEFAULT_PORT : serverSocket.getLocalPort();
    }

    /**
     * Stops the read loop and closes all sockets owned by the server.
     */
    @Override
    public void close() {
        super.close();
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Attempts to find the LAN-facing address that should be shared with the
     * second player.
     *
     * @return host IP address, falling back to localhost if detection fails
     */
    public static String getLocalAddress() {
        try {
            InetAddress address = localInterfaceAddress(true);
            if (address != null) {
                return address.getHostAddress();
            }
            address = localInterfaceAddress(false);
            if (address != null) {
                return address.getHostAddress();
            }
        } catch (SocketException ignored) {
        }

        try {
            // Connecting to a public address reveals the interface used for LAN traffic.
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("8.8.8.8", 80), 1000);
                return s.getLocalAddress().getHostAddress();
            }
        } catch (IOException e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                return "127.0.0.1";
            }
        }
    }

    private static InetAddress localInterfaceAddress(boolean siteLocalOnly) throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address
                        && !address.isLoopbackAddress()
                        && (!siteLocalOnly || address.isSiteLocalAddress())) {
                    return address;
                }
            }
        }
        return null;
    }
}
