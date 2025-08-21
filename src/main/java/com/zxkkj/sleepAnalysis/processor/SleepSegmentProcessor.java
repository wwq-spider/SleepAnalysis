package com.zxkkj.sleepAnalysis.processor;

import com.zxkkj.sleepAnalysis.model.SleepData;
import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.ArrayList;
import java.util.List;

public class SleepSegmentProcessor {

    // 处理结果封装类
    public static class ProcessingResult {
        private final List<int[]> sleepSegments;
        private final List<SleepDataModel> interpolatedData;

        public ProcessingResult(List<int[]> sleepSegments,
                                List<SleepDataModel> interpolatedData) {
            this.sleepSegments = sleepSegments;
            this.interpolatedData = interpolatedData;
        }

        public List<int[]> getSleepSegments() {
            return sleepSegments;
        }

        public List<SleepDataModel> getInterpolatedData() {
            return interpolatedData;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data, List<int[]> offBedSegments) {
        int m = data.size();
        List<int[]> sleepSegments = new ArrayList<>();

        // 1. 搜索睡眠段
        if (offBedSegments.isEmpty()) {
            // 无离床段：整个数据为一个睡眠段
            sleepSegments.add(new int[]{0, m - 1});
        } else {
            int n = 0;
            // 第一个睡眠段：从开始到第一个离床段前
            sleepSegments.add(new int[]{0, offBedSegments.get(0)[0] - 1});
            n++;

            // 处理中间离床段
            for (int i = 0; i < offBedSegments.size() - 1; i++) {
                int[] currentOffBed = offBedSegments.get(i);
                int[] nextOffBed = offBedSegments.get(i + 1);

                // 睡眠段：离床结束后的下一个点到下一个离床开始前
                sleepSegments.add(new int[]{currentOffBed[1] + 1, nextOffBed[0] - 1});
                n++;
            }

            // 处理最后一个离床段
            int[] lastOffBed = offBedSegments.get(offBedSegments.size() - 1);
            sleepSegments.add(new int[]{lastOffBed[1] + 1, m - 1});
            n++;
        }

        // 2. 剔除无效睡眠段
        List<int[]> validSleepSegments = new ArrayList<>();
        for (int[] segment : sleepSegments) {
            if (segment[0] <= segment[1] && segment[1] < m) {
                validSleepSegments.add(segment);
            }
        }

        // 3. 创建数据副本用于插值
        List<SleepDataModel> interpolatedData = copyData(data);

        // 4. 对每个睡眠段进行插值处理
        for (int[] segment : validSleepSegments) {
            int start = segment[0];
            int end = segment[1];

            for (int i = start; i <= end; i++) {
                // 处理心率为0的情况
                if (interpolatedData.get(i).getHeartRate() == 0) {
                    interpolatedData.get(i).setHeartRate(
                            interpolateNearest(interpolatedData, i, 4, 3)
                    );
                }

                // 处理呼吸率为0的情况
                if (interpolatedData.get(i).getBreathingRate() == 0) {
                    interpolatedData.get(i).setBreathingRate(
                            interpolateNearest(interpolatedData, i, 5, 3)
                    );
                }
            }
        }

        return new ProcessingResult(validSleepSegments, interpolatedData);
    }

    // 创建数据副本
    private List<SleepDataModel> copyData(List<SleepDataModel> original) {
        List<SleepDataModel> copy = new ArrayList<>();
        for (SleepDataModel item : original) {
            copy.add(new SleepData(item));
        }
        return copy;
    }

    // 最近邻插值
    private double interpolateNearest(List<SleepDataModel> data, int index, int type, int lookback) {
        // 查找最近的非零值
        for (int i = index - 1; i >= Math.max(0, index - lookback); i--) {
            double value = getValue(data.get(i), type);
            if (value != 0) {
                return value;
            }
        }
        // 如果前几个点都是0，则返回0
        return 0;
    }

    // 获取指定类型的值
    private double getValue(SleepDataModel data, int type) {
        switch (type) {
            case 4: return data.getHeartRate();
            case 5: return data.getBreathingRate();
            default: return 0;
        }
    }
}