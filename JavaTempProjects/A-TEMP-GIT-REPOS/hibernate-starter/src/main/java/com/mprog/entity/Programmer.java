package com.mprog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import java.util.List;

//@Entity
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//@PrimaryKeyJoinColumn(name = "id")
//public class Programmer extends User{
//
//    private Language language;
//
//    @Builder
//    public Programmer(Long id, PersonalInfo personalInfo, String username, String info, Role role, Company company, Profile profile, List<UserChat> userChats, Language language) {
//        super(id, personalInfo, username, info, role, company, profile, userChats);
//        this.language = language;
//    }
//}
