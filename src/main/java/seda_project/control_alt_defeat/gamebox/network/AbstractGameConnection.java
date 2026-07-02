package seda_project.control_alt_defeat.gamebox.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

abstract class AbstractGameConnection implements Closeable {

    private static final int READ_TIMEOUT_MS = 5_000;
    private static final Consumer<String> NO_OP_MESSAGE_LISTENER = message -> {
    };
    private static final Runnable NO_OP_DISCONNECT_LISTENER = () -> {
    };

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Socket socket;
    protected BufferedReader reader;
    protected PrintWriter writer;
    protected volatile boolean running = false;
    protected Consumer<String> messageListener = NO_OP_MESSAGE_LISTENER;
    protected Runnable disconnectListener = NO_OP_DISCONNECT_LISTENER;

    protected void prepareConnection(Socket connectedSocket) throws IOException {
        socket = connectedSocket;
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        running = true;
    }

    protected void startReadLoop(String threadName, String logName) {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while (running) {
                    try {
                        line = reader.readLine();
                        if (line == null) {
                            disconnectListener.run();
                            break;
                        }
                        messageListener.accept(line);
                    } catch (SocketTimeoutException e) {
                        // Timeout allows close() to flip running and stop the loop promptly.
                    }
                }
            } catch (IOException e) {
                if (running) {
                    log.warn("{} read loop ended: {}", logName, e.getMessage());
                    disconnectListener.run();
                }
            } finally {
                running = false;
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Sends one protocol message to the connected peer.
     *
     * @param message already formatted protocol message
     */
    public synchronized void send(String message) {
        if (writer != null && socket != null && !socket.isClosed()) {
            writer.println(message);
        }
    }

    public void setMessageListener(Consumer<String> messageListener) {
        this.messageListener = messageListener == null ? NO_OP_MESSAGE_LISTENER : messageListener;
    }

    public void setDisconnectListener(Runnable disconnectListener) {
        this.disconnectListener = disconnectListener == null ? NO_OP_DISCONNECT_LISTENER : disconnectListener;
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
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
