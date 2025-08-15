package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.User;

public interface UserRepository extends JpaRepository<User, String> {
}


