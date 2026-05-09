package seda_project.control_alt_defeat.gamebox.network.tetris;

import java.io.Closeable;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

public class TetrisLanDiscoveryService implements Closeable {

    public static final int DISCOVERY_PORT = 54322;

    private static final String PREFIX = "ZETRIS_DISCOVERY";
    private static final int TIMEOUT_MS = 1_000;

    private final String sessionId = UUID.randomUUID().toString();

    private volatile boolean advertising;
    private volatile boolean listening;
    private DatagramSocket advertisingSocket;
    private DatagramSocket listeningSocket;

    public record DiscoveredGame(String playerName, String hostAddress, int tcpPort, String sessionId) {

        @Override
        public String toString() {
            return playerName + " - " + hostAddress;
        }
    }

    public void startAdvertising(String playerName, int tcpPort, Consumer<String> onError) {
        stopAdvertising();
        advertising = true;

        Thread thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                advertisingSocket = socket;
                socket.setBroadcast(true);

                while (advertising) {
                    byte[] data = buildMessage(playerName, tcpPort).getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(
                            data,
                            data.length,
                            InetAddress.getByName("255.255.255.255"),
                            DISCOVERY_PORT);
                    socket.send(packet);
                    Thread.sleep(TIMEOUT_MS);
                }
            } catch (Exception e) {
                if (advertising) {
                    onError.accept("Could not advertise LAN game: " + e.getMessage());
                }
            }
        }, "tetris-udp-advertise");

        thread.setDaemon(true);
        thread.start();
    }

    public void startListening(Consumer<DiscoveredGame> onGameFound, Consumer<String> onError) {
        stopListening();
        listening = true;

        Thread thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(DISCOVERY_PORT));
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
                    onError.accept("Could not find LAN games: " + e.getMessage());
                }
            }
        }, "tetris-udp-listen");

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
        String name = Base64.getEncoder().encodeToString(playerName.getBytes(StandardCharsets.UTF_8));
        return PREFIX + ":" + sessionId + ":" + tcpPort + ":" + name;
    }

    private DiscoveredGame parse(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        String[] parts = message.split(":", 4);

        if (parts.length != 4 || !PREFIX.equals(parts[0]) || sessionId.equals(parts[1])) {
            return null;
        }

        try {
            String name = new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8);
            int tcpPort = Integer.parseInt(parts[2]);
            String hostAddress = packet.getAddress().getHostAddress();
            return new DiscoveredGame(name, hostAddress, tcpPort, parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
