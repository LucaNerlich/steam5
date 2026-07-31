package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.steam5.domain.CommentReaction;
import org.steam5.domain.ReactionType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    List<CommentReaction> findByComment_IdIn(Collection<Long> commentIds);

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

    interface ReactionCountRow {
        Long getCommentId();

        ReactionType getReactionType();

        Long getReactionCount();
    }
}
