package com.zxkkj.sleepAnalysis.service;

import java.util.ArrayList;
import java.util.List;

public class SnoreSegmentProcessor {

    // 处理结果封装类
    public static class ProcessingResult {
        private final List<int[]> snoreSegments;
        private final int snoreSegmentCount;
        private final int snoreTotalDuration;

        public ProcessingResult(List<int[]> snoreSegments,
                                int snoreSegmentCount,
                                int snoreTotalDuration) {
            this.snoreSegments = snoreSegments;
            this.snoreSegmentCount = snoreSegmentCount;
            this.snoreTotalDuration = snoreTotalDuration;
        }

        public List<int[]> getSnoreSegments() {
            return snoreSegments;
        }

        public int getSnoreSegmentCount() {
            return snoreSegmentCount;
        }

        public int getSnoreTotalDuration() {
            return snoreTotalDuration;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data) {
        int m = data.size();
        List<int[]> snoreSegments = new ArrayList<>();
        boolean inSnoreSegment = false;
        int segmentStart = 0;
        int n = 0; // 打鼾段计数器

        // 1. 检测打鼾时间段
        for (int i = 0; i < m - 1; i++) {
            int currentBedStatus = data.get(i).getBedStatus();
            int nextBedStatus = data.get(i + 1).getBedStatus();

            if (currentBedStatus == 5) { // 打鼾状态
                if (!inSnoreSegment) {
                    // 开始新的打鼾段
                    segmentStart = i;
                    inSnoreSegment = true;

                    // 检查下一时刻状态是否改变
                    if (nextBedStatus != currentBedStatus) {
                        // 单点打鼾段
                        snoreSegments.add(new int[]{segmentStart, i});
                        inSnoreSegment = false;
                        n++;
                    }
                } else {
                    // 已经在打鼾段中，检查是否保持打鼾状态
                    if (nextBedStatus != currentBedStatus) {
                        // 打鼾状态结束
                        snoreSegments.add(new int[]{segmentStart, i});
                        inSnoreSegment = false;
                        n++;
                    }
                }
            }
        }

        // 2. 处理最后可能的打鼾段
        if (inSnoreSegment) {
            // 如果最后一段延伸到结尾
            snoreSegments.add(new int[]{segmentStart, m - 1});
            n++;
        }

        // 3. 统计结果
        int snoreSegmentCount = n;
        int snoreTotalDuration = 0;

        if (snoreSegmentCount > 0) {
            // 计算打鼾总时长
            for (int[] segment : snoreSegments) {
                snoreTotalDuration += segment[1] - segment[0] + 1;
            }
        }

        return new ProcessingResult(snoreSegments, snoreSegmentCount, snoreTotalDuration);
    }
}