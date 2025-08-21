package com.zxkkj.sleepAnalysis.model;

public interface SleepDataModel {
    int getNormalStatus();
    double getBloodOxygen();
    int getBedStatus();
    double getHeartRate();
    double getBreathingRate();
    void setBedStatus(int status);
    void setHeartRate(double rate);
    void setBreathingRate(double rate);
}
