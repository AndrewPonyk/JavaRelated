package com.ap;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class UserService {
    public void deleteAllUsers() {
    }

    public void deleteUserById(long id) {
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<User> findAllUsers() {
        return new LinkedList<User>();
    }

    public User findById(long id) {
        return new User();
    }

    public boolean isUserExist(User user) {
        return false;
    }

    public void saveUser(User user) {
    }

    public void updateUser(User currentUser) {
    }
}
