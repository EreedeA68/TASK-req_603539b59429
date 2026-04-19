package com.meridianmart.scheduler;

import com.meridianmart.model.User;
import com.meridianmart.repository.DisputeRepository;
import com.meridianmart.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final UserRepository userRepository;
    private final DisputeRepository disputeRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void anonymizeInactiveUsers() {
        LocalDateTime start = LocalDateTime.now();
        log.info("Data retention job started at {}", start);

        LocalDateTime cutoff = LocalDateTime.now().minusMonths(24);
        List<User> inactiveUsers = userRepository.findInactiveShoppers(cutoff);

        List<Long> usersWithOpenDisputes = disputeRepository.findUserIdsWithOpenDisputes();
        Set<Long> disputeSet = Set.copyOf(usersWithOpenDisputes);

        int anonymized = 0;
        for (User user : inactiveUsers) {
            if (!disputeSet.contains(user.getId())) {
                user.setEmail("anonymized-" + user.getId() + "@deleted.local");
                user.setUsername("anonymized-" + user.getId());
                user.setPasswordHash("ANONYMIZED");
                user.setAnonymized(true);
                userRepository.save(user);
                anonymized++;
            }
        }

        log.info("Data retention job complete: anonymized {} users out of {} candidates at {}",
                anonymized, inactiveUsers.size(), LocalDateTime.now());
    }
}
