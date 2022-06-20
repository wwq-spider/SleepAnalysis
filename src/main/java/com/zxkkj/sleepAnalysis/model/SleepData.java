package com.zxkkj.sleepAnalysis.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 睡眠数据
 */
@Data
public class SleepData implements Serializable {

    //离床数据
    private List<String[]> leaveDatas = new ArrayList<>();

    //在床数据
    private List<String[]> bedData = new ArrayList<>();

    //心率
    private List<String> hrData = new ArrayList<>();

    //呼吸率

    //血氧饱和度
}
