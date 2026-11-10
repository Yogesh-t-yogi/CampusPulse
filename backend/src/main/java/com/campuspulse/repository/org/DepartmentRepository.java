package com.campuspulse.repository.org;

import com.campuspulse.entity.org.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findAllByInstitutionIdOrderByNameAsc(Long institutionId);

    Optional<Department> findByIdAndInstitutionId(Long id, Long institutionId);
}
