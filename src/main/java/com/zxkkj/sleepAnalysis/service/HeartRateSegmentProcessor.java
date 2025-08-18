package com.zxkkj.sleepAnalysis.service;

import java.util.ArrayList;
import java.util.List;

public class HeartRateSegmentProcessor {

    // 处理结果封装类
    public static class ProcessingResult {
        private final List<SleepDataModel> processedData;
        private final List<int[]> zeroHeartRateSegments;
        private final int zeroSegmentCount;

        public ProcessingResult(List<SleepDataModel> processedData,
                                List<int[]> zeroHeartRateSegments) {
            this.processedData = processedData;
            this.zeroHeartRateSegments = zeroHeartRateSegments;
            this.zeroSegmentCount = zeroHeartRateSegments.size();
        }

        public List<SleepDataModel> getProcessedData() {
            return processedData;
        }

        public List<int[]> getZeroHeartRateSegments() {
            return zeroHeartRateSegments;
        }

        public int getZeroSegmentCount() {
            return zeroSegmentCount;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data) {
        if (data == null || data.isEmpty()) {
            return new ProcessingResult(new ArrayList<>(), new ArrayList<>());
        }

        int m = data.size();

        // 1. 找到第一个非零心率点，并删除之前的数据
        int startIndex = 0;
        for (int i = 0; i < m; i++) {
            if (data.get(i).getHeartRate() != 0) {
                startIndex = i;
                break;
            }
        }

        // 截取数据
        List<SleepDataModel> trimmedData = new ArrayList<>(data.subList(startIndex, data.size()));
        // 2. 识别零心率段
        List<int[]> zeroSegments = new ArrayList<>();
        boolean inZeroSegment = false;
        int segmentStart = 0;

        for (int i = 0; i < data.size(); i++) {
            double heartRate = data.get(i).getHeartRate();

            if (heartRate == 0) {
                if (!inZeroSegment) {
                    segmentStart = i;
                    inZeroSegment = true;
                }

                // 如果是最后一个点且处于零心率段中
                if (inZeroSegment && i == m - 1) {
                    zeroSegments.add(new int[]{segmentStart, i});
                }
            } else {
                if (inZeroSegment) {
                    // 结束当前零心率段
                    zeroSegments.add(new int[]{segmentStart, i - 1});
                    inZeroSegment = false;
                }
            }
        }

        // 3. 清理无效段（长度小于2的段）
        List<int[]> validSegments = new ArrayList<>();
        for (int[] segment : zeroSegments) {
            if (segment[1] - segment[0] >= 1) {
                validSegments.add(segment);
            }
        }

        return new ProcessingResult(trimmedData, validSegments);
    }
}