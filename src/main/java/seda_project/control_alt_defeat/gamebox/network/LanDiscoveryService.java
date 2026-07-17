package seda_project.control_alt_defeat.gamebox.network;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import seda_project.control_alt_defeat.gamebox.util.SafeText;

public class LanDiscoveryService implements Closeable {

    private static final String PREFIX = "GAMEBOX_DISCOVERY";
    private static final int TETRIS_PORT = 54322;
    private static final int MEMORY_PORT = 54323;
    private static final int HEX_CHESS_PORT = 54324;
    private static final int TICK_MS = 1_000;
    private static final long ADVERTISEMENT_TTL_MS = 5_000;
    private static final int MAX_PACKETS_PER_SECOND = 30;

    private final String gameType;
    private final int discoveryPort;
    private final String displayName;
    private final String threadPrefix;
    private final String sessionId = UUID.randomUUID().toString();
    private final Object advertisingMonitor = new Object();

    private volatile boolean advertising;
    private volatile boolean listening;
    private volatile DatagramSocket advertisingSocket;
    private volatile DatagramSocket listeningSocket;

    public record DiscoveredGame(
            String playerName,
            String gameType,
            String hostAddress,
            int tcpPort,
            String sessionId,
            long timestamp) {

        @Override
        public String toString() {
            return playerName + " - " + hostAddress + ":" + tcpPort;
        }
    }

    public static LanDiscoveryService tetris() {
        return new LanDiscoveryService("TETRIS", TETRIS_PORT, "Zetris", "tetris");
    }

    public static LanDiscoveryService memory() {
        return new LanDiscoveryService("MEMORY", MEMORY_PORT, "Memory", "memory");
    }

    public static LanDiscoveryService hexChess() {
        return new LanDiscoveryService("HEX_CHESS", HEX_CHESS_PORT, "Hex Chess", "hexchess");
    }

    public LanDiscoveryService(String gameType, int discoveryPort, String displayName, String threadPrefix) {
        this.gameType = gameType;
        this.discoveryPort = discoveryPort;
        this.displayName = displayName;
        this.threadPrefix = threadPrefix;
    }

    public void startAdvertising(String playerName, int tcpPort, Consumer<String> onError) {
        stopAdvertising();
        Consumer<String> safeError = onError == null ? message -> { } : onError;
        if (tcpPort < 1 || tcpPort > 65_535) {
            notifySafely(safeError, "Could not advertise " + displayName + " LAN game: invalid TCP port.");
            return;
        }
        advertising = true;

        Thread thread = new Thread(() -> advertise(playerName, tcpPort, safeError), threadPrefix + "-udp-advertise");
        thread.setDaemon(true);
        thread.start();
    }

    public void startListening(Consumer<DiscoveredGame> onGameFound, Consumer<String> onError) {
        stopListening();
        Consumer<DiscoveredGame> safeGameFound = onGameFound == null ? game -> { } : onGameFound;
        Consumer<String> safeError = onError == null ? message -> { } : onError;
        listening = true;

        Thread thread = new Thread(() -> listen(safeGameFound, safeError), threadPrefix + "-udp-listen");
        thread.setDaemon(true);
        thread.start();
    }

    public void stopAdvertising() {
        advertising = false;
        if (advertisingSocket != null) {
            advertisingSocket.close();
            advertisingSocket = null;
        }
        synchronized (advertisingMonitor) {
            advertisingMonitor.notifyAll();
        }
    }

    public void stopListening() {
        listening = false;
        if (listeningSocket != null) {
            listeningSocket.close();
            listeningSocket = null;
        }
    }

    @Override
    public void close() {
        stopAdvertising();
        stopListening();
    }

    private void advertise(String playerName, int tcpPort, Consumer<String> onError) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            advertisingSocket = socket;
            socket.setReuseAddress(true);
            socket.setBroadcast(true);

