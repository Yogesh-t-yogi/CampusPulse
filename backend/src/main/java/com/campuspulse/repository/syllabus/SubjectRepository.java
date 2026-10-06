package com.campuspulse.repository.syllabus;

import com.campuspulse.entity.syllabus.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
}