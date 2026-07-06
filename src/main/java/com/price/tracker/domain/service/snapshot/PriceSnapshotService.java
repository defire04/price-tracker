package com.price.tracker.domain.service.snapshot;

import com.price.tracker.domain.entity.PriceSnapshot;

import java.util.List;
import java.util.Optional;

public interface PriceSnapshotService {

    Optional<PriceSnapshot> lastOf(long itemId);

    List<PriceSnapshot> historyOf(long itemId);

    void record(PriceSnapshot snapshot);
}
