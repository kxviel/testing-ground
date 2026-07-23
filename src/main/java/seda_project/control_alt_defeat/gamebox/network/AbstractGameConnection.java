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
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractGameConnection implements Closeable {

    private static final int READ_TIMEOUT_MS = 5_000;
    /*
     * Real-time games can legitimately combine gravity snapshots, held-key
     * repeats, and control messages. Keep a bounded abuse guard, but leave
     * enough headroom for normal LAN play on a responsive keyboard.
     */
    static final int MAX_MESSAGES_PER_SECOND = 120;
    private static final int MAX_CHARS_PER_SECOND = 512 * 1024;
    private static final int MAX_OUTBOUND_MESSAGES = 32;
    private static final long GRACEFUL_CLOSE_TIMEOUT_MS = 500;
    private static final long MAX_LINE_ASSEMBLY_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final Consumer<String> NO_OP_MESSAGE_LISTENER = message -> {
    };
    private static final Runnable NO_OP_DISCONNECT_LISTENER = () -> {
    };

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected volatile Socket socket;
    protected volatile BufferedReader reader;
    protected volatile PrintWriter writer;
    protected volatile boolean running = false;
    protected volatile Consumer<String> messageListener = NO_OP_MESSAGE_LISTENER;
    protected volatile Runnable disconnectListener = NO_OP_DISCONNECT_LISTENER;
    private volatile BlockingQueue<OutboundMessage> outboundMessages =
            new ArrayBlockingQueue<>(MAX_OUTBOUND_MESSAGES);
    private final AtomicBoolean disconnectNotified = new AtomicBoolean();
    private final AtomicBoolean gracefulCloseRequested = new AtomicBoolean();

    protected void prepareConnection(Socket connectedSocket) throws IOException {
        socket = connectedSocket;
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
        outboundMessages = new ArrayBlockingQueue<>(MAX_OUTBOUND_MESSAGES);
        disconnectNotified.set(false);
        gracefulCloseRequested.set(false);
        running = true;
        startWriteLoop();
    }

    protected void startReadLoop(String threadName, String logName) {
        Socket connectionSocket = socket;
        BufferedReader connectionReader = reader;
        Thread thread = new Thread(() -> {
            StringBuilder pendingLine = new StringBuilder();
            long[] lineStartedNanos = {0};
            long rateWindowStarted = System.nanoTime();
            int messagesInWindow = 0;
            int charactersInWindow = 0;
            try {
                while (running && socket == connectionSocket) {
                    try {
                        String line = readBoundedLine(
                                pendingLine, lineStartedNanos, connectionSocket, connectionReader);
                        if (line == null) {
                            if (socket == connectionSocket) {
                                notifyDisconnect();
                            }
                            break;
                        }
                        long now = System.nanoTime();
                        if (now - rateWindowStarted >= 1_000_000_000L) {
                            rateWindowStarted = now;
                            messagesInWindow = 0;
                            charactersInWindow = 0;
                        }
                        messagesInWindow++;
                        charactersInWindow += line.length();
                        if (messagesInWindow > MAX_MESSAGES_PER_SECOND
                                || charactersInWindow > MAX_CHARS_PER_SECOND) {
                            throw new IOException("Inbound message rate exceeded the safety limit.");
                        }
                        try {
                            messageListener.accept(line);
                        } catch (RuntimeException e) {
                            throw new IOException("Inbound message handler rejected a message.", e);
                        }
                    } catch (SocketTimeoutException e) {
                        // Timeout allows close() to flip running and stop the loop promptly.
                    }
                }
            } catch (IOException e) {
                if (running && socket == connectionSocket) {
                    if (e instanceof SocketException) {
                        // A peer closing or resetting its socket is a normal LAN
                        // disconnect, not an application failure.
                        log.debug("{} peer disconnected: {}", logName, e.getMessage());
                    } else {
                        log.warn("{} read loop ended: {}", logName, e.getMessage());
                    }
                    notifyDisconnect();
                }
            } finally {
                closeConnectionSocket(connectionSocket);
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
    public void send(String message) {
        if (!isValidMessage(message)) {
            log.warn("Dropping invalid outbound message.");
            notifyDisconnect();
            close();
            return;
        }

        BlockingQueue<OutboundMessage> activeQueue = outboundMessages;
        if (running
                && !gracefulCloseRequested.get()
                && writer != null
                && socket != null
                && !socket.isClosed()
                && !activeQueue.offer(new OutboundMessage(message, false))) {
            log.warn("Outbound message queue is full; closing stalled connection.");
            notifyDisconnect();
            close();
        }
    }

    /**
     * Sends one final protocol message before closing the connection.
     *
     * <p>Pending state updates are discarded because they are obsolete once the
     * final message is sent. A timeout still forces the socket closed if the peer
     * stops reading.</p>
     *
     * @param message already formatted final protocol message
     */
    public void closeAfterSending(String message) {
        if (!isValidMessage(message)) {
            log.warn("Cannot gracefully close with an invalid outbound message.");
            close();
            return;
        }

        Socket connectionSocket = socket;
        BlockingQueue<OutboundMessage> activeQueue = outboundMessages;
        if (!running
                || writer == null
                || connectionSocket == null
                || connectionSocket.isClosed()) {
            close();
            return;
        }
        if (!gracefulCloseRequested.compareAndSet(false, true)) {
            return;
        }

        activeQueue.clear();
        if (!activeQueue.offer(new OutboundMessage(message, true))) {
            close();
            return;
        }
        startGracefulCloseTimeout(connectionSocket);
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
        closeConnectionSocket();
    }

    private void notifyDisconnect() {
        if (!disconnectNotified.compareAndSet(false, true)) {
            return;
        }
        try {
            disconnectListener.run();
        } catch (RuntimeException e) {
            log.warn("Disconnect listener failed: {}", e.getMessage());
        }
    }

    private void closeConnectionSocket() {
        closeConnectionSocket(null);
    }

    private void closeConnectionSocket(Socket expectedSocket) {
        if (expectedSocket != null && socket != expectedSocket) {
            return;
        }
        running = false;
        Socket socketToClose = socket;
        socket = null;
        reader = null;
        writer = null;
        outboundMessages.clear();
        gracefulCloseRequested.set(false);
        if (socketToClose != null) {
            try {
                socketToClose.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void startWriteLoop() {
        Socket connectionSocket = socket;
        PrintWriter connectionWriter = writer;
        BlockingQueue<OutboundMessage> connectionQueue = outboundMessages;
        Thread thread = new Thread(() -> {
            try {
                while (running && socket == connectionSocket) {
                    OutboundMessage outboundMessage = connectionQueue.poll(1, TimeUnit.SECONDS);
                    if (outboundMessage == null) {
                        continue;
                    }
                    if (connectionWriter == null) {
                        throw new IOException("Connection writer is unavailable.");
                    }
                    connectionWriter.println(outboundMessage.message());
                    if (connectionWriter.checkError()) {
                        throw new IOException("Socket write failed.");
                    }
                    if (outboundMessage.closeAfterWrite()) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (running && socket == connectionSocket) {
                    log.warn("Connection write loop ended: {}", e.getMessage());
                    notifyDisconnect();
                }
            } finally {
                closeConnectionSocket(connectionSocket);
            }
        }, getClass().getSimpleName().toLowerCase() + "-writer");
        thread.setDaemon(true);
        thread.start();
    }

    private void startGracefulCloseTimeout(Socket connectionSocket) {
        Thread timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(GRACEFUL_CLOSE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            closeConnectionSocket(connectionSocket);
        }, getClass().getSimpleName().toLowerCase() + "-graceful-close-timeout");
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    private static boolean isValidMessage(String message) {
        return message != null
                && !message.isBlank()
                && message.length() <= NetworkMessage.MAX_MESSAGE_CHARS;
    }

    private String readBoundedLine(
            StringBuilder pendingLine,
            long[] lineStartedNanos,
            Socket connectionSocket,
            BufferedReader connectionReader) throws IOException {
        while (running && socket == connectionSocket) {
            if (!pendingLine.isEmpty()
                    && System.nanoTime() - lineStartedNanos[0] > MAX_LINE_ASSEMBLY_NANOS) {
                throw new IOException("Inbound message was not completed within the time limit.");
            }
            if (connectionReader == null) {
                throw new IOException("Connection reader is unavailable.");
            }
            int next = connectionReader.read();
            if (next < 0) {
                return pendingLine.isEmpty() ? null : drainLine(pendingLine);
            }
            if (next == '\n') {
                return drainLine(pendingLine);
            }
            if (pendingLine.length() >= NetworkMessage.MAX_MESSAGE_CHARS) {
                throw new IOException("Inbound message exceeded " + NetworkMessage.MAX_MESSAGE_CHARS + " chars.");
            }
            if (pendingLine.isEmpty()) {
                lineStartedNanos[0] = System.nanoTime();
            }
            pendingLine.append((char) next);
        }
        return null;
    }

    private static String drainLine(StringBuilder pendingLine) {
        int length = pendingLine.length();
        if (length > 0 && pendingLine.charAt(length - 1) == '\r') {
            pendingLine.setLength(length - 1);
        }
        String line = pendingLine.toString();
        pendingLine.setLength(0);
        return line;
    }

    private record OutboundMessage(String message, boolean closeAfterWrite) {
    }
}
