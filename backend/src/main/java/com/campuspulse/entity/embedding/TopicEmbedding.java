package com.campuspulse.entity.embedding;

import com.campuspulse.entity.syllabus.Topic;
import jakarta.persistence.*;

@Entity
@Table(name = "topic_embedding")
public class TopicEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false, unique = true)
    private Topic topic;

    @Column(columnDefinition = "vector(384)", nullable = false)
    private String embedding;

    @Column(nullable = false, length = 100)
    private String model;

    public TopicEmbedding() {
    }

    public Long getId() {
        return id;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}