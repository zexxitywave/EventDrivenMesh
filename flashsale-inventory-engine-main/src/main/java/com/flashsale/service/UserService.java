package com.flashsale.service;

import com.flashsale.entity.User;

import java.util.List;

public interface UserService {

    User getUserById(Long userId);

    User getUserByEmail(String email);

    List<User> getAllUsers();

    User createUser(User user);

    User updateUser(Long userId, User updated);

    void deleteUser(Long userId);
}
