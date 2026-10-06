package com.campuspulse.service.syllabus;

import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SyllabusService {

    private final SyllabusRepository syllabusRepository;

    public SyllabusService(SyllabusRepository syllabusRepository) {
        this.syllabusRepository = syllabusRepository;
    }

    public Syllabus createSyllabus(Syllabus syllabus) {
        return syllabusRepository.save(syllabus);
    }

    public List<Syllabus> getAllSyllabi() {
        return syllabusRepository.findAll();
    }

    public Optional<Syllabus> getSyllabusById(Long id) {
        return syllabusRepository.findById(id);
    }
}