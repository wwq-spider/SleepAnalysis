package com.zxkkj.sleepAnalysis.service;

import com.zxkkj.sleepAnalysis.model.ExecuteResult;

/**
 * 睡眠分析服务
 */
public interface IAnalysisService {
    ExecuteResult startAnalysis(String fileDir, String outTxtPath);
}
