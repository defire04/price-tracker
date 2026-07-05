package com.price.tracker.interfaces.telegram.wizard;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TrackWizardStore {

    private static final Duration WIZARD_TTL = Duration.ofMinutes(15);

    private final ConcurrentHashMap<Long, TrackWizardState> statesByChatId = new ConcurrentHashMap<>();

    public void put(long chatId, TrackWizardState state) {
        statesByChatId.put(chatId, state);
    }

    public Optional<TrackWizardState> find(long chatId) {
        TrackWizardState state = statesByChatId.get(chatId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.getStartedAt().plus(WIZARD_TTL).isBefore(Instant.now())) {
            statesByChatId.remove(chatId);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    public void remove(long chatId) {
        statesByChatId.remove(chatId);
    }
}
