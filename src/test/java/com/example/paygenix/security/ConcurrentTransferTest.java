package com.example.paygenix.security;

import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.InsufficientBalanceException;
import com.example.paygenix.service.TransferService;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// SEC-12 / FLOW-2: concurrent debits must neither overspend nor lose updates, and opposite
// transfers between the same two cards must not deadlock.
class ConcurrentTransferTest extends IntegrationTest {

    private static final int THREADS = 8;

    @Autowired TransferService transferService;

    @Test
    void parallelDebitsNeverOverspendOrLoseUpdates() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "51.00"); // room for exactly 5 transfers of 10.00 + 2% commission

        List<Boolean> results = runConcurrently(THREADS, i -> () -> transfer(sender, from, to, "10.00"));

        long succeeded = results.stream().filter(ok -> ok).count();
        assertThat(succeeded).isEqualTo(5);
        assertThat(balanceOf(from)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("50.00");
        assertBooksBalanced();
    }

    @Test
    void oppositeTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        User a = createUser();
        User b = createUser();
        String cardA = issueCard(a, "AZN");
        String cardB = issueCard(b, "AZN");
        setBalance(cardA, "1000.00");
        setBalance(cardB, "1000.00");

        List<Boolean> results = runConcurrently(THREADS, i -> i % 2 == 0
                ? () -> transfer(a, cardA, cardB, "10.00")
                : () -> transfer(b, cardB, cardA, "10.00"));

        assertThat(results).containsOnly(true);
        // each of the 8 transfers moves 10.00 and pays 0.20 commission to FEE_REVENUE
        BigDecimal total = balanceOf(cardA).add(balanceOf(cardB));
        assertThat(total).isEqualByComparingTo(new BigDecimal("2000.00").subtract(new BigDecimal("1.60")));
        assertBooksBalanced();
    }

    private boolean transfer(User caller, String from, String to, String amount) {
        try {
            transferService.transfer(caller, new TransferRequest(from, to, new BigDecimal(amount), PIN));
            return true;
        } catch (InsufficientBalanceException rejected) {
            return false;
        }
    }

    private List<Boolean> runConcurrently(int n, java.util.function.IntFunction<Callable<Boolean>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<Boolean> t = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return t.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
