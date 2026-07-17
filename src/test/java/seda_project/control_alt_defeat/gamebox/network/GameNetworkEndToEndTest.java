package seda_project.control_alt_defeat.gamebox.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;

class GameNetworkEndToEndTest {

    @Test
    void displayedHostAddressIsAConcreteIpv4Address() throws Exception {
        InetAddress address = InetAddress.getByName(GameServer.getLocalAddress());

        assertTrue(address instanceof Inet4Address);
        assertFalse(address.isAnyLocalAddress());
    }

    @Test
    void clientAndServerExchangeOneProtocolMessage() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();
        CountDownLatch serverAccepted = new CountDownLatch(1);
        AtomicReference<IOException> serverError = new AtomicReference<>();

        server.listen(0, serverMessages::add, () -> {
        });
        Thread acceptThread = new Thread(() -> {
            try {
                server.waitForClient();
            } catch (IOException e) {
                serverError.set(e);
            } finally {
                serverAccepted.countDown();
            }
        }, "game-network-smoke-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        try {
            client.connect("127.0.0.1", server.localPort(), clientMessages::add, () -> {
            });
            assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());

            String join = MemoryProtocol.join("Player Two");
            client.send(join);

            String received = serverMessages.poll(5, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals(MemoryProtocol.JOIN, MemoryProtocol.type(received));
            assertEquals(List.of("Player Two"), MemoryProtocol.fields(received));
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    void gracefulCloseDeliversFinalMessageBeforeDisconnecting() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        CountDownLatch serverAccepted = new CountDownLatch(1);
        CountDownLatch serverDisconnected = new CountDownLatch(1);
        List<String> serverEvents = new CopyOnWriteArrayList<>();
        AtomicReference<IOException> serverError = new AtomicReference<>();

        server.listen(0, message -> {
            serverMessages.add(message);
            serverEvents.add("message:" + message);
        }, () -> {
            serverEvents.add("disconnect");
            serverDisconnected.countDown();
        });
        Thread acceptThread = new Thread(() -> {
            try {
                server.waitForClient();
            } catch (IOException e) {
                serverError.set(e);
            } finally {
                serverAccepted.countDown();
            }
        }, "game-network-graceful-close-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        try {
            client.connect("127.0.0.1", server.localPort(), message -> {
            }, () -> {
            });
            assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());

            String quit = MemoryProtocol.quit("Player Two");
            client.closeAfterSending(quit);

            assertEquals(quit, serverMessages.poll(5, TimeUnit.SECONDS));
            assertTrue(serverDisconnected.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("message:" + quit, "disconnect"), serverEvents);
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    void gracefulHostCloseDeliversFinalMessageAndReleasesListeningPort() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();
        CountDownLatch serverAccepted = new CountDownLatch(1);
        AtomicReference<IOException> serverError = new AtomicReference<>();

        server.listen(0, message -> {
        }, () -> {
        });
        int port = server.localPort();
        Thread acceptThread = new Thread(() -> {
            try {
                server.waitForClient();
            } catch (IOException e) {
                serverError.set(e);
            } finally {
                serverAccepted.countDown();
            }
        }, "game-network-host-graceful-close-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        try {
            client.connect("127.0.0.1", port, clientMessages::add, () -> {
            });
            assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());

            String quit = MemoryProtocol.quit("Player One");
            server.closeAfterSending(quit);

            assertEquals(quit, clientMessages.poll(5, TimeUnit.SECONDS));
            try (ServerSocket rebound = new ServerSocket(port)) {
                assertEquals(port, rebound.getLocalPort());
            }
        } finally {
            client.close();
            server.close();
        }
    }
}
