package com.price.tracker.domain.repository;

import com.price.tracker.domain.entity.PriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findTopByItemIdOrderByCheckedAtDesc(Long itemId);
}
