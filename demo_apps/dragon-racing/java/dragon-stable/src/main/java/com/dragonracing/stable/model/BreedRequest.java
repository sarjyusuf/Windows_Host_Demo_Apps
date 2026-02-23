package com.dragonracing.stable.model;

public class BreedRequest {

    private Long parentOneId;
    private Long parentTwoId;
    private String offspringName;
    private String ownerName;

    public BreedRequest() {
    }

    public Long getParentOneId() { return parentOneId; }
    public void setParentOneId(Long parentOneId) { this.parentOneId = parentOneId; }

    public Long getParentTwoId() { return parentTwoId; }
    public void setParentTwoId(Long parentTwoId) { this.parentTwoId = parentTwoId; }

    public String getOffspringName() { return offspringName; }
    public void setOffspringName(String offspringName) { this.offspringName = offspringName; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
}
