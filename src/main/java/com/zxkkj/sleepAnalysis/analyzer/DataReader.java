package com.zxkkj.sleepAnalysis.analyzer;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.List;

/**
 * 数据读取接口
 */
public interface DataReader {
    List<SleepDataModel> readData(String filePath);
}
