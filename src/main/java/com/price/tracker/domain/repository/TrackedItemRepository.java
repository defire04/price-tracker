package com.price.tracker.domain.repository;

import com.price.tracker.domain.entity.TrackedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrackedItemRepository extends JpaRepository<TrackedItem, Long> {

    @Query("select distinct i from TrackedItem i join fetch i.events join fetch i.user where i.active = true")
    List<TrackedItem> findAllActiveWithEvents();

    @Query("""
            select distinct i from TrackedItem i join fetch i.events
            where i.user.telegramChatId = :telegramChatId and i.active = true
            order by i.id""")
    List<TrackedItem> findActiveByChatIdWithEvents(long telegramChatId);

    List<TrackedItem> findTop20ByUserTelegramChatIdAndActiveFalseOrderByIdDesc(long telegramChatId);

    @Query("""
            select i from TrackedItem i join fetch i.events
            where i.id = :itemId and i.user.telegramChatId = :telegramChatId""")
    Optional<TrackedItem> findByIdAndChatIdWithEvents(long itemId, long telegramChatId);

    Optional<TrackedItem> findByIdAndUserTelegramChatId(Long id, long telegramChatId);
}
