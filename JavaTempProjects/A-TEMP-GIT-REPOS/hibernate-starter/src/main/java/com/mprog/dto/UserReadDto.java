package com.mprog.dto;

import com.mprog.entity.PersonalInfo;
import com.mprog.entity.Role;

public record UserReadDto(Long id,
                          PersonalInfo personalInfo,
                          String username,
                          String info,
                          Role role,
                          CompanyReadDto company) {
}
