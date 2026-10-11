
package com.campuspulse.repository.syllabus;

import com.campuspulse.entity.syllabus.Syllabus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyllabusRepository extends JpaRepository<Syllabus, Long> {

    // Retrieve syllabi belonging to a specific user
    List<Syllabus> findAllByOwner_Id(Long userId);

    // Retrieve a specific syllabus only if it belongs to that user
    Optional<Syllabus> findByIdAndOwner_Id(Long id, Long userId);
}
