package com.zxkkj.sleepAnalysis.processor;

import com.zxkkj.sleepAnalysis.model.SleepDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

public class SleepStagingProcessor {

    private static Logger logger = LoggerFactory.getLogger(SleepStagingProcessor.class);

    // 睡眠段统计信息
    public static class SleepSegmentStats {
        private final double maxHeartRate;
        private final double minHeartRate;
        private final double avgHeartRate;

        public SleepSegmentStats(double max, double min, double avg) {
            this.maxHeartRate = max;
            this.minHeartRate = min;
            this.avgHeartRate = avg;
        }

        public double getMaxHeartRate() { return maxHeartRate; }
        public double getMinHeartRate() { return minHeartRate; }
        public double getAvgHeartRate() { return avgHeartRate; }
    }

    // 睡眠分期信息
    public static class SleepPhase {
        private final int phaseType; // 1=醒, 2=浅睡, 3=深睡, 4=REM
        private final int startIndex;
        private int endIndex;
        //心率变异度
        private double heartRateVariability;

        public SleepPhase(int phaseType, int startIndex) {
            this.phaseType = phaseType;
            this.startIndex = startIndex;
            this.endIndex = startIndex;
        }

        public int getPhaseType() { return phaseType; }
        public int getStartIndex() { return startIndex; }
        public int getEndIndex() { return endIndex; }
        public void setEndIndex(int endIndex) { this.endIndex = endIndex; }
        public double getHeartRateVariability() { return heartRateVariability; }
        public void setHeartRateVariability(double hrVar) { this.heartRateVariability = hrVar; }

        public String getPhaseName() {
            switch (phaseType) {
                case 1: return "觉醒期";
                case 2: return "浅睡期";
                case 3: return "深睡期";
                case 4: return "REM期";
                default: return "未知";
            }
        }
    }

    // 睡眠段分析结果
    public static class SleepSegmentAnalysis {
        private final SleepSegmentStats stats;
        private final List<SleepPhase> phases;

        public SleepSegmentAnalysis(SleepSegmentStats stats, List<SleepPhase> phases) {
            this.stats = stats;
            this.phases = phases;
        }

        public SleepSegmentStats getStats() { return stats; }
        public List<SleepPhase> getPhases() { return phases; }
    }

    // 处理结果封装类
    public static class ProcessingResult {
        private final List<SleepSegmentAnalysis> segmentAnalyses;

        public ProcessingResult(List<SleepSegmentAnalysis> segmentAnalyses) {
            this.segmentAnalyses = segmentAnalyses;
        }

        public List<SleepSegmentAnalysis> getSegmentAnalyses() {
            return segmentAnalyses;
        }
    }

    public ProcessingResult process(List<SleepDataModel> data, List<int[]> sleepSegments) {
        List<SleepSegmentAnalysis> segmentAnalyses = new ArrayList<>();

        // 1. 计算基础心率（取在床后前30个数据点的最大心率）
        double baseHeartRate = calculateBaseHeartRate(data);

        // 2. 计算阈值
        double wakeToLightThreshold = baseHeartRate * (1 - 0.08);  // 醒转浅阈值
        double lightToDeepThreshold = baseHeartRate * (1 - 0.28);   // 浅转深阈值

        logger.debug("基础心率: {}, 醒转浅阈值: {}, 浅转深阈值: {}",
                new Object[]{baseHeartRate, wakeToLightThreshold, lightToDeepThreshold} );

        // 3. 处理每个睡眠段
        for (int[] segment : sleepSegments) {
            int start = segment[0];
            int end = segment[1];
            int length = end - start + 1;

            // 3.1 计算睡眠段的心率统计
            SleepSegmentStats stats = calculateSegmentStats(data, start, end);

            // 3.2 进行睡眠分期
            List<SleepPhase> phases = performSleepStaging(
                    data, start, end, wakeToLightThreshold, lightToDeepThreshold);

            // 3.3 计算心率变异度
            calculateHeartRateVariability(data, phases);

            segmentAnalyses.add(new SleepSegmentAnalysis(stats, phases));
        }

        return new ProcessingResult(segmentAnalyses);
    }

    // 计算基础心率（前30个数据点的最大心率）
    private double calculateBaseHeartRate(List<SleepDataModel> data) {
        int n = Math.min(30, data.size());
        double maxHr = 0;

        for (int i = 0; i < n; i++) {
            double hr = data.get(i).getHeartRate();
            if (hr > maxHr) {
                maxHr = hr;
            }
        }

        return maxHr;
    }

    // 计算睡眠段的心率统计
    private SleepSegmentStats calculateSegmentStats(List<SleepDataModel> data, int start, int end) {
        double maxHr = Double.MIN_VALUE;
        double minHr = Double.MAX_VALUE;
        double sumHr = 0;
        int count = 0;

        for (int i = start; i <= end; i++) {
            double hr = data.get(i).getHeartRate();
            if (hr > maxHr) maxHr = hr;
            if (hr < minHr) minHr = hr;
            sumHr += hr;
            count++;
        }

        double avgHr = sumHr / count;
        return new SleepSegmentStats(maxHr, minHr, avgHr);
    }

