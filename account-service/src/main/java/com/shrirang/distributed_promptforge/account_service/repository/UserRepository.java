package com.shrirang.distributed_promptforge.account_service.repository;

import com.shrirang.distributed_promptforge.account_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String email);
    @Query("select u from User u where lower(u.username) like lower(concat('%', :q, '%')) or lower(u.name) like lower(concat('%', :q, '%'))")
    List<User> search(@Param("q") String q);
}
