package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.steam5.domain.CommentReaction;
import org.steam5.domain.ReactionType;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    /**
     * Returns, for each (comment, reactionType) group among the given comments, only the first
     * {@code limit} reaction rows ordered by creation time (id as a tiebreaker for deterministic
     * grouping when timestamps collide). Capping in the database avoids loading every reaction
     * on popular comments just to keep a handful of reactor names.
     */
    @Query(value = """
            SELECT ranked.id, ranked.comment_id, ranked.steam_id, ranked.reaction_type, ranked.created_at
            FROM (
                SELECT r.id, r.comment_id, r.steam_id, r.reaction_type, r.created_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.comment_id, r.reaction_type
                           ORDER BY r.created_at ASC, r.id ASC
                       ) AS rn
                FROM comment_reactions r
                WHERE r.comment_id IN (:commentIds)
            ) ranked
            WHERE ranked.rn <= :limit
            ORDER BY ranked.comment_id, ranked.reaction_type, ranked.created_at ASC, ranked.id ASC
            """, nativeQuery = true)
    List<CommentReaction> findTopReactorsByCommentIds(@Param("commentIds") Collection<Long> commentIds,
                                                       @Param("limit") int limit);

    List<CommentReaction> findByComment_IdInAndSteamId(Collection<Long> commentIds, String steamId);

    Optional<CommentReaction> findByComment_IdAndSteamIdAndReactionType(
            Long commentId, String steamId, ReactionType reactionType);

    @Query("""
            select r.comment.id as commentId,
                   r.reactionType as reactionType,
                   count(r) as reactionCount
            from CommentReaction r
            where r.comment.id in :commentIds
            group by r.comment.id, r.reactionType
            """)
    List<ReactionCountRow> countByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    /**
     * Inserts a reaction, ignoring unique-constraint conflicts so the outer
     * transaction remains usable under concurrent toggles.
     *
     * @return 1 when a row was inserted, 0 when the conflict was ignored
     */
    @Modifying
    @Query(value = """
            INSERT INTO comment_reactions (comment_id, steam_id, reaction_type, created_at)
            VALUES (:commentId, :steamId, :reactionType, :createdAt)
            ON CONFLICT (comment_id, steam_id, reaction_type) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoreConflict(@Param("commentId") Long commentId,
                             @Param("steamId") String steamId,
                             @Param("reactionType") String reactionType,
                             @Param("createdAt") OffsetDateTime createdAt);

    interface ReactionCountRow {
        Long getCommentId();

        ReactionType getReactionType();

        Long getReactionCount();
    }
}
