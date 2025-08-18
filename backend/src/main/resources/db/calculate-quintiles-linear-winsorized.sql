WITH r AS (
    SELECT (total_positive + total_negative) AS total
    FROM steam_app_reviews
    WHERE (total_positive + total_negative) > 0
),
     ext AS (
         SELECT
             MIN(total)::bigint                        AS mn,
             CAST(percentile_disc(0.995) WITHIN GROUP (ORDER BY total) AS bigint) AS cap
         FROM r
     ),
     bins AS (
         SELECT gs AS idx, mn, cap,
                CEIL(((cap - mn + 1)::numeric) / 5.0)::bigint AS width
         FROM ext, generate_series(1, 5) AS gs
     ),
     bounds AS (
         SELECT
             idx,
             (mn + (idx - 1) * width) AS lower,
             CASE WHEN idx < 5 THEN (mn + idx * width - 1) ELSE cap END AS upper
         FROM bins
     ),
     counts AS (
         SELECT
             b.idx,
             b.lower,
             b.upper,
             COUNT(CASE
                       WHEN b.idx < 5 AND r.total BETWEEN b.lower AND b.upper THEN 1
                       WHEN b.idx = 5 AND r.total >= b.lower THEN 1
                 END) AS count_in_bucket
         FROM bounds b
                  CROSS JOIN r
         GROUP BY b.idx, b.lower, b.upper
     )
SELECT
    idx,
    lower,
    CASE WHEN idx < 5 THEN upper ELSE NULL END AS upper,
    CASE
        WHEN idx < 5 THEN (lower::text || '-' || upper::text)
        ELSE ('≥ ' || lower::text)
        END AS label,
    count_in_bucket
FROM counts
ORDER BY idx;
