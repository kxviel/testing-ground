package seda_project.control_alt_defeat.gamebox.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;

class GameNetworkEndToEndTest {

    @Test
    void clientAndServerExchangeOneProtocolMessage() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();
        CountDownLatch serverAccepted = new CountDownLatch(1);
        AtomicReference<Exception> serverError = new AtomicReference<>();

        server.listen(0, serverMessages::add, () -> {
        });
        Thread acceptThread = new Thread(() -> {
            try {
                server.waitForClient();
            } catch (Exception e) {
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
}
