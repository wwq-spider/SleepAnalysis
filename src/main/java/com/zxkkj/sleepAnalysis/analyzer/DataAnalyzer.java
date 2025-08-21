package com.zxkkj.sleepAnalysis.analyzer;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.List;

/**
 * 数据分析接口
 */
public interface DataAnalyzer {
    void analyze(List<SleepDataModel> data);
}
