SELECT reviews.*, index.name
FROM steam_app_reviews reviews
         JOIN steam_app_index index ON reviews.app_id = index.app_id
WHERE reviews.total_positive + reviews.total_negative > 10000;
