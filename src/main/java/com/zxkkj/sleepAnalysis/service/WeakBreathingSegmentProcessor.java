package com.zxkkj.sleepAnalysis.service;

import java.util.ArrayList;
import java.util.List;

public class WeakBreathingSegmentProcessor {

    // 处理结果封装类
    public static class ProcessingResult {
        private final List<int[]> weakBreathingSegments;
        private final int segmentCount;
        private final int totalDuration;

        public ProcessingResult(List<int[]> weakBreathingSegments,
                                int segmentCount,
                                int totalDuration) {
            this.weakBreathingSegments = weakBreathingSegments;
            this.segmentCount = segmentCount;
            this.totalDuration = totalDuration;
        }

        public List<int[]> getWeakBreathingSegments() {
            return weakBreathingSegments;
        }

        public int getSegmentCount() {
            return segmentCount;
        }

        public int getTotalDuration() {
            return totalDuration;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data) {
        int m = data.size();
        List<int[]> segments = new ArrayList<>();
        boolean inSegment = false;
        int segmentStart = 0;
        int n = 0; // 弱呼吸段计数器

        // 1. 检测弱呼吸时间段
        for (int i = 0; i < m - 1; i++) {
            int currentBedStatus = data.get(i).getBedStatus();
            int nextBedStatus = data.get(i + 1).getBedStatus();

            if (currentBedStatus == 3) { // 弱呼吸状态
                if (!inSegment) {
                    // 开始新的弱呼吸段
                    segmentStart = i;
                    inSegment = true;

                    // 检查下一时刻状态是否改变
                    if (nextBedStatus != currentBedStatus) {
                        // 单点弱呼吸段
                        segments.add(new int[]{segmentStart, i});
                        inSegment = false;
                        n++;
                    }
                } else {
                    // 已经在弱呼吸段中，检查是否保持弱呼吸状态
                    if (nextBedStatus != currentBedStatus) {
                        // 弱呼吸状态结束
                        segments.add(new int[]{segmentStart, i});
                        inSegment = false;
                        n++;
                    }
                }
            }
        }

        // 2. 处理最后可能的弱呼吸段
        if (inSegment) {
            // 如果最后一段延伸到结尾
            segments.add(new int[]{segmentStart, m - 1});
            n++;
        }

        // 3. 统计结果
        int segmentCount = segments.size();
        int totalDuration = 0;

        if (segmentCount > 0) {
            // 计算弱呼吸总时长
            for (int[] segment : segments) {
                totalDuration += segment[1] - segment[0] + 1;
            }
        }

        return new ProcessingResult(segments, segmentCount, totalDuration);
    }
}