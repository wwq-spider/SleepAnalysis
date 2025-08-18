package com.zxkkj.sleepAnalysis.service;

import java.util.List;

/**
 * 数据处理接口
 */
public interface DataProcessor {
    void process(List<SleepDataModel> data);
}
