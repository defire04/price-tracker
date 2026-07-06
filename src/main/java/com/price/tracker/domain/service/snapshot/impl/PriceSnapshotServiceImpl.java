package com.price.tracker.domain.service.snapshot.impl;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.repository.PriceSnapshotRepository;
import com.price.tracker.domain.service.snapshot.PriceSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceSnapshotServiceImpl implements PriceSnapshotService {

    private final PriceSnapshotRepository priceSnapshotRepository;

    @Override
    public Optional<PriceSnapshot> lastOf(long itemId) {
        return priceSnapshotRepository.findTopByItemIdOrderByCheckedAtDesc(itemId);
    }

    @Override
    public List<PriceSnapshot> historyOf(long itemId) {
        return priceSnapshotRepository.findAllByItemIdOrderByCheckedAtAsc(itemId);
    }

    @Override
    public void record(PriceSnapshot snapshot) {
        priceSnapshotRepository.save(snapshot);
    }
}
