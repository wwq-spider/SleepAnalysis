package com.zxkkj.sleepAnalysis.processor;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;

import java.util.ArrayList;
import java.util.List;

public class OffBedSegmentProcessor {
    public static class ProcessingResult {
        private final List<SleepDataModel> processedData;
        private final List<int[]> offBedSegments;
        private final int offBedSegmentCount;
        private final int offBedTotalDuration;

        public ProcessingResult(List<SleepDataModel> processedData,
                                List<int[]> offBedSegments,
                                int offBedTotalDuration) {
            this.processedData = processedData;
            this.offBedSegments = offBedSegments;
            this.offBedSegmentCount = offBedSegments.size();
            this.offBedTotalDuration = offBedTotalDuration;
        }

        public List<SleepDataModel> getProcessedData() {
            return processedData;
        }

        public List<int[]> getOffBedSegments() {
            return offBedSegments;
        }

        public int getOffBedSegmentCount() {
            return offBedSegmentCount;
        }

        public int getOffBedTotalDuration() {
            return offBedTotalDuration;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data) {
        if (data == null || data.isEmpty()) {
            return new ProcessingResult(data, new ArrayList<>(), 0);
        }

        List<int[]> offBedSegments = new ArrayList<>();
        int segmentStart = 0;
        int flag = 0;

        // 检测离床时间段
        for (int i = 0; i < data.size() - 1; i++) {
            int currentBedStatus = data.get(i).getBedStatus();
            int nextBedStatus = data.get(i + 1).getBedStatus();

            if (currentBedStatus == 1) { // 当前为离床状态
                if (flag == 0) {
                    // 开始新的离床段
                    segmentStart = i;
                    flag = 1;
                    if (nextBedStatus - currentBedStatus != 0) {
                        // 单点离床段
                        offBedSegments.add(new int[]{segmentStart, i});
                        flag = 0;
                    }
                } else {
                    // 已经在离床段中，检查是否结束
                    if (nextBedStatus - currentBedStatus != 0 && flag != 0) {
                        // 离床段结束
                        offBedSegments.add(new int[]{segmentStart, i});
                        flag= 0;
                    }
                }
            }
        }

        // 清理无效段
        int i = 0;
        while (i < offBedSegments.size()) {
            int[] segment = offBedSegments.get(i);
            // 如果段内容均为0或起始终止时刻相同，则为无效段
            if ((segment[0] == 0 && segment[1] == 0) || segment[0] == segment[1]) {
                offBedSegments.remove(i);
            } else {
                i++;
            }
        }

        // 删除起床后的离床段
        if (!offBedSegments.isEmpty()) {
            int[] lastSegment = offBedSegments.get(offBedSegments.size() - 1);
            if (lastSegment[1] >= data.size() - 1) {
                offBedSegments.remove(offBedSegments.size() - 1);
            }
        }

        // 计算离床总时长
        int offBedTotalDuration = 0;
        for (int[] segment : offBedSegments) {
            offBedTotalDuration += segment[1] - segment[0] + 1;
        }

        return new ProcessingResult(data, offBedSegments, offBedTotalDuration);
    }
}