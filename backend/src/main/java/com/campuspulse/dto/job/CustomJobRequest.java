package com.campuspulse.dto.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CustomJobRequest {

    @NotBlank
    @Size(max = 120)
    private String title;

    @Size(max = 120)
    private String company;

    @Size(max = 120)
    private String location;

    @NotBlank
    @Size(min = 40, max = 20000)
    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
