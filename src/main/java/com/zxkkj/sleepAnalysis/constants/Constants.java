package com.zxkkj.sleepAnalysis.constants;

import lombok.Getter;

public class Constants {

    public static int Min = 1;

    public static int Max = 2;

    /**
     * 睡眠分期类型
     */
    public enum SleepStatus {
        OnBed(0), //觉醒期
        LeaveBed(1), //钱睡期
        Move(2), //深睡期
        ShallowBreath(4); //快速眼动睡眠期


        @Getter
        private int value;

        SleepStatus(int value) {
            this.value= value;
        }
    }
}
