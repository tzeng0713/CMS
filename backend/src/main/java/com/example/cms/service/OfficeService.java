package com.example.cms.service;

import com.example.cms.dto.OfficeContactRequest;
import com.example.cms.dto.OfficeRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class OfficeService extends CmsJdbcSupport {

    public OfficeService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> offices(Long branchId) {
        List<Map<String, Object>> rows;
        if (branchId != null) {
            rows = jdbc.queryForList("""
                    SELECT o.*, b.branch_name
                    FROM offices o
                    JOIN branches b ON b.branch_id = o.branch_id
                    WHERE o.branch_id = ?
                    ORDER BY o.office_id
                    """, branchId);
        } else {
            rows = jdbc.queryForList("""
                    SELECT o.*, b.branch_name
                    FROM offices o
                    JOIN branches b ON b.branch_id = o.branch_id
                    ORDER BY o.office_id
                    """);
        }
        rows.forEach(row -> {
            Long officeId = ((Number) row.get("office_id")).longValue();
            row.put("contacts", jdbc.queryForList("""
                    SELECT office_contact_id, person_name, phone
                    FROM office_contacts
                    WHERE office_id = ?
                    ORDER BY office_contact_id
                    """, officeId));
        });
        return rows;
    }

    @Transactional
    public Map<String, Object> createOffice(OfficeRequest request) {
        requiredId(request.branchId(), "branchId");
        Long id = nextId("offices", "office_id");
        jdbc.update("""
                INSERT INTO offices (office_id, office_no, branch_id, notes)
                VALUES (?, ?, ?, ?)
                """,
                id,
                blankToNull(request.officeNo()),
                request.branchId(),
                blankToNull(request.notes()));
        saveOfficeContacts(id, request.contacts());
        return officeDetail(id);
    }

    @Transactional
    public Map<String, Object> updateOffice(long id, OfficeRequest request) {
        requiredId(request.branchId(), "branchId");
        jdbc.update("""
                UPDATE offices
                SET office_no = ?,
                    branch_id = ?,
                    notes = ?
                WHERE office_id = ?
                """,
                blankToNull(request.officeNo()),
                request.branchId(),
                blankToNull(request.notes()),
                id);
        saveOfficeContacts(id, request.contacts());
        return officeDetail(id);
    }

    private Map<String, Object> officeDetail(long id) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT o.*, b.branch_name
                FROM offices o
                JOIN branches b ON b.branch_id = o.branch_id
                WHERE o.office_id = ?
                """, id);
        row.put("contacts", jdbc.queryForList("""
                SELECT office_contact_id, person_name, phone
                FROM office_contacts
                WHERE office_id = ?
                ORDER BY office_contact_id
                """, id));
        return row;
    }

    private void saveOfficeContacts(long officeId, List<OfficeContactRequest> contacts) {
        jdbc.update("DELETE FROM office_contacts WHERE office_id = ?", officeId);
        if (contacts == null || contacts.isEmpty()) {
            return;
        }
        Long baseId = nextId("office_contacts", "office_contact_id");
        for (int i = 0; i < contacts.size(); i++) {
            OfficeContactRequest c = contacts.get(i);
            jdbc.update("""
                    INSERT INTO office_contacts (office_contact_id, office_id, person_name, phone)
                    VALUES (?, ?, ?, ?)
                    """,
                    baseId + i,
                    officeId,
                    blankToNull(c.personName()),
                    blankToNull(c.phone()));
        }
    }
}
