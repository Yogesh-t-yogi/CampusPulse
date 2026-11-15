package com.campuspulse.repository.org;

import com.campuspulse.entity.org.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    List<Program> findAllByDepartmentIdOrderByNameAsc(Long departmentId);

    List<Program> findAllByDepartmentIdInOrderByNameAsc(List<Long> departmentIds);

    Optional<Program> findFirstById(Long id);
}
