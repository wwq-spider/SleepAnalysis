package com.zxkkj.sleepAnalysis;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvWriter;
import com.zxkkj.sleepAnalysis.analyzer.*;
import com.zxkkj.sleepAnalysis.model.SleepDataModel;
import com.zxkkj.sleepAnalysis.processor.*;
import com.zxkkj.sleepAnalysis.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 应用程序
 */
public class Application {
    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        logger.info("程序启动，入参: {}", Arrays.toString(args));
        String dataFile = "";
        String outTxtPath = "";
        //localTest
        dataFile = "/Users/heyuqi/Desktop/sleep/问题数据";
        outTxtPath = "/Users/heyuqi/Desktop/sleep/outTxtPath/";

        /*dataFile = args[0];
        outTxtPath = args[1];*/

        List<File> fileList = FileUtil.loopFiles(dataFile);

        for (File file : fileList) {
            DataReader reader = new CsvDataReader();
            DataProcessor dataProcessor = new SleepDataPreprocessor();
            DataAnalyzer analyzer = new SleepDataAnalyzer();
            // 构建处理管道
            DataProcessingPipeline pipeline = new DataProcessingPipeline(reader, dataProcessor, analyzer);
            List<SleepDataModel> sleepDataList = pipeline.execute(file.getAbsolutePath());

            // 创建处理器
            HeartRateSegmentProcessor processor = new HeartRateSegmentProcessor();

            // 处理数据
            HeartRateSegmentProcessor.ProcessingResult hrResult = processor.process(sleepDataList);

            // 获取处理后的数据
            List<SleepDataModel> processedData = hrResult.getProcessedData();

            // 获取零心率段信息
            List<int[]> zeroSegments = hrResult.getZeroHeartRateSegments();
            int zeroSegmentCount = hrResult.getZeroSegmentCount();

            // 处理呼吸率段
            BreathingRateSegmentProcessor brProcessor = new BreathingRateSegmentProcessor();
            BreathingRateSegmentProcessor.ProcessingResult brResult =
                    brProcessor.process(processedData, hrResult.getZeroHeartRateSegments());

            // 6. 处理离床时间段
            OffBedSegmentProcessor offBedProcessor = new OffBedSegmentProcessor();
            OffBedSegmentProcessor.ProcessingResult offBedResult = offBedProcessor.process(sleepDataList);

            // 处理弱呼吸时间段
            WeakBreathingSegmentProcessor weakBreathProcessor = new WeakBreathingSegmentProcessor();
            WeakBreathingSegmentProcessor.ProcessingResult weakBreathResult = weakBreathProcessor.process(sleepDataList);

            // 处理打鼾时间段
            SnoreSegmentProcessor snoreProcessor = new SnoreSegmentProcessor();
            SnoreSegmentProcessor.ProcessingResult snoreResult = snoreProcessor.process(sleepDataList);

            // 处理睡眠段和插值
            SleepSegmentProcessor sleepSegmentProcessor = new SleepSegmentProcessor();
            SleepSegmentProcessor.ProcessingResult sleepResult = sleepSegmentProcessor.process(offBedResult.getProcessedData(),
                            offBedResult.getOffBedSegments());

            // 使用插值后的数据进行后续分析
            List<SleepDataModel> interpolatedData = sleepResult.getInterpolatedData();

            // 处理平滑滤波
            SmoothingProcessor smoothingProcessor = new SmoothingProcessor();
            SmoothingProcessor.ProcessingResult smoothResult = smoothingProcessor.process(interpolatedData,
                    sleepResult.getSleepSegments());

            // 使用平滑后的数据进行后续分析
            List<SleepDataModel> smoothedData = smoothResult.getSmoothedData();

            // 进行睡眠分期分析
            SleepStagingProcessor stagingProcessor = new SleepStagingProcessor();
            SleepStagingProcessor.ProcessingResult stagingResult = stagingProcessor.process(smoothedData,
                    sleepResult.getSleepSegments());

            //分析结果输出
            File fileCurrent = new File(outTxtPath + file.getName()
                    .substring(0,file.getName().lastIndexOf(".")) + ".txt");
            FileWriter fw = null;
            //睡眠分期结果写入csv文件
            CsvWriter writer = null;
            List<int[]> stagingList = new ArrayList<>();
            try {
                //文件不存在才创建
                if (!fileCurrent.exists()){
                    fileCurrent.createNewFile();
                }
                fw = new FileWriter(fileCurrent.getPath());
                //输出结论中次数相关的指标小于0，监测数据无效
                if (offBedResult.getOffBedSegmentCount() < 0 || weakBreathResult.getSegmentCount() < 0
                        || snoreResult.getSnoreSegmentCount() < 0){
                    fw.write("监测数据存在错误，修正后仍无法分析");
                }else {
                    // 全程最大心率
                    fw.write(sleepDataList.stream().mapToDouble(v -> v.getHeartRate()).max().orElse(0.0) + "" + " ");
                    // 全程最小心率
                    fw.write(sleepDataList.stream().mapToDouble(v -> v.getHeartRate()).min().orElse(0.0) + "" + " ");
                    // 全程平均心率
                    fw.write(CommonUtils.twoDecimalD(sleepDataList.stream().mapToDouble(v -> v.getHeartRate()).average().orElse(0.0)) + "" + " ");
                    // 全程最大呼吸率
                    fw.write(sleepDataList.stream().mapToDouble(v -> v.getBreathingRate()).max().orElse(0.0) + "" + " ");
                    // 全程最小呼吸率
                    fw.write(sleepDataList.stream().mapToDouble(v -> v.getBreathingRate()).min().orElse(0.0) + "" + " ");
                    // 全程平均呼吸率
                    fw.write(CommonUtils.twoDecimalD(sleepDataList.stream().mapToDouble(v -> v.getBreathingRate()).average().orElse(0.0)) + "" + " ");
                    //全程最大血氧饱和度
                    fw.write(sleepDataList.stream().mapToDouble(v -> v.getBloodOxygen()).max().orElse(0.0) + "" + " ");
                    //全程最小血氧饱和度
                    fw.write(sleepDataList.stream().mapToDouble(v -> v.getBloodOxygen()).min().orElse(0.0) + "" + " ");
                    //全程平均血氧饱和度
                    fw.write(CommonUtils.twoDecimalD(sleepDataList.stream().mapToDouble(v -> v.getBloodOxygen()).average().orElse(0.0)) + "" + " ");
                    //监测总时长
                    fw.write(sleepDataList.size() + "" + " ");
                    //在床总时长
                    fw.write(sleepDataList.size() - offBedResult.getOffBedTotalDuration() + "" + " ");

                    int totalWakeDuration = 0;
                    int totalLightSleepDuration = 0;
                    int totalDeepSleepDuration = 0;
                    int totalREMDuration = 0;
                    int duration = 0;

                    for (int segIdx = 0; segIdx < stagingResult.getSegmentAnalyses().size(); segIdx++) {
                        SleepStagingProcessor.SleepSegmentAnalysis analysis =
                                stagingResult.getSegmentAnalyses().get(segIdx);
                        List<SleepStagingProcessor.SleepPhase> phases = analysis.getPhases();
                        for (SleepStagingProcessor.SleepPhase phase : phases) {
                            duration += phase.getEndIndex() - phase.getStartIndex() + 1;
                            // 统计总时长
                            switch (phase.getPhaseType()) {
                                case 1: totalWakeDuration += duration; break;
                                case 2: totalLightSleepDuration += duration; break;
                                case 3: totalDeepSleepDuration += duration; break;
                                case 4: totalREMDuration += duration; break;
                            }
                            stagingList.add(new int[]{phase.getPhaseType(),phase.getStartIndex(),phase.getEndIndex()});
                        }
                    }
                    int totalSleepDuration = totalLightSleepDuration + totalDeepSleepDuration + totalREMDuration;
                    int totalDuration = totalWakeDuration + totalSleepDuration;
                    //睡眠总时长
                    fw.write(duration + "" + " ");
                    //浅睡眠总时长
                    fw.write(totalLightSleepDuration + "" + " ");
                    //浅睡眠比例
                    fw.write(100.0 * totalLightSleepDuration / totalDuration + "" + " ");
                    //深睡眠总时长
                    fw.write(totalDeepSleepDuration + "" + " ");
                    //深睡眠比例
                    fw.write(100.0 * totalDeepSleepDuration / totalDuration + "" + " ");
                    //rem总时长
                    fw.write(totalREMDuration + "" + " ");
                    //rem比例
                    fw.write(100.0 * totalREMDuration / totalDuration + "" + " ");
                    //离床次数
                    fw.write(offBedResult.getOffBedSegmentCount() + "" + " ");
                    //离床总时间
                    fw.write(offBedResult.getOffBedTotalDuration() + "" + " ");
                    //睡眠分段数量
                    fw.write(stagingResult.getSegmentAnalyses().size() + "" + " ");
                    //低通气次数
                    fw.write( weakBreathResult.getSegmentCount() + "" + " ");
                    //低通气总时长
                    fw.write( weakBreathResult.getTotalDuration() + "" + " ");
                    //打鼾次数
                    fw.write( snoreResult.getSnoreSegmentCount() + "" + " ");
                    //打鼾总时长
                    fw.write( snoreResult.getSnoreTotalDuration() + "" + " ");
                    //呼吸阻塞（分别用2代表呼吸阻塞明显，1代表有呼吸阻塞，0代表无呼吸阻塞）
                    fw.write( 0 + "" + " ");
                    //睡眠总体评价（分别用2代表好，1代表较好，0代表差）
                    //睡眠总体评价(根据深睡眠比例：大于30% 好、15% - 30% 较好、小于15%)
                    double temp = 100.0 * totalDeepSleepDuration / totalDuration;
                    if (temp >= 30.0){
                        fw.write( 2 + "" + " ");
                    }else if (temp < 30.0 && temp >= 15.0){
                        fw.write( 1 + "" + " ");
                    }else {
                        fw.write( 0 + "" + " ");
                    }

                }
                FileOutputStream fileOutputStream = new FileOutputStream
                        (outTxtPath + file.getName().substring(0,file.getName().lastIndexOf(".")) + ".csv");
                fileOutputStream.write(0xef);
                fileOutputStream.write(0xbb);
                fileOutputStream.write(0xbf);

                if (stagingList == null || stagingList.size() == 0) {
                    stagingList.add(new int[]{0,0,0});
                }
                writer = new CsvWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8.name()));
                writer.write(stagingList);
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                try {
                    writer.close();
                    fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
