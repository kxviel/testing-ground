package seda_project.control_alt_defeat.gamebox.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessProtocol;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisProtocol;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisStateSnapshot;

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
        int port = server.localPort();
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
            client.connect("127.0.0.1", port, clientMessages::add, () -> {
            });
            assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());
            assertThrows(IOException.class, () -> {
                try (Socket ignored = new Socket("127.0.0.1", port)) {
                }
            });

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
    void legitimateRealtimeBurstDoesNotDisconnectLanClient() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();
        CountDownLatch serverAccepted = new CountDownLatch(1);
        CountDownLatch clientDisconnected = new CountDownLatch(1);
        AtomicReference<IOException> serverError = new AtomicReference<>();

        server.listen(0, message -> {
        }, () -> {
        });
        Thread acceptThread = new Thread(() -> {
            try {
                server.waitForClient();
            } catch (IOException e) {
                serverError.set(e);
            } finally {
                serverAccepted.countDown();
            }
        }, "game-network-realtime-burst-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        try {
            client.connect("127.0.0.1", server.localPort(), clientMessages::add, clientDisconnected::countDown);
            assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());

            int messageCount = 60;
            for (int index = 0; index < messageCount; index++) {
                server.send(MemoryProtocol.state("snapshot-" + index));
                Thread.sleep(10);
            }

            for (int index = 0; index < messageCount; index++) {
                assertNotNull(clientMessages.poll(5, TimeUnit.SECONDS));
            }
            assertEquals(1, clientDisconnected.getCount());
            assertTrue(client.isConnected());
            assertTrue(server.isConnected());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    void completeSnapshotsForAllGamesSurviveBidirectionalTcpTransport() throws Exception {
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
        }, "all-games-snapshot-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        try {
            client.connect("127.0.0.1", server.localPort(), clientMessages::add, () -> {
            });
            assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());

            GameModel memory = new GameModel(new BoardVariant(2, 1, "LAN Test"));
            TetrisGameConfig tetrisConfig = TetrisGameConfig.defaultConfig();
            TetrisGameState tetris = TetrisGameState.create(
                    new TetrisGameSetup("Host", "Joiner", tetrisConfig)).running();
            HexGameState hexChess = HexGameState.standard();

            server.send(MemoryProtocol.state(MemoryStateSnapshot.serialize(memory)));
            server.send(TetrisProtocol.state(TetrisStateSnapshot.serialize(tetris, 15_000)));
            server.send(HexChessProtocol.state(
                    HexChessStateSnapshot.serialize(hexChess, "Host", "Joiner")));

            String memoryMessage = clientMessages.poll(5, TimeUnit.SECONDS);
            String tetrisMessage = clientMessages.poll(5, TimeUnit.SECONDS);
            String hexMessage = clientMessages.poll(5, TimeUnit.SECONDS);
            assertNotNull(memoryMessage);
            assertNotNull(tetrisMessage);
            assertNotNull(hexMessage);

            GameModel restoredMemory = MemoryStateSnapshot.deserialize(
                    MemoryProtocol.fields(memoryMessage).getFirst());
            TetrisStateSnapshot.SnapshotData restoredTetris = TetrisStateSnapshot.deserializeWithTiming(
                    TetrisProtocol.fields(tetrisMessage).getFirst(), tetrisConfig);
            HexChessStateSnapshot.MatchSnapshot restoredHex = HexChessStateSnapshot.deserializeMatch(
                    HexChessProtocol.fields(hexMessage).getFirst());

            assertEquals(2, restoredMemory.getCards().size());
            assertEquals(15_000, restoredTetris.elapsedGameMillis());
            assertEquals("Host", restoredHex.whiteName());
            assertEquals("Joiner", restoredHex.blackName());

            client.send(MemoryProtocol.flip(1));
            client.send(TetrisProtocol.input(PlayerSide.TOP, TetrisProtocol.MOVE_LEFT));
            client.send(HexChessProtocol.simple(HexChessProtocol.DRAW_OFFER));

            assertEquals(MemoryProtocol.FLIP,
                    MemoryProtocol.type(serverMessages.poll(5, TimeUnit.SECONDS)));
            assertEquals(TetrisProtocol.INPUT,
                    TetrisProtocol.type(serverMessages.poll(5, TimeUnit.SECONDS)));
            assertEquals(HexChessProtocol.DRAW_OFFER,
                    HexChessProtocol.type(serverMessages.poll(5, TimeUnit.SECONDS)));
            assertTrue(client.isConnected());
            assertTrue(server.isConnected());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    void repeatedConnectExchangeAndDisconnectCyclesReleaseLanResources() throws Exception {
        for (int cycle = 0; cycle < 25; cycle++) {
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
            }, "reconnect-cycle-" + cycle);
            acceptThread.setDaemon(true);
            acceptThread.start();

            try {
                client.connect("127.0.0.1", server.localPort(), clientMessages::add, () -> {
                });
                assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
                assertNull(serverError.get());

                String clientToHost = MemoryProtocol.flip(cycle);
                String hostToClient = MemoryProtocol.state("cycle-" + cycle);
                client.send(clientToHost);
                server.send(hostToClient);

                assertEquals(clientToHost, serverMessages.poll(5, TimeUnit.SECONDS));
                assertEquals(hostToClient, clientMessages.poll(5, TimeUnit.SECONDS));
            } finally {
                client.close();
                server.close();
            }

            assertFalse(client.isConnected());
            assertFalse(server.isConnected());
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
