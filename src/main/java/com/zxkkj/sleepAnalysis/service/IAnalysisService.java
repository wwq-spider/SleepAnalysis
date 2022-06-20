package com.zxkkj.sleepAnalysis.service;

import com.zxkkj.sleepAnalysis.model.AnalysisReult;
import com.zxkkj.sleepAnalysis.model.SleepData;

import java.io.File;
import java.util.List;

/**
 * 睡眠分析服务
 */
public interface IAnalysisService {

    /**
     * 从本地文件中读取睡眠数据
     * @param localFile
     * @return
     */
    SleepData loadDataByLoaclFile(File localFile);

    /**
     * 分析数据
     * @param sleepData
     * @return
     */
    AnalysisReult executeAnalysis(SleepData sleepData);

    /**
     * 写入结果
     * @param analysisReult
     */
    void writeAnalysisResult(AnalysisReult analysisReult);
}
