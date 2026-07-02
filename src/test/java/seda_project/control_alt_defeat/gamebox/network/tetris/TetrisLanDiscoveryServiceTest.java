package seda_project.control_alt_defeat.gamebox.network.tetris;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetrisLanDiscoveryServiceTest {

    @Test
    void tetrisAdvertisingIsDiscoverableOnLocalListener() throws Exception {
        assertDiscovery(LanDiscoveryService.tetris(), LanDiscoveryService.tetris(), "Host Alpha", "TETRIS");
    }

    @Test
    void memoryAdvertisingIsDiscoverableOnLocalListener() throws Exception {
        assertDiscovery(LanDiscoveryService.memory(), LanDiscoveryService.memory(), "Host Alpha", "MEMORY");
    }

    @Test
    void hexChessAdvertisingIsDiscoverableOnLocalListener() throws Exception {
        assertDiscovery(LanDiscoveryService.hexChess(), LanDiscoveryService.hexChess(), "Host Alpha", "HEX_CHESS");
    }

    private static void assertDiscovery(
            LanDiscoveryService advertiser,
            LanDiscoveryService listener,
            String playerName,
            String gameType) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LanDiscoveryService.DiscoveredGame> discovered = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        try {
            listener.startListening(game -> {
                discovered.compareAndSet(null, game);
                latch.countDown();
            }, error::set);

            advertiser.startAdvertising(playerName, 54321, error::set);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "expected LAN advertisement to be discovered");
            assertEquals(playerName, discovered.get().playerName());
            assertEquals(gameType, discovered.get().gameType());
            assertEquals(54321, discovered.get().tcpPort());
            assertNotNull(discovered.get().hostAddress());
            assertNull(error.get());
        } finally {
            advertiser.close();
            listener.close();
        }
    }
}
