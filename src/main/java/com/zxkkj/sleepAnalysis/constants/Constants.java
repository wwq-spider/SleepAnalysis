package com.zxkkj.sleepAnalysis.constants;

import lombok.Getter;

public class Constants {

    public static int Min = 1;

    public static int Max = 2;

    /**
     * 睡眠分期类型
     */
    public enum SleepStatus {
        OnBed(0), //在床
        LeaveBed(1), //离床
        Move(2), //体动
        ShallowBreath(3), //弱呼吸
        Weight(4), //重物
        Snoring(5); //打鼾

        @Getter
        private int value;

        SleepStatus(int value) {
            this.value= value;
        }
    }
}
