package com.campuspulse.dto.ai;

import java.util.ArrayList;
import java.util.List;

public class AiSubject {

    private String name;

    private String code;

    private List<AiUnit> units = new ArrayList<>();

    public AiSubject() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<AiUnit> getUnits() {
        return units;
    }

    public void setUnits(List<AiUnit> units) {
        this.units = units;
    }
}