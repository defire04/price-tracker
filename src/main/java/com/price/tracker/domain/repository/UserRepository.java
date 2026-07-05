package com.price.tracker.domain.repository;

import com.price.tracker.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramChatId(long telegramChatId);
}
