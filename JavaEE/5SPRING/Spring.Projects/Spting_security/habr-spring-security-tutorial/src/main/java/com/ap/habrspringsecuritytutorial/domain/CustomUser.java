package com.ap.habrspringsecuritytutorial.domain;

public class CustomUserEntity extends UserEntity {
    private static final long serialVersionUID = 1L;
    public CustomUserEntity(UserEntity user) {
        super(user.getUsername(), user.getPassword(), user.getGrantedAuthoritiesList());
    }
}
