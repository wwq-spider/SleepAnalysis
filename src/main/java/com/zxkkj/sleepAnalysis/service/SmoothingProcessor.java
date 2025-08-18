package com.zxkkj.sleepAnalysis.service;

import com.zxkkj.sleepAnalysis.model2.SleepData;

import java.util.ArrayList;
import java.util.List;

public class SmoothingProcessor {

    // 处理结果封装类
    public static class ProcessingResult {
        private final List<SleepDataModel> smoothedData;

        public ProcessingResult(List<SleepDataModel> smoothedData) {
            this.smoothedData = smoothedData;
        }

        public List<SleepDataModel> getSmoothedData() {
            return smoothedData;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data, List<int[]> sleepSegments) {
        // 创建数据副本用于平滑
        List<SleepDataModel> smoothedData = copyData(data);

        // 对每个睡眠段进行平滑处理
        for (int[] segment : sleepSegments) {
            int start = segment[0];
            int end = segment[1];
            int length = end - start + 1;

            // 计算合适的窗口大小（不超过睡眠段长度）
            int windowSize = Math.min(1000, length);

            // 平滑心率数据
            smoothData(smoothedData, start, end, windowSize, true);

            // 平滑呼吸率数据
            smoothData(smoothedData, start, end, windowSize, false);
        }

        return new ProcessingResult(smoothedData);
    }

    // 创建数据副本
    private List<SleepDataModel> copyData(List<SleepDataModel> original) {
        List<SleepDataModel> copy = new ArrayList<>();
        for (SleepDataModel item : original) {
            copy.add(new SleepData(item));
        }
        return copy;
    }

    // 平滑数据
    private void smoothData(List<SleepDataModel> data, int start, int end,
                            int windowSize, boolean isHeartRate) {
        int halfWindow = windowSize / 2;
        int length = end - start + 1;

        // 创建原始数据数组
        double[] original = new double[length];
        for (int i = 0; i < length; i++) {
            int index = start + i;
            original[i] = isHeartRate ?
                    data.get(index).getHeartRate() :
                    data.get(index).getBreathingRate();
        }

        // 应用移动平均滤波器
        double[] smoothed = movingAverage(original, windowSize);

        // 将平滑后的值写回数据
        for (int i = 0; i < length; i++) {
            int index = start + i;
            if (isHeartRate) {
                data.get(index).setHeartRate(smoothed[i]);
            } else {
                data.get(index).setBreathingRate(smoothed[i]);
            }
        }
    }

    // 移动平均滤波器实现
    private double[] movingAverage(double[] data, int windowSize) {
        int n = data.length;
        double[] result = new double[n];
        int halfWindow = windowSize / 2;

        // 处理边界情况
        for (int i = 0; i < n; i++) {
            int windowStart = Math.max(0, i - halfWindow);
            int windowEnd = Math.min(n - 1, i + halfWindow);
            int actualWindowSize = windowEnd - windowStart + 1;

            double sum = 0;
            for (int j = windowStart; j <= windowEnd; j++) {
                sum += data[j];
            }

            result[i] = sum / actualWindowSize;
        }

        return result;
    }
}