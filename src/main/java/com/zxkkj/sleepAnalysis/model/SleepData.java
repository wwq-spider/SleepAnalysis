package com.zxkkj.sleepAnalysis.model;

public class SleepData implements SleepDataModel {
    /**
     * A列：床垫正常状态
     */
    private int normalStatus;
    /**
     * B列：血氧
     */
    private double bloodOxygen;
    /**
     * C列：在床状态 (0-在床, 1-离床, 4-重物)
     */
    private int bedStatus;
    /**
     * D列：心率
     */
    private double heartRate;
    /**
     * E列：呼吸率
     */
    private double breathingRate;

    public SleepData(int normalStatus, double bloodOxygen, int bedStatus,
                     int heartRate, double breathingRate) {
        this.normalStatus = normalStatus;
        this.bloodOxygen = bloodOxygen;
        this.bedStatus = bedStatus;
        this.heartRate = heartRate;
        this.breathingRate = breathingRate;
    }

    public SleepData(SleepDataModel other) {
        this.heartRate = other.getHeartRate();
        this.breathingRate = other.getBreathingRate();
    }


    @Override public int getNormalStatus() { return normalStatus; }
    @Override public double getBloodOxygen() { return bloodOxygen; }
    @Override public int getBedStatus() { return bedStatus; }
    @Override public double getHeartRate() { return heartRate; }
    @Override public double getBreathingRate() { return breathingRate; }
    @Override public void setBedStatus(int status) { this.bedStatus = status; }
    @Override
    public void setHeartRate(double rate) {
        this.heartRate = (int) rate;
    }
    @Override
    public void setBreathingRate(double rate) {
        this.breathingRate = rate;
    }

}
