package seda_project.control_alt_defeat.gamebox.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

// TCP server used by the host player in a network game.
public class GameServer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(GameServer.class);
    public static final int DEFAULT_PORT = 54321;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean running = false;
    private Consumer<String> messageListener;
    private Runnable disconnectListener;

    /**
     * Opens the server socket and stores callbacks for future client messages.
     *
     * @param port         TCP port to bind to
     * @param onMessage    callback for each received protocol line
     * @param onDisconnect callback invoked when the client disconnects
     * @throws IOException if the port cannot be opened
     */
    public void listen(int port, Consumer<String> onMessage, Runnable onDisconnect) throws IOException {
        this.messageListener = onMessage;
        this.disconnectListener = onDisconnect;
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
        clientSocket = serverSocket.accept();
        clientSocket.setTcpNoDelay(true);
        clientSocket.setSoTimeout(5_000);
        reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);
        running = true;
        log.info("Client connected from {}", clientSocket.getRemoteSocketAddress());
        startReadLoop();
    }

    private void startReadLoop() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while (running) {
                    try {
                        // Use a timeout so close() can stop the loop without waiting forever.
                        line = reader.readLine();
                        if (line == null) {
                            disconnectListener.run();
                            break;
                        }
                        String msg = line;
                        messageListener.accept(msg);
                    } catch (SocketTimeoutException e) {
                    }
                }
            } catch (IOException e) {
                if (running) {
                    log.warn("Server read loop ended: {}", e.getMessage());
                    disconnectListener.run();
                }
            } finally {
                running = false;
            }
        }, "server-reader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Sends one protocol message to the connected client.
     *
     * @param message already formatted protocol message
     */
    public synchronized void send(String message) {
        if (writer != null && clientSocket != null && !clientSocket.isClosed()) {
            writer.println(message);
        }
    }

    /**
     * @return true while the client socket is open and the read loop is active
     */
    public boolean isConnected() {
        return running && clientSocket != null && !clientSocket.isClosed();
    }

    /**
     * Stops the read loop and closes all sockets owned by the server.
     */
    @Override
    public void close() {
        running = false;
        try {
            if (clientSocket != null)
                clientSocket.close();
        } catch (IOException ignored) {
        }
        try {
            if (serverSocket != null)
                serverSocket.close();
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
            // Connecting to a public address reveals the interface used for LAN traffic.
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("8.8.8.8", 80), 1000);
                return s.getLocalAddress().getHostAddress();
            }
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                return "127.0.0.1";
            }
        }
    }
}