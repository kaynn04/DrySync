package com.example.drysync;

public class WoodBatch {
    private String batchId;
    private long arrivalDateMillis;
    private int totalQuantity;
    private int inRackCount;
    private int finishedCount;

    // Firebase needs a no-arg constructor
    public WoodBatch() {}

    public WoodBatch(String batchId, int totalQuantity, long arrivalDateMillis) {
        this.batchId = batchId;
        this.totalQuantity = Math.max(0, totalQuantity);
        this.arrivalDateMillis = arrivalDateMillis;
        this.inRackCount = 0;
        this.finishedCount = 0;
    }

    // getters
    public String getBatchId() { return batchId; }
    public long getArrivalDateMillis() { return arrivalDateMillis; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getInRackCount() { return inRackCount; }
    public int getFinishedCount() { return finishedCount; }
    public int getRemaining() { return Math.max(0, totalQuantity - inRackCount - finishedCount); }

    // setters (Firebase uses these)
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public void setArrivalDateMillis(long arrivalDateMillis) { this.arrivalDateMillis = arrivalDateMillis; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = Math.max(0, totalQuantity); clampCounts(); }
    public void setInRackCount(int inRackCount) { this.inRackCount = Math.max(0, inRackCount); clampCounts(); }
    public void setFinishedCount(int finishedCount) { this.finishedCount = Math.max(0, finishedCount); clampCounts(); }

    private void clampCounts() {
        int sum = inRackCount + finishedCount;
        if (sum > totalQuantity) {
            int overflow = sum - totalQuantity;
            int reduceFinished = Math.min(overflow, finishedCount);
            finishedCount -= reduceFinished;
            overflow -= reduceFinished;
            inRackCount = Math.max(0, inRackCount - overflow);
        }
    }
}
