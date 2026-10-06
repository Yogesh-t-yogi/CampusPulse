package com.campuspulse.repository.syllabus;

import com.campuspulse.entity.syllabus.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {
}
