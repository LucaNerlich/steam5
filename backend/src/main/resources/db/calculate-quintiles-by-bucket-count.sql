-- Quintile buckets (equal number of apps per bucket)
WITH r AS (
    SELECT app_id, (total_positive + total_negative) AS total
    FROM steam_app_reviews
    WHERE (total_positive + total_negative) > 0
),
     q AS (
         SELECT app_id, total, ntile(5) OVER (ORDER BY total) AS bucket
         FROM r
     ),
     b AS (
         SELECT bucket AS idx,
                MIN(total) AS lower,
                MAX(total) AS upper,
                COUNT(*)   AS count_in_bucket
         FROM q
         GROUP BY bucket
     )
SELECT idx,
       lower,
       upper,
       (lower::bigint || '-' || upper::bigint) AS label,
       count_in_bucket
FROM b
ORDER BY idx;
