package com.campuspulse.repository.syllabus;

import com.campuspulse.entity.syllabus.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Query("""
        SELECT t
        FROM Topic t
        JOIN t.unit u
        JOIN u.subject s
        WHERE s.syllabus.id = :syllabusId
        """)
    List<Topic> findBySyllabusId(@Param("syllabusId") Long syllabusId);
}