package seda_project.control_alt_defeat.gamebox.network;

import java.io.Closeable;
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

public class LanDiscoveryService implements Closeable {

    private static final String PREFIX = "GAMEBOX_DISCOVERY";
    private static final int TETRIS_PORT = 54322;
    private static final int HEX_CHESS_PORT = 54324;
    private static final int TICK_MS = 1_000;
    private static final long ADVERTISEMENT_TTL_MS = 5_000;

    private final String gameType;
    private final int discoveryPort;
    private final String displayName;
    private final String threadPrefix;
    private final String sessionId = UUID.randomUUID().toString();
    private final Object advertisingMonitor = new Object();

    private volatile boolean advertising;
    private volatile boolean listening;
    private DatagramSocket advertisingSocket;
    private DatagramSocket listeningSocket;

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
        advertising = true;

        Thread thread = new Thread(() -> advertise(playerName, tcpPort, onError), threadPrefix + "-udp-advertise");
        thread.setDaemon(true);
        thread.start();
    }

    public void startListening(Consumer<DiscoveredGame> onGameFound, Consumer<String> onError) {
        stopListening();
        listening = true;

        Thread thread = new Thread(() -> listen(onGameFound, onError), threadPrefix + "-udp-listen");
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
        try (DatagramSocket socket = new DatagramSocket()) {
            advertisingSocket = socket;
            socket.setReuseAddress(true);
            socket.setBroadcast(true);

            while (advertising) {
                byte[] data = buildMessage(playerName, tcpPort).getBytes(StandardCharsets.UTF_8);
                for (InetAddress address : broadcastAddresses()) {
                    socket.send(new DatagramPacket(data, data.length, address, discoveryPort));
                }
                waitForNextAdvertisement();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (advertising) {
                onError.accept("Could not advertise " + displayName + " LAN game: " + e.getMessage());
            }
        }
    }

    private void listen(Consumer<DiscoveredGame> onGameFound, Consumer<String> onError) {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), discoveryPort));
            socket.setSoTimeout(TICK_MS);
            listeningSocket = socket;

            while (listening) {
                try {
                    byte[] data = new byte[512];
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    socket.receive(packet);

                    DiscoveredGame game = parse(packet);
                    if (game != null) {
                        onGameFound.accept(game);
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception e) {
            if (listening) {
                onError.accept("Could not find " + displayName + " LAN games: " + e.getMessage());
            }
        }
    }

    private String buildMessage(String playerName, int tcpPort) {
        String safePlayerName = playerName == null || playerName.isBlank() ? "Host" : playerName.trim();
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
            if (System.currentTimeMillis() - timestamp > ADVERTISEMENT_TTL_MS) {
                return null;
            }
            return new DiscoveredGame(
                    fields.get(3),
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
}
