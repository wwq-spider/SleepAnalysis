package com.zxkkj.sleepAnalysis.model;

import lombok.Data;

@Data
public class SleepInfo {
    //状态
    private Integer status;
    //心率
    private double hr;
    //呼吸
    private double re;
}
