package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SteamAppDetailRepository detailRepository;
    private final ReviewsBucketRepository reviewsBucketRepository;

    @Cacheable(value = "stats-long", key = "'stats-genres-'+#limit", unless = "#result == null")
    public List<LabelCount> topGenres(int limit) {
        return detailRepository.topGenres(limit).stream()
                .map(p -> new LabelCount(p.getLabel(), p.getCount()))
                .toList();
    }

    @Cacheable(value = "stats-long", key = "'stats-categories-'+#limit", unless = "#result == null")
    public List<LabelCount> topCategories(int limit) {
        return detailRepository.topCategories(limit).stream()
                .map(p -> new LabelCount(p.getLabel(), p.getCount()))
                .toList();
    }

    @Cacheable(value = "stats-long", key = "'stats-review-buckets-'+#mode", unless = "#result == null")
    public List<Bucket> reviewBuckets(BucketMode mode) {
        return switch (mode) {
            case EQUAL_WIDTH -> reviewsBucketRepository.equalWidth().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
            case EQUAL_COUNT -> reviewsBucketRepository.equalCount().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
            case LINEAR_WINSORIZED -> reviewsBucketRepository.linearWinsorized().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
            case LOG_SPACE -> reviewsBucketRepository.logSpace().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
        };
    }

    public enum BucketMode {EQUAL_WIDTH, EQUAL_COUNT, LINEAR_WINSORIZED, LOG_SPACE}

    public record LabelCount(String label, long count) {
    }

    public record Bucket(int bucket, Long lower, Long upper, String label, long count) {
    }
}


