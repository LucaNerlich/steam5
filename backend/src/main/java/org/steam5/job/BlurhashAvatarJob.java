package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.service.BlurhashService;

import java.util.Optional;

@Component
@Slf4j
@DisallowConcurrentExecution
public class BlurhashAvatarJob implements Job {

    private final BlurhashService service;
    private final UserRepository userRepository;

    public BlurhashAvatarJob(final BlurhashService service, final UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        log.info("Job start BlurhashAvatarJob key={} fireTime={} scheduled={} refireCount={}", context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());

        final Object steamIdObj = context.getMergedJobDataMap() != null ? context.getMergedJobDataMap().get("steamId") : null;
        if (steamIdObj != null) {
            final String steamId = String.valueOf(steamIdObj);
            if (steamId != null) {
                try {
                    int encoded = encodeForSteamId(steamId);
                    log.info("Targeted BlurhashAvatarJob finished for steamId={} encoded={}", steamId, encoded);
                    return;
                } catch (Exception e) {
                    log.warn("Targeted BlurhashAvatarJob failed for steamId {}", steamId, e);
                    return;
                }
            }
        }

        log.error("SteamId job data parameter is missing");
    }

    public int encodeForSteamId(final String steamId) {
        int encoded = 0;
        final Optional<User> userOptional = userRepository.findById(steamId);
        if (userOptional.isEmpty()) {
            log.error("User not found for steamId {}", steamId);
            return encoded;
        }

        final User user = userOptional.get();
        final String avatar = user.getAvatar();
        final String avatarFull = user.getAvatarFull();

        if (StringUtils.isNotBlank(avatar)) {
            final BlurhashService.Encoded thumbEncoded = service.readAndEncode(avatar, BlurhashService.Type.THUMBNAIL);
            user.setBlurhashAvatar(thumbEncoded.hash());
            user.setBlurdataAvatar(thumbEncoded.dataUrl());
            userRepository.save(user);
            encoded++;
        }


        if (StringUtils.isNotBlank(avatarFull)) {
            final BlurhashService.Encoded fullEncoded = service.readAndEncode(avatarFull, BlurhashService.Type.FULL);
            user.setBlurhashAvatarFull(fullEncoded.hash());
            user.setBlurdataAvatarFull(fullEncoded.dataUrl());
            userRepository.save(user);
            encoded++;
        }

        log.info("Blurhash immediate finished for steamId={} encoded={} ", steamId, encoded);
        return encoded;
    }

    @Bean("BlurhashAvatarJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob()
                .ofType(BlurhashAvatarJob.class)
                .storeDurably()
                .withIdentity("BlurhashAvatarJob")
                .build();
    }
}
