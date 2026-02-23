package com.dragonracing.schedule.model;

import java.util.List;

public class CompleteRequest {

    private List<RaceResult> results;

    public CompleteRequest() {}

    public List<RaceResult> getResults() { return results; }
    public void setResults(List<RaceResult> results) { this.results = results; }

    public static class RaceResult {
        private Long dragonId;
        private String dragonName;
        private int position;
        private String finishTime;

        public RaceResult() {}

        public Long getDragonId() { return dragonId; }
        public void setDragonId(Long dragonId) { this.dragonId = dragonId; }

        public String getDragonName() { return dragonName; }
        public void setDragonName(String dragonName) { this.dragonName = dragonName; }

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public String getFinishTime() { return finishTime; }
        public void setFinishTime(String finishTime) { this.finishTime = finishTime; }
    }
}
