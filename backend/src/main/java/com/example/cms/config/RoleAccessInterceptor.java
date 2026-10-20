package com.example.cms.config;

import com.example.cms.service.RoleAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Profile("!test")
public class RoleAccessInterceptor implements HandlerInterceptor {
    private final RoleAccess access;

    public RoleAccessInterceptor(RoleAccess access) {
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/") || "OPTIONS".equals(request.getMethod())) {
            return true;
        }
        access.actor();
        String method = request.getMethod();
        boolean writing = !"GET".equals(method);
        if (path.startsWith("/api/rent-payments")) {
            if (writing) access.manager(); else access.canViewRent();
        } else if (path.startsWith("/api/branches")) {
            if ("POST".equals(method)) access.manager();
            if ("PUT".equals(method)) access.ownBranch(lastId(path));
        } else if (path.startsWith("/api/customers/")) {
            if (path.matches("/api/customers/\\d+")) access.customer(lastId(path), writing);
            if (path.matches("/api/customers/\\d+/.*")) access.customer(segmentId(path, 3), false);
        } else if (path.startsWith("/api/contracts/") && "PUT".equals(method)) {
            access.contract(lastId(path), true);
        } else if (path.startsWith("/api/offices/") && "PUT".equals(method)) {
            access.office(lastId(path));
        } else if (path.startsWith("/api/staff/")) {
            if (path.matches("/api/staff/\\d+/profile-change-requests") && "POST".equals(method)) {
                access.sameStaff(segmentId(path, 3));
            } else if (writing) {
                access.manager();
            } else if (path.equals("/api/staff/profile-change-requests")) {
                if (request.getParameter("staffId") == null) access.manager();
                else if (!access.actor().manager()) access.sameStaff(Long.parseLong(request.getParameter("staffId")));
            }
        } else if (path.startsWith("/api/bonus-rules") && writing
                || path.startsWith("/api/sales-targets") && writing
                || path.startsWith("/api/performance-bonuses") && writing
                || path.startsWith("/api/backups")) {
            access.manager();
        } else if (path.matches("/api/refunds/\\d+/review") && writing) {
            access.manager();
        }
        // All other endpoints are available to approved staff; row scopes are applied in services.
        return true;
    }

    private long lastId(String path) {
        return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
    }

    private long segmentId(String path, int segment) {
        return Long.parseLong(path.split("/")[segment]);
    }
}
