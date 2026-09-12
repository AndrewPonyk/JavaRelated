package com.shopflow.auth.repository;

import com.shopflow.auth.domain.UserAccount;
import org.springframework.data.repository.CrudRepository;

/**
 * Redis-backed user store. Keyed by username (the entity {@code @Id}), so
 * {@code findById} is a username lookup.
 */
public interface UserRepository extends CrudRepository<UserAccount, String> {
}
