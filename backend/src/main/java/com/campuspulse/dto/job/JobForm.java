package com.campuspulse.dto.job;

import java.util.List;

/** Create / edit a job by hand (title required; skills listed explicitly, no AI). */
public record JobForm(
        String title,
        String company,
        String location,
        String description,
        List<String> skills) {
}