    // 进行睡眠分期
    private List<SleepPhase> performSleepStaging(List<SleepDataModel> data,
                                                 int start, int end,
                                                 double wakeToLightThreshold,
                                                 double lightToDeepThreshold) {
        List<SleepPhase> phases = new ArrayList<>();

        // 初始化第一个分期（从觉醒期开始）
        phases.add(new SleepPhase(1, start)); // 觉醒期

        int currentPhaseIndex = 0;
        SleepPhase currentPhase = phases.get(currentPhaseIndex);

        // 从第二个数据点开始处理
        for (int i = start + 1; i <= end; i++) {
            double currentHr = data.get(i).getHeartRate();
            double prevHr = data.get(i - 1).getHeartRate();

            int transitionType = determineTransition(
                    currentHr, prevHr, wakeToLightThreshold, lightToDeepThreshold);

            switch (transitionType) {
                case 1: // 醒转浅
                    currentPhase.setEndIndex(i - 1);
                    phases.add(new SleepPhase(2, i)); // 浅睡期
                    currentPhase = phases.get(phases.size() - 1);
                    break;

                case 2: // 醒转深
                    currentPhase.setEndIndex(i - 1);
                    phases.add(new SleepPhase(3, i)); // 深睡期
                    currentPhase = phases.get(phases.size() - 1);
                    break;

                case 3: // 浅转深
                    currentPhase.setEndIndex(i - 1);
                    phases.add(new SleepPhase(3, i)); // 深睡期
                    currentPhase = phases.get(phases.size() - 1);
                    break;

                case 4: // 浅转醒
                    currentPhase.setEndIndex(i - 1);
                    phases.add(new SleepPhase(1, i)); // 觉醒期
                    currentPhase = phases.get(phases.size() - 1);
                    break;

                case 5: // 深转醒
                    currentPhase.setEndIndex(i - 1);
                    phases.add(new SleepPhase(1, i)); // 觉醒期
                    currentPhase = phases.get(phases.size() - 1);
                    break;

                case 6: // 深转浅
                    currentPhase.setEndIndex(i - 1);
                    phases.add(new SleepPhase(2, i)); // 浅睡期
                    currentPhase = phases.get(phases.size() - 1);
                    break;

                case 7: // 数据尾（处理最后一个点）
                    currentPhase.setEndIndex(i);
                    break;

                default: // 无状态变化
                    // 继续当前分期
                    break;
            }
        }

        // 确保最后一个分期的结束点正确设置
        if (currentPhase.getEndIndex() < end) {
            currentPhase.setEndIndex(end);
        }

        return phases;
    }

    // 确定状态转换类型
    private int determineTransition(double currentHr, double prevHr,
                                    double wakeToLightThreshold,
                                    double lightToDeepThreshold) {
        if (currentHr <= wakeToLightThreshold && prevHr > wakeToLightThreshold) {
            return 1; // 醒转浅
        }
        if (currentHr <= lightToDeepThreshold && prevHr > wakeToLightThreshold) {
            return 2; // 醒转深
        }
        if (currentHr <= lightToDeepThreshold && prevHr > lightToDeepThreshold && prevHr <= wakeToLightThreshold) {
            return 3; // 浅转深
        }
        if (currentHr >= wakeToLightThreshold && prevHr > lightToDeepThreshold && prevHr <= wakeToLightThreshold) {
            return 4; // 浅转醒
        }
        if (currentHr >= wakeToLightThreshold && prevHr < lightToDeepThreshold) {
            return 5; // 深转醒
        }
        if (currentHr >= lightToDeepThreshold && prevHr < lightToDeepThreshold) {
            return 6; // 深转浅
        }
        return 0; // 无状态变化
    }

    // 计算心率变异度
    private void calculateHeartRateVariability(List<SleepDataModel> data, List<SleepPhase> phases) {
        double remHrVariabilityThreshold = 5.0; // REM心率变异度阈值
        if (!phases.isEmpty()) {
            SleepPhase sleepPhase = phases.get(phases.size() - 1);
            for (int i = 0; i < phases.get(phases.size() - 1).getEndIndex(); i++) {
                double stdDev = calculateHeartRateStdDev(
                        data,
                        sleepPhase.getStartIndex(),
                        sleepPhase.getEndIndex()
                );
                sleepPhase.setHeartRateVariability(stdDev);

                // 如果心率变异度超过阈值且不是觉醒期，标记为REM
                if (stdDev >= remHrVariabilityThreshold) {
                    setPhaseType(sleepPhase, 4);
                }
            }
        }
    }

    private static void setPhaseType(SleepPhase phase, int newType) {
        try {
            java.lang.reflect.Field field = SleepPhase.class.getDeclaredField("phaseType");
            field.setAccessible(true);
            field.set(phase, newType);
        } catch (Exception e) {
            logger.error("无法修改睡眠阶段类型: " + e.getMessage());
        }
    }

    private static double calculateHeartRateStdDev(List<SleepDataModel> data, int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex >= data.size() || startIndex > endIndex) {
            return 0.0;
        }

        // 提取心率数据
        DoubleSummaryStatistics stats = data.subList(startIndex, endIndex + 1)
                .stream().mapToDouble(SleepDataModel::getHeartRate).summaryStatistics();

        if (stats.getCount() == 0) return 0.0;

        double mean = stats.getAverage();

        double variance = data.subList(startIndex, endIndex + 1)
                .stream().mapToDouble(model -> {
                    double diff = model.getHeartRate() - mean;
                    return diff * diff;
                }).average().orElse(0.0);

        return Math.sqrt(variance);
    }
}