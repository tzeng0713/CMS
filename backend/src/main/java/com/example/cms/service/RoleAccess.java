package com.example.cms.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** The logged-in staff member, reloaded on each request so role changes take effect immediately. */
@Component
@Profile("!test")
public class RoleAccess {
    public static final String SESSION_STAFF_ID = "cmsStaffId";

    private final JdbcTemplate jdbc;
    private final HttpServletRequest request;

    public RoleAccess(JdbcTemplate jdbc, HttpServletRequest request) {
        this.jdbc = jdbc;
        this.request = request;
    }

    public Actor actor() {
        Object cached = request.getAttribute("cmsActor");
        if (cached instanceof Actor actor) return actor;
        HttpSession session = request.getSession(false);
        Object staffId = session == null ? null : session.getAttribute(SESSION_STAFF_ID);
        if (!(staffId instanceof Number number)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT s.staff_id, s.branch_id, s.account_approved_at, s.email_verified_at, r.role_name
                    FROM staff s JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                    WHERE s.staff_id = ?
                    """, number.longValue());
            if (row.get("account_approved_at") == null || row.get("email_verified_at") == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account approval required");
            }
            Number branch = (Number) row.get("branch_id");
            Actor actor = new Actor(number.longValue(), branch == null ? null : branch.longValue(), (String) row.get("role_name"));
            request.setAttribute("cmsActor", actor);
            return actor;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
    }

    public void manager() {
        if (!actor().manager()) forbidden();
    }

    public void canViewRent() {
        if (actor().generalSecretary()) forbidden();
    }

    public void ownBranch(long branchId) {
        Actor actor = actor();
        if (!actor.manager() && (actor.branchId() == null || actor.branchId() != branchId)) forbidden();
    }

    public void customer(long customerId, boolean writing) {
        Actor actor = actor();
        if (actor.manager() || (!writing && actor.supervisor())) return;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contracts co JOIN offices o ON o.office_id = co.office_id
                WHERE co.customer_id = ? AND o.branch_id = ?
                """, Integer.class, customerId, actor.branchId());
        if (count == null || count == 0) forbidden();
    }

    public void contract(long contractId, boolean writing) {
        Actor actor = actor();
        if (actor.manager() || (!writing && actor.supervisor())) return;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contracts co JOIN offices o ON o.office_id = co.office_id
                WHERE co.contract_id = ? AND o.branch_id = ?
                """, Integer.class, contractId, actor.branchId());
        if (count == null || count == 0) forbidden();
    }

    public void office(long officeId) {
        Actor actor = actor();
        if (actor.manager()) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM offices WHERE office_id = ? AND branch_id = ?",
                Integer.class, officeId, actor.branchId());
        if (count == null || count == 0) forbidden();
    }

    public void sameStaff(long staffId) {
        if (actor().staffId() != staffId) forbidden();
    }

    private void forbidden() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "permission denied");
    }

    public record Actor(long staffId, Long branchId, String roleName) {
        public boolean manager() { return "主管".equals(roleName); }
        public boolean supervisor() { return "督導秘書".equals(roleName); }
        public boolean generalSecretary() { return "一般秘書".equals(roleName); }
    }
}
