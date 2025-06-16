package com.zxkkj.sleepAnalysis.model;

import lombok.Data;

@Data
public class SleepInfo {
    //胸部监测带状态
    private Integer monitorStatus;
    //心率
    private double hr;
    //呼吸
    private double re;
    //血氧
    private double bo;
}
