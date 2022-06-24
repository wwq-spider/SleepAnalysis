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
    private List<Integer[]> leaveDatas = new ArrayList<>();

    //在床数据
    private List<String[]> bedData = new ArrayList<>();

    //心率
    private List<String> hrData = new ArrayList<>();

    private List<SleepInfo> sleepInfoList = new ArrayList<>();

    //呼吸率

    //血氧饱和度

    //离床次数及总离床时长
    private Integer offBedTime;
    private Integer offBedAllTime;

    //各睡眠段心率的最大值、最小值、平均值
    private List<String[]> hrMaxMin = new ArrayList<>();

    //全程心率最大值、最小值、平均值
    private List<String[]> hrMaxMinAll = new ArrayList<>();

    //各睡眠段分期
    private List<Map<String,List>> sleepPhase = new ArrayList<>();

    //在床数据信息
    @Data
    public class OnBedData{

        private int onBedStartTime;

        private int hrStartTime;

        private int onBenEndTime;
    }
}
