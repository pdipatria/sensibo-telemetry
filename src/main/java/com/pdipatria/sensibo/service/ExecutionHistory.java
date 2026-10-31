package com.pdipatria.sensibo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class ExecutionHistory {
    private final JdbcTemplate jdbc;
    public ExecutionHistory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int begin(String device) {
        int seq = jdbc.queryForObject("SELECT COALESCE(MAX(import_seq_num), 0) + 1 FROM import_execution WHERE device_id = ?", Integer.class, device);
        jdbc.update("INSERT INTO import_execution(device_id, import_seq_num, started_at, status) VALUES (?, ?, CURRENT_TIMESTAMP, 'RUNNING')", device, seq);
        return seq;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String device, int seq, String reason) {
        jdbc.update("UPDATE import_execution SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, failure_reason = ? WHERE device_id = ? AND import_seq_num = ?", reason, device, seq);
    }
    public Summary summary(String device) {
        return jdbc.queryForObject("""
            SELECT COALESCE(MAX(import_seq_num), 0) AS latest,
                   COALESCE(MAX(import_seq_num) FILTER (WHERE status = 'SUCCEEDED'), 0) AS succeeded,
                   (SELECT status FROM import_execution WHERE device_id = ? ORDER BY import_seq_num DESC LIMIT 1) AS status
            FROM import_execution WHERE device_id = ?
            """, (rs, row) -> new Summary(rs.getInt("latest"), rs.getInt("succeeded"), rs.getString("status")), device, device);
    }
    public record Summary(int importSeqNum, int lastSuccessfulImportSeqNum, String lastImportStatus) {}
}
