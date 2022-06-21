package com.zxkkj.sleepAnalysis.model;

import cn.hutool.core.text.csv.CsvRow;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 睡眠数据
 */
@Data
public class SleepData implements Serializable {

    private List<CsvRow> rowList = new ArrayList<>();

    //离床数据
    private List<String[]> leaveDatas = new ArrayList<>();

    //在床数据
    private List<String[]> bedData = new ArrayList<>();

    //心率
    private List<String> hrData = new ArrayList<>();

    //呼吸率

    //血氧饱和度

    //离床次数及总离床时长
    private Integer offBedTime;
    private Integer offBedAllTime;
}
