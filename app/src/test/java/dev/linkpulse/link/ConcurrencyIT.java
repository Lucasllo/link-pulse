package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linkpulse.AbstractIT;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Criações concorrentes nunca geram código duplicado (LINK-02).
 */
class ConcurrencyIT extends AbstractIT {

    private static final int THREADS = 8;
    private static final int PER_THREAD = 50;

    @Autowired
    private LinkService linkService;

    @Autowired
    private LinkRepository links;

    @Test
    void concurrentCreationsProduceDistinctCodes() throws Exception {
        final long before = links.count();
        Set<String> codes = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                futures.add(executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < PER_THREAD; i++) {
                            int n = thread * PER_THREAD + i;
                            Link link = linkService.create(new CreateLinkRequest(
                                    "https://example.com/c/" + n, null, null));
                            codes.add(link.getCode());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        errors.add(e);
                    } catch (RuntimeException e) {
                        errors.add(e);
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(errors).isEmpty();
        assertThat(codes).hasSize(THREADS * PER_THREAD);
        assertThat(links.count() - before).isEqualTo(THREADS * PER_THREAD);
    }
}
