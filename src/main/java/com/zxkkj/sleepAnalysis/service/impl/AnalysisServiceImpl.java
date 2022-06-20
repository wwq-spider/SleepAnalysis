package com.zxkkj.sleepAnalysis.service.impl;

import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.zxkkj.sleepAnalysis.model.AnalysisReult;
import com.zxkkj.sleepAnalysis.model.SleepData;
import com.zxkkj.sleepAnalysis.service.IAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class AnalysisServiceImpl implements IAnalysisService {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public SleepData loadDataByLoaclFile(File localFile) {

        //解析csv文件
        CsvReadConfig csvConfig = new CsvReadConfig();
        List<CsvRow> rowList = CsvUtil.getReader(csvConfig).read(localFile).getRows();

        //离床数据

        //读取离床数据

        return null;
    }

    @Override
    public AnalysisReult executeAnalysis(SleepData sleepData) {
        return null;
    }

    @Override
    public void writeAnalysisResult(AnalysisReult analysisReult) {

    }
}
