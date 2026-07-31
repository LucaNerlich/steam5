package org.steam5.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.Comment;

import java.time.LocalDate;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByGameDateOrderByCreatedAtDesc(LocalDate gameDate, Pageable pageable);
}
