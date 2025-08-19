package org.steam5.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewsBucketRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Bucket> equalWidth() {
        final String sql = "WITH r AS (\n" +
                "    SELECT (total_positive + total_negative) AS total\n" +
                "    FROM steam_app_reviews\n" +
                "    WHERE (total_positive + total_negative) > 0\n" +
                "),\n" +
                "     ext AS (\n" +
                "         SELECT MIN(total)::bigint AS mn, MAX(total)::bigint AS mx\n" +
                "         FROM r\n" +
                "     ),\n" +
                "     bins AS (\n" +
                "         SELECT gs AS idx,\n" +
                "                ext.mn,\n" +
                "                ext.mx,\n" +
                "                CEIL(((ext.mx - ext.mn + 1)::numeric) / 5.0)::bigint AS width\n" +
                "         FROM ext, generate_series(1, 5) AS gs\n" +
                "     ),\n" +
                "     bounds AS (\n" +
                "         SELECT\n" +
                "             idx,\n" +
                "             (mn + (idx - 1) * width) AS lower,\n" +
                "             CASE WHEN idx < 5 THEN (mn + idx * width - 1) ELSE mx END AS upper\n" +
                "         FROM bins\n" +
                "     ),\n" +
                "     counts AS (\n" +
                "         SELECT\n" +
                "             b.idx,\n" +
                "             b.lower,\n" +
                "             b.upper,\n" +
                "             COUNT(r.total) AS count_in_bucket\n" +
                "         FROM bounds b\n" +
                "                  LEFT JOIN r ON r.total BETWEEN b.lower AND b.upper\n" +
                "         GROUP BY b.idx, b.lower, b.upper\n" +
                "     )\n" +
                " SELECT\n" +
                "     idx AS bucket,\n" +
                "     lower,\n" +
                "     upper,\n" +
                "     (lower::bigint || '-' || upper::bigint) AS label,\n" +
                "     count_in_bucket\n" +
                " FROM counts\n" +
                " ORDER BY idx;";
        return mapRows(sql);
    }

    public List<Bucket> equalCount() {
        final String sql = "WITH r AS (\n" +
                "    SELECT app_id, (total_positive + total_negative) AS total\n" +
                "    FROM steam_app_reviews\n" +
                "    WHERE (total_positive + total_negative) > 0\n" +
                "),\n" +
                "     q AS (\n" +
                "         SELECT app_id, total, ntile(5) OVER (ORDER BY total) AS bucket\n" +
                "         FROM r\n" +
                "     ),\n" +
                "     b AS (\n" +
                "         SELECT bucket AS idx,\n" +
                "                MIN(total)::bigint AS lower,\n" +
                "                MAX(total)::bigint AS upper,\n" +
                "                COUNT(*)   AS count_in_bucket\n" +
                "         FROM q\n" +
                "         GROUP BY bucket\n" +
                "     )\n" +
                " SELECT idx AS bucket,\n" +
                "        lower,\n" +
                "        upper,\n" +
                "        (lower::bigint || '-' || upper::bigint) AS label,\n" +
                "        count_in_bucket\n" +
                " FROM b\n" +
                " ORDER BY idx;";
        return mapRows(sql);
    }

    public List<Bucket> linearWinsorized() {
        final String sql = "WITH r AS (\n" +
                "    SELECT (total_positive + total_negative) AS total\n" +
                "    FROM steam_app_reviews\n" +
                "    WHERE (total_positive + total_negative) > 0\n" +
                "),\n" +
                "      ext AS (\n" +
                "          SELECT\n" +
                "              MIN(total)::bigint                        AS mn,\n" +
                "              CAST(percentile_disc(0.995) WITHIN GROUP (ORDER BY total) AS bigint) AS cap\n" +
                "          FROM r\n" +
                "      ),\n" +
                "      bins AS (\n" +
                "          SELECT gs AS idx, mn, cap,\n" +
                "                 CEIL(((cap - mn + 1)::numeric) / 5.0)::bigint AS width\n" +
                "          FROM ext, generate_series(1, 5) AS gs\n" +
                "      ),\n" +
                "      bounds AS (\n" +
                "          SELECT\n" +
                "              idx,\n" +
                "              (mn + (idx - 1) * width) AS lower,\n" +
                "              CASE WHEN idx < 5 THEN (mn + idx * width - 1) ELSE cap END AS upper\n" +
                "          FROM bins\n" +
                "      ),\n" +
                "      counts AS (\n" +
                "          SELECT\n" +
                "              b.idx,\n" +
                "              b.lower,\n" +
                "              b.upper,\n" +
                "              COUNT(CASE\n" +
                "                        WHEN b.idx < 5 AND r.total BETWEEN b.lower AND b.upper THEN 1\n" +
                "                        WHEN b.idx = 5 AND r.total >= b.lower THEN 1\n" +
                "                  END) AS count_in_bucket\n" +
                "          FROM bounds b\n" +
                "                   CROSS JOIN r\n" +
                "          GROUP BY b.idx, b.lower, b.upper\n" +
                "      )\n" +
                " SELECT\n" +
                "     idx AS bucket,\n" +
                "     lower,\n" +
                "     CASE WHEN idx < 5 THEN upper ELSE NULL END AS upper,\n" +
                "     CASE\n" +
                "         WHEN idx < 5 THEN (lower::text || '-' || upper::text)\n" +
                "         ELSE ('≥ ' || lower::text)\n" +
                "         END AS label,\n" +
                "     count_in_bucket\n" +
                " FROM counts\n" +
                " ORDER BY idx;";
        return mapRows(sql);
    }

    public List<Bucket> logSpace() {
        final String sql = "WITH RECURSIVE\n" +
                "    r AS (\n" +
                "        SELECT (total_positive + total_negative) AS total\n" +
                "        FROM steam_app_reviews\n" +
                "        WHERE (total_positive + total_negative) > 0\n" +
                "    ),\n" +
                "    ext AS (\n" +
                "        SELECT\n" +
                "            MIN(total)::bigint AS mn,\n" +
                "            CAST(percentile_disc(0.995) WITHIN GROUP (ORDER BY total) AS bigint) AS cap\n" +
                "        FROM r\n" +
                "    ),\n" +
                "    log_params AS (\n" +
                "        SELECT LN(mn + 1.0) AS min_log, LN(cap + 1.0) AS max_log FROM ext\n" +
                "    ),\n" +
                "    edges_raw AS (\n" +
                "        SELECT i,\n" +
                "               FLOOR(EXP(min_log + i * ((max_log - min_log) / 5.0)) - 1)::bigint AS raw_edge\n" +
                "        FROM log_params, generate_series(0,5) AS i\n" +
                "    ),\n" +
                "    edges AS (\n" +
                "        SELECT i, raw_edge AS edge\n" +
                "        FROM edges_raw\n" +
                "        WHERE i = 0\n" +
                "        UNION ALL\n" +
                "        SELECT er.i, GREATEST(er.raw_edge, e.edge + 1) AS edge\n" +
                "        FROM edges e\n" +
                "                 JOIN edges_raw er ON er.i = e.i + 1\n" +
                "    ),\n" +
                "    bounds AS (\n" +
                "        SELECT\n" +
                "            e0.i AS idx,                              -- 0..4\n" +
                "            e0.edge AS lower,\n" +
                "            /* force last bucket's upper to NULL (open end) */\n" +
                "            CASE WHEN e0.i < 4 THEN (e1.edge - 1) ELSE NULL END AS upper\n" +
                "        FROM edges e0\n" +
                "                 LEFT JOIN edges e1 ON e1.i = e0.i + 1\n" +
                "        WHERE e0.i BETWEEN 0 AND 4\n" +
                "    ),\n" +
                "    counts AS (\n" +
                "        SELECT\n" +
                "            b.idx + 1 AS bucket,                      -- 1..5\n" +
                "            b.lower,\n" +
                "            b.upper,\n" +
                "            COUNT(\n" +
                "                    CASE\n" +
                "                        WHEN b.upper IS NOT NULL AND r.total BETWEEN b.lower AND b.upper THEN 1\n" +
                "                        WHEN b.upper IS NULL      AND r.total >= b.lower                  THEN 1\n" +
                "                        END\n" +
                "            ) AS count_in_bucket\n" +
                "        FROM bounds b\n" +
                "                 CROSS JOIN r\n" +
                "        GROUP BY b.idx, b.lower, b.upper\n" +
                "    )\n" +
                " SELECT\n" +
                "     bucket,\n" +
                "     lower,\n" +
                "     upper,  -- last bucket upper is NULL\n" +
                "     CASE WHEN upper IS NOT NULL\n" +
                "              THEN lower::text || '-' || upper::text\n" +
                "          ELSE '≥ ' || lower::text\n" +
                "         END AS label,\n" +
                "     count_in_bucket\n" +
                " FROM counts\n" +
                " ORDER BY bucket;";
        return mapRows(sql);
    }

    private List<Bucket> mapRows(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Bucket(
                rs.getInt("bucket"),
                rs.getObject("lower") == null ? null : rs.getLong("lower"),
                rs.getObject("upper") == null ? null : rs.getLong("upper"),
                rs.getString("label"),
                rs.getLong("count_in_bucket")
        ));
    }

    public record Bucket(int bucket, Long lower, Long upper, String label, long countInBucket) {
    }
}


