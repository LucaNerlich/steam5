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

    /**
 * Finds reactions associated with comments whose IDs are included in the supplied collection.
 *
 * @param commentIds the comment IDs to search for
 * @return the matching comment reactions
 */
List<CommentReaction> findByComment_IdIn(Collection<Long> commentIds);

    /**
 * Finds reactions for the specified comments submitted by a Steam user.
 *
 * @param commentIds the comment IDs to search
 * @param steamId    the Steam user ID
 * @return the matching comment reactions
 */
List<CommentReaction> findByComment_IdInAndSteamId(Collection<Long> commentIds, String steamId);

    /**
             * Finds a reaction for a comment submitted by a specific Steam user.
             *
             * @param commentId    the comment identifier
             * @param steamId      the Steam user identifier
             * @param reactionType the reaction type
             * @return the matching reaction, if present
             */
            Optional<CommentReaction> findByComment_IdAndSteamIdAndReactionType(
            Long commentId, String steamId, ReactionType reactionType);

    /**
     * Counts reactions for each comment and reaction type among the specified comments.
     *
     * @param commentIds the IDs of the comments to include
     * @return aggregated reaction counts grouped by comment and reaction type
     */
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
        /**
 * Provides the identifier of the associated comment.
 *
 * @return the associated comment's identifier
 */
Long getCommentId();

        /**
 * Gets the reaction type represented by this row.
 *
 * @return the reaction type
 */
ReactionType getReactionType();

        /**
 * Provides the number of reactions for the comment and reaction type represented by this row.
 *
 * @return the reaction count
 */
Long getReactionCount();
    }
}
