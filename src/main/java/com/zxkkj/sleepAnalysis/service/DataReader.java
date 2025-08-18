package com.zxkkj.sleepAnalysis.service;

import java.util.List;

/**
 * 数据读取接口
 */
public interface DataReader {
    List<SleepDataModel> readData(String filePath);
}
