package org.steam5.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.steam5.domain.Comment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
 * Retrieves comments for a game date, ordered from newest to oldest.
 *
 * @param gameDate the game date used to filter comments
 * @param pageable pagination and result-size settings
 * @return comments matching the game date in descending creation order
 */
List<Comment> findByGameDateOrderByCreatedAtDesc(LocalDate gameDate, Pageable pageable);

    /**
     * Retrieves a comment while acquiring a pessimistic write lock.
     *
     * @param id the comment identifier
     * @return the locked comment, if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.id = :id")
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);
}
