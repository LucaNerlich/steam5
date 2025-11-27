-- Delete the daily_pick_lock entry for today
-- This allows the system to regenerate picks for today if needed

DELETE FROM daily_pick_lock
WHERE pick_date = CURRENT_DATE;

-- Verify deletion (optional - remove this if you don't want output)
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN 'Lock for today successfully deleted'
        ELSE 'Warning: Lock still exists for today'
    END AS result
FROM daily_pick_lock
WHERE pick_date = CURRENT_DATE;

