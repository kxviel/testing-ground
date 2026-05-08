package seda_project.control_alt_defeat.gamebox.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

// TCP client used by the joining player in a network game.
public class GameClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(GameClient.class);

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean running = false;
    private Consumer<String> messageListener;
    private Runnable disconnectListener;

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
        this.messageListener = onMessage;
        this.disconnectListener = onDisconnect;

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 120_000);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(5_000);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        running = true;
        log.info("Connected to host {}:{}", host, port);
        startReadLoop();
    }

    private void startReadLoop() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while (running) {
                    try {
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
                    log.warn("Client read loop ended: {}", e.getMessage());
                    disconnectListener.run();
                }
            } finally {
                running = false;
            }
        }, "client-reader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Sends one protocol message to the host.
     *
     * @param message already formatted protocol message
     */
    public synchronized void send(String message) {
        if (writer != null && !socket.isClosed()) {
            writer.println(message);
        }
    }

    /**
     * @return true while the socket is open and the read loop is active
     */
    public boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    @Override
    public void close() {
        running = false;
        try {
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {
        }
    }
}