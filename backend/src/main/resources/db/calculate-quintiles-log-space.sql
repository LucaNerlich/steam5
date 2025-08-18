WITH r AS (
    SELECT (total_positive + total_negative) AS total
    FROM steam_app_reviews
    WHERE (total_positive + total_negative) > 0
),
     ext AS (
         SELECT
             MIN(total)::bigint                        AS mn,
             CAST(percentile_disc(0.995) WITHIN GROUP (ORDER BY total) AS bigint) AS cap  -- tune 0.995/0.999
         FROM r
     ),
     log_params AS (
         SELECT
             LN(mn + 1.0)              AS min_log,
             LN(cap + 1.0)             AS max_log
         FROM ext
     ),
     bins AS (
         SELECT
             gs AS idx,
             min_log,
             max_log,
             ((max_log - min_log) / 5.0) AS w
         FROM log_params, generate_series(1, 5) AS gs
     ),
     bounds AS (
         SELECT
             idx,
             (min_log + (idx - 1) * w) AS lower_log,
             CASE WHEN idx < 5 THEN (min_log + idx * w) ELSE max_log END AS upper_log
         FROM bins
     ),
     bounds_lin AS (
         SELECT
             idx,
             GREATEST(0, FLOOR(EXP(lower_log) - 1))::bigint AS lower,
             CEIL(EXP(upper_log) - 1)::bigint               AS upper
         FROM bounds
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
         FROM bounds_lin b
                  CROSS JOIN r
         GROUP BY b.idx, b.lower, b.upper
     )
SELECT
    idx,
    lower,
    CASE WHEN idx < 5 THEN upper ELSE NULL END AS upper,   -- last bucket is open-ended
    CASE
        WHEN idx < 5 THEN (lower::text || '-' || upper::text)
        ELSE ('≥ ' || lower::text)
        END AS label,
    count_in_bucket
FROM counts
ORDER BY idx;
