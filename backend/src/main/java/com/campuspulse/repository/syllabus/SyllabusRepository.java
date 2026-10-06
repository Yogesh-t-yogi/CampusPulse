package com.campuspulse.repository.syllabus;

import com.campuspulse.entity.syllabus.Syllabus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyllabusRepository extends JpaRepository<Syllabus, Long> {
}