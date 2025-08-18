WITH review_counts AS (SELECT app_id,
                              COALESCE(total_positive, 0) + COALESCE(total_negative, 0) as total_reviews
                       FROM steam_app_reviews),
     clusters AS (SELECT CASE
                             WHEN total_reviews = 0 THEN '0 reviews'
                             WHEN total_reviews BETWEEN 1 AND 100 THEN '1-100 reviews'
                             WHEN total_reviews BETWEEN 101 AND 1000 THEN '101-1000 reviews'
                             WHEN total_reviews BETWEEN 1001 AND 10000 THEN '1001-10000 reviews'
                             ELSE '10000+ reviews'
                             END                                                 AS review_cluster,
                         COUNT(*)                                                as cluster_count,
                         COUNT(*) * 100.0 / (SELECT COUNT(*) FROM review_counts) as percentage
                  FROM review_counts
                  GROUP BY CASE
                               WHEN total_reviews = 0 THEN '0 reviews'
                               WHEN total_reviews BETWEEN 1 AND 100 THEN '1-100 reviews'
                               WHEN total_reviews BETWEEN 101 AND 1000 THEN '101-1000 reviews'
                               WHEN total_reviews BETWEEN 1001 AND 10000 THEN '1001-10000 reviews'
                               ELSE '10000+ reviews'
                               END)
SELECT review_cluster,
       cluster_count,
       ROUND(percentage, 2) as percentage
FROM clusters
ORDER BY CASE review_cluster
             WHEN '0 reviews' THEN 1
             WHEN '1-100 reviews' THEN 2
             WHEN '101-1000 reviews' THEN 3
             WHEN '1001-10000 reviews' THEN 4
             WHEN '10000+ reviews' THEN 5
             END;
