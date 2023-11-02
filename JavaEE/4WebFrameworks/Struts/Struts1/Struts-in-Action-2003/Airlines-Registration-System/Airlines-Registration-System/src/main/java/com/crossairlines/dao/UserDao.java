package com.crossairlines.dao;

import java.util.List;

import com.crossairlines.pojo.User;

public abstract class UserDao {
    public abstract User verifyUser(User userauth) ;
    public abstract User verifyAdmin(User userauth) ;
    public abstract boolean addUser(User userauth);

    public abstract User getUserDetails(int id);
    public abstract void updateUserDetails(User user);
    public abstract List<User> getAllUserDetails();
    public abstract void deleteUser(User user);
}