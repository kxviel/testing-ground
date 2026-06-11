package seda_project.control_alt_defeat.gamebox.network.hexchess;

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
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class HexChessLanDiscoveryService implements Closeable {

    public static final int DISCOVERY_PORT = 54324;

    private static final String PREFIX = "GAMEBOX_DISCOVERY";
    private static final String GAME_TYPE = "HEX_CHESS";
    private static final int TIMEOUT_MS = 1_000;

    private final String sessionId = UUID.randomUUID().toString();

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

    public void startAdvertising(String playerName, int tcpPort, Consumer<String> onError) {
        stopAdvertising();
        advertising = true;

        Thread thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                advertisingSocket = socket;
                socket.setReuseAddress(true);
                socket.setBroadcast(true);

                while (advertising) {
                    byte[] data = buildMessage(playerName, tcpPort).getBytes(StandardCharsets.UTF_8);
                    for (InetAddress address : broadcastAddresses()) {
                        DatagramPacket packet = new DatagramPacket(data, data.length, address, DISCOVERY_PORT);
                        socket.send(packet);
                    }
                    Thread.sleep(TIMEOUT_MS);
                }
            } catch (Exception e) {
                if (advertising) {
                    onError.accept("Could not advertise Hex Chess game: " + e.getMessage());
                }
            }
        }, "hexchess-udp-advertise");

        thread.setDaemon(true);
        thread.start();
    }

    public void startListening(Consumer<DiscoveredGame> onGameFound, Consumer<String> onError) {
        stopListening();
        listening = true;

        Thread thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_PORT));
                socket.setSoTimeout(TIMEOUT_MS);
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
                    onError.accept("Could not find Hex Chess LAN games: " + e.getMessage());
                }
            }
        }, "hexchess-udp-listen");

        thread.setDaemon(true);
        thread.start();
    }

    public void stopAdvertising() {
        advertising = false;
        if (advertisingSocket != null) {
            advertisingSocket.close();
            advertisingSocket = null;
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

    private String buildMessage(String playerName, int tcpPort) {
        String safePlayerName = playerName == null || playerName.isBlank() ? "Host" : playerName.trim();
        String name = Base64.getEncoder().encodeToString(safePlayerName.getBytes(StandardCharsets.UTF_8));
        return PREFIX + ":" + GAME_TYPE + ":" + sessionId + ":" + tcpPort + ":" + name + ":"
                + System.currentTimeMillis();
    }

    private DiscoveredGame parse(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        String[] parts = message.split(":", 6);

        if (parts.length != 6
                || !PREFIX.equals(parts[0])
                || !GAME_TYPE.equals(parts[1])
                || sessionId.equals(parts[2])) {
            return null;
        }

        try {
            String name = new String(Base64.getDecoder().decode(parts[4]), StandardCharsets.UTF_8);
            int tcpPort = Integer.parseInt(parts[3]);
            Long.parseLong(parts[5]);
            return new DiscoveredGame(
                    name,
                    GAME_TYPE,
                    packet.getAddress().getHostAddress(),
                    tcpPort,
                    parts[2],
                    System.currentTimeMillis());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
