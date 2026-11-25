package com.campuspulse.controller.admin;

import com.campuspulse.entity.syllabus.Syllabus;

/** Test access to package-private helpers of AdminUserController. */
public final class AdminUserControllerAccess {
    private AdminUserControllerAccess() {
    }

    public static boolean isShared(Syllabus syllabus) {
        return AdminUserController.isShared(syllabus);
    }
}
