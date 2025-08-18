package com.zxkkj.sleepAnalysis.service;

import java.util.List;

/**
 * 数据处理管道
 */
public class DataProcessingPipeline {
    private final DataReader reader;
    private final DataProcessor processor;
    private final DataAnalyzer analyzer;

    public DataProcessingPipeline(DataReader reader, DataProcessor processor, DataAnalyzer analyzer) {
        this.reader = reader;
        this.processor = processor;
        this.analyzer = analyzer;
    }

    public List<SleepDataModel> execute(String filePath) {
        List<SleepDataModel> data = reader.readData(filePath);
        processor.process(data);
        //输出异常心率比例、在床数据比例、离床数据比例
        analyzer.analyze(data);
        return data;
    }
}
