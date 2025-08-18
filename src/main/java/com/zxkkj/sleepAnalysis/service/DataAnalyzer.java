package com.zxkkj.sleepAnalysis.service;

import java.util.List;

/**
 * 数据分析接口
 */
public interface DataAnalyzer {
    void analyze(List<SleepDataModel> data);
}
