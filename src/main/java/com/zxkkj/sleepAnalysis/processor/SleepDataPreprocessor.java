package com.zxkkj.sleepAnalysis.processor;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.List;

/**
 * 数据预处理器实现
 */
public class SleepDataPreprocessor implements DataProcessor {
    @Override
    public void process(List<SleepDataModel> data) {
        data.forEach(item -> {
            // 重物状态(4)转为离床状态(1)
            if (item.getBedStatus() == 4) {
                item.setBedStatus(1);
            }
            // 离床状态时心率归零
            if (item.getBedStatus() == 1) {
                item.setHeartRate(0);
            }
        });
    }
}
