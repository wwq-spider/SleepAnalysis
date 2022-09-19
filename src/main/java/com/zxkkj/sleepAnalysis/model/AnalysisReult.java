package com.zxkkj.sleepAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分析结果
 */
@Data
public class AnalysisReult implements Serializable {

    /**
     * 睡眠分期类型
     */
    public enum SleepStageType {
        WakeUP(1), //觉醒期
        ShallowSleep(2), //浅睡期
        DeepSleep(3), //深睡期
        RemSleep(4); //快速眼动睡眠期

        @Getter
        private int value;

        SleepStageType(int value) {
            this.value= value;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SleepStage {

        //睡眠分期类型
        private int type;
        //开始时间
        private int startTime;
        //结束时间
        private int endTime;
    }

    /**
     * 心率信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HrStatInfo {

        private double max;

        private double min;

        private double avg;
    }

    /**
     * 呼吸率信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReStatInfo {

        private double max;

        private double min;

        private double avg;
    }

    /**
     * 血氧饱和度信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BoStatInfo {

        private double max;

        private double min;

        private double avg;
    }

    /**
     * 各睡眠段心率
     */
    private List<HrStatInfo> hrStatInfoList = new ArrayList<>();

    /**
     * 各睡眠段呼吸率
     */
    private List<ReStatInfo> reStatInfoList = new ArrayList<>();

    /**
     * 各睡眠段血氧饱和度
     */
    private List<BoStatInfo> boStatInfoList = new ArrayList<>();

    /**
     * 睡眠分期
     */
    List<AnalysisReult.SleepStage> analysisReult = new ArrayList<>();

    //监测总时长(分)
    private int monitorTotalTime;
    //在床总时长(分)
    private int onBedTotalTime;
    //离床次数
    private int leaveBedTimes;
    //离床总时间
    private int leaveBedTotalTime;
    //睡眠分段数量
    private int sleepSplitNum;
    //睡眠总时长
    private int sleepTotalTime;
    //浅睡眠总时长
    private int shallowSleepTotalTime;
    //深睡眠总时长
    private int DeepSleepTotalTime;
    //rem总时长
    private int remSleepTotalTime;
    //觉醒总时长
    private int wakeUpTotalTime;
    //打鼾总时长
    private int snoreAllTime;
    //打鼾次数
    private int snoreAllTimes;
    //弱呼吸总时长
    private int shallowBreathTime;
    //弱呼吸次数
    private int shallowBreathTimes;
    //浅睡眠比例
    private double shallowSleepRatio;
    //深睡眠比例
    private double DeepSleepRatio;
    //rem比例
    private double remSleepRatio;
    //呼吸阻塞：2:明显、1:有、0:无
    private int sleepApnea;
    //睡眠总体评价：2:好、1:较好、0:差
    private int overallEvaluationOfSleep;
}
