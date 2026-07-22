package seda_project.control_alt_defeat.gamebox.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class LanDiscoveryServiceTest {

    @Test
    void separateInstancesDiscoverALocalLanAdvertisement() throws Exception {
        LanDiscoveryService listener = LanDiscoveryService.tetris();
        LanDiscoveryService advertiser = LanDiscoveryService.tetris();
        CountDownLatch found = new CountDownLatch(1);
        AtomicReference<LanDiscoveryService.DiscoveredGame> discovered = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        try {
            listener.startListening(game -> {
                discovered.compareAndSet(null, game);
                found.countDown();
            }, error::set);
            advertiser.startAdvertising("Alice\nHost", 54321, error::set);

            assertTrue(found.await(7, TimeUnit.SECONDS), () -> "Discovery timed out: " + error.get());
            LanDiscoveryService.DiscoveredGame game = discovered.get();
            assertNotNull(game);
            assertEquals("Alice Host", game.playerName());
            assertEquals("TETRIS", game.gameType());
            assertEquals(54321, game.tcpPort());
            assertFalse(game.hostAddress().isBlank());
            assertFalse(game.sessionId().isBlank());
            assertTrue(game.toString().contains(game.hostAddress()));
        } finally {
            advertiser.close();
            listener.close();
        }
    }

    @Test
    void invalidAdvertisementPortsReportAnErrorWithoutStarting() {
        LanDiscoveryService service = LanDiscoveryService.memory();
        AtomicReference<String> error = new AtomicReference<>();
        try {
            service.startAdvertising("Host", 0, error::set);
            assertNotNull(error.get());
            assertTrue(error.get().contains("invalid TCP port"));

            error.set(null);
            service.startAdvertising("Host", 65_536, error::set);
            assertNotNull(error.get());
        } finally {
            service.close();
        }
    }

    @Test
    void tcpEndpointsRejectInvalidHostsAndPortsBeforeOpeningSockets() {
        GameClient client = new GameClient();
        GameServer server = new GameServer();
        try {
            assertThrows(IOException.class, () -> client.connect("", 54321, null, null));
            assertThrows(IOException.class, () -> client.connect("127.0.0.1", 0, null, null));
            assertThrows(IOException.class, () -> client.connect("127.0.0.1", 65_536, null, null));
            assertThrows(IOException.class, () -> server.listen(-1, null, null));
            assertThrows(IOException.class, () -> server.listen(65_536, null, null));
            assertFalse(client.isConnected());
            assertFalse(server.isConnected());
        } finally {
            client.close();
            server.close();
        }
    }
}
