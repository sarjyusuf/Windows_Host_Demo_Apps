package com.dragonracing.schedule.model;

public class EnterRequest {

    private Long dragonId;
    private String dragonName;

    public EnterRequest() {}

    public Long getDragonId() { return dragonId; }
    public void setDragonId(Long dragonId) { this.dragonId = dragonId; }

    public String getDragonName() { return dragonName; }
    public void setDragonName(String dragonName) { this.dragonName = dragonName; }
}
