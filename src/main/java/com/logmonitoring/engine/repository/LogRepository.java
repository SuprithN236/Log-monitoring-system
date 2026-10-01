package com.logmonitoring.engine.repository;

import com.logmonitoring.engine.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<SystemLog, Long> {

    interface SeverityCount {
        String getSeverity();

        long getTotal();
    }

    @Query("SELECT l.severity AS severity, COUNT(l) AS total FROM SystemLog l GROUP BY l.severity")
    List<SeverityCount> countBySeverity();
}
