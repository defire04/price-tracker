package com.price.tracker.domain.service.user.impl;

import com.price.tracker.domain.entity.User;
import com.price.tracker.domain.model.TelegramProfile;
import com.price.tracker.domain.repository.UserRepository;
import com.price.tracker.domain.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User registerIfAbsent(long telegramChatId) {
        return userRepository.findByTelegramChatId(telegramChatId)
                .orElseGet(() -> createUser(telegramChatId));
    }

    @Override
    @Transactional
    public User registerOrUpdate(long telegramChatId, TelegramProfile profile) {
        User user = registerIfAbsent(telegramChatId);
        user.setUsername(profile.username());
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setLanguageCode(profile.languageCode());
        user.setLastSeenAt(Instant.now());
        return user;
    }

    private User createUser(long telegramChatId) {
        User user = new User();
        user.setTelegramChatId(telegramChatId);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }
}
