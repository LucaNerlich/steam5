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

    List<Comment> findByGameDateOrderByCreatedAtDesc(LocalDate gameDate, Pageable pageable);

    /**
     * Loads a comment under a row lock so concurrent reaction toggles for the same
     * comment serialize their insert/delete decisions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.id = :id")
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);
}