            while (advertising && advertisingSocket == socket) {
                byte[] data = buildMessage(playerName, tcpPort).getBytes(StandardCharsets.UTF_8);
                for (InetAddress address : broadcastAddresses()) {
                    socket.send(new DatagramPacket(data, data.length, address, discoveryPort));
                }
                waitForNextAdvertisement();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (advertising && advertisingSocket == socket) {
                notifySafely(onError, "Could not advertise " + displayName + " LAN game: " + e.getMessage());
            }
        } finally {
            if (socket != null) {
                socket.close();
            }
            if (advertisingSocket == socket) {
                advertisingSocket = null;
            }
        }
    }

    private void listen(Consumer<DiscoveredGame> onGameFound, Consumer<String> onError) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), discoveryPort));
            socket.setSoTimeout(TICK_MS);
            listeningSocket = socket;
            long rateWindowStarted = System.nanoTime();
            int packetsInWindow = 0;

            while (listening && listeningSocket == socket) {
                try {
                    byte[] data = new byte[512];
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    socket.receive(packet);

                    long now = System.nanoTime();
                    if (now - rateWindowStarted >= 1_000_000_000L) {
                        rateWindowStarted = now;
                        packetsInWindow = 0;
                    }
                    if (++packetsInWindow > MAX_PACKETS_PER_SECOND) {
                        continue;
                    }

                    DiscoveredGame game = parse(packet);
                    if (game != null) {
                        notifySafely(onGameFound, game);
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException e) {
            if (listening && listeningSocket == socket) {
                notifySafely(onError, "Could not find " + displayName + " LAN games: " + e.getMessage());
            }
        } finally {
            if (socket != null) {
                socket.close();
            }
            if (listeningSocket == socket) {
                listeningSocket = null;
            }
        }
    }

    private String buildMessage(String playerName, int tcpPort) {
        String safePlayerName = SafeText.playerName(playerName, "Host");
        return NetworkMessage.make(
                PREFIX,
                gameType,
                sessionId,
                String.valueOf(tcpPort),
                safePlayerName,
                String.valueOf(System.currentTimeMillis()));
    }

    private void waitForNextAdvertisement() throws InterruptedException {
        synchronized (advertisingMonitor) {
            if (advertising) {
                advertisingMonitor.wait(TICK_MS);
            }
        }
    }

    private DiscoveredGame parse(DatagramPacket packet) {
        String message = message(packet);
        if (!NetworkMessage.isType(message, PREFIX)) {
            return null;
        }

        List<String> fields = NetworkMessage.fields(message);
        if (fields.size() != 5 || !gameType.equals(fields.getFirst()) || sessionId.equals(fields.get(1))) {
            return null;
        }

        try {
            int tcpPort = Integer.parseInt(fields.get(2));
            long timestamp = Long.parseLong(fields.get(4));
            long now = System.currentTimeMillis();
            if (tcpPort <= 0 || tcpPort > 65_535
                    || timestamp > now + TICK_MS
                    || timestamp < now - ADVERTISEMENT_TTL_MS
                    || fields.get(1).isBlank() || fields.get(1).length() > 64
                    || packet.getAddress() == null) {
                return null;
            }
            return new DiscoveredGame(
                    SafeText.playerName(fields.get(3), "Host"),
                    gameType,
                    packet.getAddress().getHostAddress(),
                    tcpPort,
                    fields.get(1),
                    timestamp);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String message(DatagramPacket packet) {
        return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
    }

    private Set<InetAddress> broadcastAddresses() throws SocketException, UnknownHostException {
        Set<InetAddress> addresses = new LinkedHashSet<>();
        addresses.add(InetAddress.getByName("255.255.255.255"));
        addresses.add(InetAddress.getByName("127.0.0.1"));

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            return addresses;
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }

            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                InetAddress broadcast = interfaceAddress.getBroadcast();
                if (broadcast != null) {
                    addresses.add(broadcast);
                }
            }
        }

        return addresses;
    }

    private static <T> void notifySafely(Consumer<T> callback, T value) {
        try {
            callback.accept(value);
        } catch (RuntimeException ignored) {
            // A UI callback must not terminate the long-running discovery thread.
        }
    }
}
