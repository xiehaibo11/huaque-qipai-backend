package com.nanbei.entertainment.backend.friend.application;

import com.nanbei.entertainment.backend.friend.domain.FriendPresenceState;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendPresenceService {
    static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);

    private final UserRepository userRepository;

    public FriendPresenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void touch(UUID userId) {
        userRepository
                .findById(userId)
                .ifPresent(
                        user -> {
                            user.setLastActiveAt(Instant.now());
                            userRepository.save(user);
                        });
    }

    public boolean isOnline(Instant lastActiveAt, Instant now) {
        return lastActiveAt != null
                && !lastActiveAt.isBefore(now.minus(ONLINE_WINDOW));
    }

    public FriendPresenceState stateOf(Instant lastActiveAt) {
        return isOnline(lastActiveAt, Instant.now())
                ? FriendPresenceState.ONLINE
                : FriendPresenceState.OFFLINE;
    }

    public Instant onlineSince() {
        return Instant.now().minus(ONLINE_WINDOW);
    }
}
