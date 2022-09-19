package com.zxkkj.sleepAnalysis.model;

import cn.hutool.core.text.csv.CsvRow;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 睡眠数据
 */
@Data
public class SleepData implements Serializable {

    private List<CsvRow> rowList = new ArrayList<>();

    //离床数据
    private List<LeaveOnBedInfo> leaveDatas = new ArrayList<>();

    //在床数据
    private List<OnBedData> bedData = new ArrayList<>();

    //心率
    private List<String> hrData = new ArrayList<>();

    private List<SleepInfo> sleepInfoList = new ArrayList<>();

    //打鼾数组
    private List<SnoreInfo> snoreInfoList = new ArrayList<>();

    //弱呼吸数组
    private List<ShallowBreathInfo> shallowBreathInfoList = new ArrayList<>();

    //打鼾总时长
    private int snoreAllTime;

    //打鼾次数
    private int snoreAllTimes;

    //离床次数
    private Integer offBedTime;

    //总离床时长
    private Integer offBedAllTime;

    //弱呼吸总时长
    private int shallowBreathTime;
    //弱呼吸次数
    private int shallowBreathTimes;
    //呼吸率

    //血氧饱和度

    //在床数据信息
    @Data
    public class OnBedData{

        private int onBedStartTime;

        private int hrStartTime;

        private int onBenEndTime;
    }
}
