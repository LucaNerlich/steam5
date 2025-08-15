-- Remove entries that were added due to transient Steam API rate limiting (HTTP 429)
-- Safe to run multiple times

DELETE
FROM excluded_app
WHERE reason ILIKE '%429%';

-- Optional: verify remaining exclusions (uncomment to inspect)
-- SELECT app_id, reason, created_at FROM excluded_app ORDER BY created_at DESC LIMIT 50;


