package com.approval.workflow.models;

/**
 * Roles for workflow routing and access control.
 */
public enum Role {
    CREATOR,
    TEAM_LEAD,
    DEPARTMENT_HEAD,
    LEGAL_COUNSEL,
    FINANCE_CONTROLLER,
    EXECUTIVE,
    ADMIN;

    public static Role fromString(String roleStr) {
        if (roleStr == null) return CREATOR;
        try {
            return Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CREATOR;
        }
    }
}
