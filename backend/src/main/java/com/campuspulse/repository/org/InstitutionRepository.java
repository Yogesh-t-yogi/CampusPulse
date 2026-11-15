package com.campuspulse.repository.org;

import com.campuspulse.entity.org.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {
    Optional<Institution> findByCodeIgnoreCase(String code);

    List<Institution> findAllByOrderByNameAsc();
}
