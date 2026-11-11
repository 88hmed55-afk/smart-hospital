package com.smarthospital.security.auth;

import com.smarthospital.security.domain.CurrentUser;
import jakarta.enterprise.context.RequestScoped;

/** Request-scoped identity populated by the bearer-token filter. */
@RequestScoped
public class AuthenticatedUserHolder {
    private CurrentUser user;

    public CurrentUser get() {
        return user;
    }

    public void set(CurrentUser user) {
        this.user = user;
    }

    public void clear() {
        this.user = null;
    }
}
