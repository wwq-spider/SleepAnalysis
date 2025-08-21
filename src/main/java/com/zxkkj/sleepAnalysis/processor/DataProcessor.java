package com.zxkkj.sleepAnalysis.processor;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.List;

/**
 * 数据处理接口
 */
public interface DataProcessor {
    void process(List<SleepDataModel> data);
}
