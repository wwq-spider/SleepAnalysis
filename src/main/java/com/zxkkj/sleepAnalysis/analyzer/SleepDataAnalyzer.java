package com.zxkkj.sleepAnalysis.analyzer;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.List;
import java.util.function.Predicate;

/**
 * 睡眠数据分析器实现
 */
public class SleepDataAnalyzer implements DataAnalyzer {
    @Override
    public void analyze(List<SleepDataModel> data) {
        int totalRecords = data.size();

        // 异常心率条件
        Predicate<SleepDataModel> abnormalHeartRate = item ->
                item.getHeartRate() > 150 || item.getHeartRate() < 30;

        long abnormalCount = data.stream().filter(abnormalHeartRate).count();
        //System.out.printf("异常心率比例: %.4f%n", (double) abnormalCount / totalRecords);

        // 在床状态
        Predicate<SleepDataModel> inBedStatus = item -> item.getBedStatus() == 0;
        long inBedCount = data.stream().filter(inBedStatus).count();
        //System.out.printf("在床数据比例: %.4f%n", (double) inBedCount / totalRecords);

        // 离床状态
        Predicate<SleepDataModel> outOfBedStatus = item -> item.getBedStatus() == 1;
        long outOfBedCount = data.stream().filter(outOfBedStatus).count();
        //System.out.printf("离床数据比例: %.4f%n", (double) outOfBedCount / totalRecords);
    }
}
