package com.mprog.dto;

import com.mprog.entity.PersonalInfo;
import com.mprog.entity.Role;

public record UserCreateDto(PersonalInfo personalInfo,
                            String username,
                            String info,
                            Role role,
                            Integer companyId) {
}
