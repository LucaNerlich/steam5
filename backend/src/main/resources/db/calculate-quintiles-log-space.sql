WITH RECURSIVE
    r AS (
        SELECT (total_positive + total_negative) AS total
        FROM steam_app_reviews
        WHERE (total_positive + total_negative) > 0
    ),
    ext AS (
        SELECT
            MIN(total)::bigint AS mn,
            CAST(percentile_disc(0.995) WITHIN GROUP (ORDER BY total) AS bigint) AS cap
        FROM r
    ),
    log_params AS (
        SELECT LN(mn + 1.0) AS min_log, LN(cap + 1.0) AS max_log FROM ext
    ),
    edges_raw AS (
        SELECT i,
               FLOOR(EXP(min_log + i * ((max_log - min_log) / 5.0)) - 1)::bigint AS raw_edge
        FROM log_params, generate_series(0,5) AS i
    ),
    edges AS (
        SELECT i, raw_edge AS edge
        FROM edges_raw
        WHERE i = 0
        UNION ALL
        SELECT er.i, GREATEST(er.raw_edge, e.edge + 1) AS edge
        FROM edges e
                 JOIN edges_raw er ON er.i = e.i + 1
    ),
    bounds AS (
        SELECT
            e0.i AS idx,                              -- 0..4
            e0.edge AS lower,
            /* force last bucket's upper to NULL (open end) */
            CASE WHEN e0.i < 4 THEN (e1.edge - 1) ELSE NULL END AS upper
        FROM edges e0
                 LEFT JOIN edges e1 ON e1.i = e0.i + 1
        WHERE e0.i BETWEEN 0 AND 4
    ),
    counts AS (
        SELECT
            b.idx + 1 AS bucket,                      -- 1..5
            b.lower,
            b.upper,
            COUNT(
                    CASE
                        WHEN b.upper IS NOT NULL AND r.total BETWEEN b.lower AND b.upper THEN 1
                        WHEN b.upper IS NULL      AND r.total >= b.lower                  THEN 1
                        END
            ) AS count_in_bucket
        FROM bounds b
                 CROSS JOIN r
        GROUP BY b.idx, b.lower, b.upper
    )
SELECT
    bucket,
    lower,
    upper,  -- last bucket upper is NULL
    CASE WHEN upper IS NOT NULL
             THEN lower::text || '-' || upper::text
         ELSE '≥ ' || lower::text
        END AS label,
    count_in_bucket
FROM counts
ORDER BY bucket;
