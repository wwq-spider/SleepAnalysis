package com.zxkkj.sleepAnalysis.processor;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.ArrayList;
import java.util.List;

public class BreathingRateSegmentProcessor {
    public static class ProcessingResult {
        private final List<SleepDataModel> processedData;
        private final List<int[]> zeroBreathingRateSegments;
        private final int zeroSegmentCount;

        public ProcessingResult(List<SleepDataModel> processedData,
                                List<int[]> zeroBreathingRateSegments) {
            this.processedData = processedData;
            this.zeroBreathingRateSegments = zeroBreathingRateSegments;
            this.zeroSegmentCount = zeroBreathingRateSegments.size();
        }

        public List<SleepDataModel> getProcessedData() {
            return processedData;
        }

        public List<int[]> getZeroBreathingRateSegments() {
            return zeroBreathingRateSegments;
        }

        public int getZeroSegmentCount() {
            return zeroSegmentCount;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data, List<int[]> zeroHeartRateSegments) {
        // 复制零心率段作为零呼吸率段
        List<int[]> zeroBreathingRateSegments = new ArrayList<>();
        for (int[] segment : zeroHeartRateSegments) {
            zeroBreathingRateSegments.add(new int[]{segment[0], segment[1]});
        }
        // 统计零呼吸率段数量
        //int zeroSegmentCount = zeroBreathingRateSegments.size();
        return new ProcessingResult(data, zeroBreathingRateSegments);
    }
}