package seda_project.control_alt_defeat.gamebox.network.tetris;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetrisLanDiscoveryServiceTest {

    @Test
    void advertisingIsDiscoverableOnLocalListener() throws Exception {
        TetrisLanDiscoveryService advertiser = new TetrisLanDiscoveryService();
        TetrisLanDiscoveryService listener = new TetrisLanDiscoveryService();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TetrisLanDiscoveryService.DiscoveredGame> discovered = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        try {
            listener.startListening(game -> {
                discovered.compareAndSet(null, game);
                latch.countDown();
            }, error::set);

            advertiser.startAdvertising("Host Alpha", 54321, error::set);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "expected LAN advertisement to be discovered");
            assertEquals("Host Alpha", discovered.get().playerName());
            assertEquals("TETRIS", discovered.get().gameType());
            assertEquals(54321, discovered.get().tcpPort());
            assertNotNull(discovered.get().hostAddress());
            assertNull(error.get());
        } finally {
            advertiser.close();
            listener.close();
        }
    }
}
