package seda_project.control_alt_defeat.gamebox.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.DatagramSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.function.Consumer;

// TCP server used by the host player in a network game.
public class GameServer extends AbstractGameConnection {

    public static final int DEFAULT_PORT = 54321;

    private volatile ServerSocket serverSocket;

    /**
     * Opens the server socket and stores callbacks for future client messages.
     *
     * @param port         TCP port to bind to
     * @param onMessage    callback for each received protocol line
     * @param onDisconnect callback invoked when the client disconnects
     * @throws IOException if the port cannot be opened
     */
    public void listen(int port, Consumer<String> onMessage, Runnable onDisconnect) throws IOException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535.");
        }
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
        ServerSocket listeningSocket = serverSocket;
        if (listeningSocket == null || listeningSocket.isClosed()) {
            throw new IOException("Server is not listening.");
        }

        Socket clientSocket = listeningSocket.accept();
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
        closeServerSocket();
    }

    /**
     * Sends a final message while immediately releasing the listening port.
     *
     * @param message already formatted final protocol message
     */
    @Override
    public void closeAfterSending(String message) {
        super.closeAfterSending(message);
        closeServerSocket();
    }

    private void closeServerSocket() {
        ServerSocket socketToClose = serverSocket;
        serverSocket = null;
        if (socketToClose == null) {
            return;
        }
        try {
            socketToClose.close();
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
        InetAddress routedAddress = primaryRouteAddress();
        if (routedAddress != null) {
            return routedAddress.getHostAddress();
        }
        try {
            InetAddress address = localInterfaceAddress(true);
            if (address != null) {
                return address.getHostAddress();
            }
            address = localInterfaceAddress(false);
            if (address != null) {
                return address.getHostAddress();
            }
        } catch (SocketException | SecurityException ignored) {
        }
        return "127.0.0.1";
    }

    private static InetAddress primaryRouteAddress() {
        // UDP connect performs no network I/O. It asks the OS which local
        // adapter would be used, matching the source address LAN clients see.
        try (DatagramSocket probe = new DatagramSocket()) {
            probe.connect(InetAddress.getByAddress(new byte[] {8, 8, 8, 8}), 53);
            InetAddress address = probe.getLocalAddress();
            return address instanceof Inet4Address
                    && !address.isAnyLocalAddress()
                    && !address.isLoopbackAddress()
                    ? address
                    : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private static InetAddress localInterfaceAddress(boolean siteLocalOnly) throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            return null;
        }
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
