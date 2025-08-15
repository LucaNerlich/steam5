-- Clear previously generated blurhash and blurdata placeholders for screenshots
-- Safe to run multiple times

UPDATE screenshots
SET blurhash_thumb = NULL,
    blurhash_full  = NULL,
    blurdata_thumb = NULL,
    blurdata_full  = NULL
WHERE blurhash_thumb IS NOT NULL
   OR blurhash_full IS NOT NULL
   OR blurdata_thumb IS NOT NULL
   OR blurdata_full IS NOT NULL;


