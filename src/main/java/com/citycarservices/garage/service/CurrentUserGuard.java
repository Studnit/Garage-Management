package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageUserDetails;

final class CurrentUserGuard {

    private CurrentUserGuard() {
    }

    /** Prevents an administrator from disabling their own account. */
    static void notSelfLockout(Long targetId, boolean enabledAfter) {
        GarageUserDetails me = CurrentUser.get();
        if (me != null && me.getId().equals(targetId) && !enabledAfter) {
            throw new BusinessException("You cannot disable your own account.");
        }
    }
}
