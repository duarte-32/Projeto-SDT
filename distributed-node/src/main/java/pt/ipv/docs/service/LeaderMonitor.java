package pt.ipv.docs.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LeaderMonitor {

    private final AtomicLong lastLeaderMessage = new AtomicLong(0);
    private final AtomicBoolean failureDetected = new AtomicBoolean(false);

    public void messageReceived() {
        lastLeaderMessage.set(System.currentTimeMillis());

        if (failureDetected.compareAndSet(true, false)) {
            System.out.println("[peer] O líder voltou a comunicar.");
        }
    }

    public long lastLeaderMessage() {
        return lastLeaderMessage.get();
    }

    public boolean detectFailureOnce() {
        return failureDetected.compareAndSet(false, true);
    }

    public boolean failureDetected() {
        return failureDetected.get();
    }
}