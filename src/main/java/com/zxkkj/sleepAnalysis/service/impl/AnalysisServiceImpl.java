package com.zxkkj.sleepAnalysis.service.impl;

import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.zxkkj.sleepAnalysis.constants.Constants;
import com.zxkkj.sleepAnalysis.model.*;
import com.zxkkj.sleepAnalysis.service.IAnalysisService;
import com.zxkkj.sleepAnalysis.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AnalysisServiceImpl implements IAnalysisService {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public SleepData loadDataByLoaclFile(File localFile) {
        //解析csv文件
        CsvReadConfig csvConfig = new CsvReadConfig();
        List<CsvRow> rowList = CsvUtil.getReader(csvConfig).read(localFile).getRows();
        //睡眠数据 状态-心率-呼吸
        List<SleepInfo> sleepInfo = this.readSleepInfo(rowList);
        SleepData sleepData = new SleepData();
        sleepData.setSleepInfoList(sleepInfo);
        //原始数据
        sleepData.setRowList(rowList);
        return sleepData;
    }

    /**
     * 读取数据：状态-心率-呼吸
     * @param rowList
     * @return
     */
    private List<SleepInfo> readSleepInfo(List<CsvRow> rowList) {
        List<SleepInfo> list = new ArrayList<>();
        for (int i = 1; i < rowList.size() - 1; i++) {//第一行表头过滤
            SleepInfo sleepInfo = new SleepInfo();
            sleepInfo.setStatus(Integer.parseInt(rowList.get(i).get(0)));//状态
            sleepInfo.setHr(Double.valueOf(rowList.get(i).get(1)));//心率
            sleepInfo.setRe(Double.valueOf(rowList.get(i).get(2)));//呼吸
            list.add(sleepInfo);
        }
        return list;
    }

    @Override
    public AnalysisReult executeAnalysis(SleepData sleepData) {
        List<SleepInfo> sleepInfoList = sleepData.getSleepInfoList();
        //离床数据分析
        SleepData leaveData = new SleepData();
        leaveData = this.leaveOnBed(sleepInfoList);
        sleepData.setLeaveDatas(leaveData.getLeaveDatas());
        sleepData.setOffBedAllTime(leaveData.getOffBedAllTime());
        sleepData.setOffBedTime(leaveData.getOffBedTime());
        //打鼾数据分析
        SleepData sonreData = new SleepData();
        sonreData = this.getSnoreData(sleepInfoList);
        sleepData.setSnoreInfoList(sonreData.getSnoreInfoList());
        sleepData.setSnoreAllTimes(sonreData.getSnoreAllTimes());
        sleepData.setSnoreAllTime(sleepData.getSnoreAllTime());
        //在床数据分析
        List<SleepData.OnBedData> onBedDataList = new ArrayList<>();
        List<LeaveOnBedInfo> leaveDatas = sleepData.getLeaveDatas();
        onBedDataList = this.getOnBedDataInfo(leaveDatas,sleepInfoList);
        sleepData.setBedData(onBedDataList);
        //剔除各睡眠段偶发0数据并进行临近插值处理
        sleepInfoList = this.insertValue(onBedDataList,sleepInfoList);
        //对非0心率0呼吸段的数据进行平滑滤波
        List<SleepInfo> Data_smooth = sleepInfoList;
        for (int i = 0; i < onBedDataList.size(); i++) {
            int start = onBedDataList.get(i).getHrStartTime();
            int end = onBedDataList.get(i).getOnBenEndTime();
            Data_smooth = this.smooth(Data_smooth,start,end);
        }
        //全程心率最大、最小、平均值
        AnalysisReult.HrStatInfo hrStatInfo = this.sleepPhaseHrDate(Data_smooth,onBedDataList,sleepData);
        //睡眠分期
        List<AnalysisReult.SleepStage> analysisReult = new ArrayList<>();
        for (int i = 0; i < onBedDataList.size(); i++) {
            if (onBedDataList.get(i).getHrStartTime() < Data_smooth.size() - 100){
                if (i == 0){
                    analysisReult.addAll(sleepStageSplit(Data_smooth,onBedDataList.get(i),true,hrStatInfo));
                }else {
                    analysisReult.addAll(sleepStageSplit(Data_smooth,onBedDataList.get(i),false,hrStatInfo));
                }
            }
        }
        int sleepTotalTime = 0;//睡眠总时长
        int shallowSleepTotalTime = 0;//浅睡眠总时长
        int deepSleepTotalTime = 0;//深睡眠总时长
        int remSleepTotalTime = 0;//REM总时长
        for (int i = 0; i < analysisReult.size(); i++) {
            sleepTotalTime += (analysisReult.get(i).getEndTime() - analysisReult.get(i).getStartTime());
            if (analysisReult.get(i).getType() == AnalysisReult.SleepStageType.ShallowSleep.getValue()){
                shallowSleepTotalTime += (analysisReult.get(i).getEndTime() - analysisReult.get(i).getStartTime());
            }
            if (analysisReult.get(i).getType() == AnalysisReult.SleepStageType.DeepSleep.getValue()){
                deepSleepTotalTime += (analysisReult.get(i).getEndTime() - analysisReult.get(i).getStartTime());
            }
            if (analysisReult.get(i).getType() == AnalysisReult.SleepStageType.RemSleep.getValue()){
                remSleepTotalTime += (analysisReult.get(i).getEndTime() - analysisReult.get(i).getStartTime());
            }
        }
        //结果汇总
        AnalysisReult analysisReultEnd = new AnalysisReult();
        analysisReultEnd.setMonitorTotalTime(sleepInfoList.size());//监测总时长(秒)
        analysisReultEnd.setLeaveBedTotalTime(sleepData.getOffBedAllTime());//离床总时间
        analysisReultEnd.setOnBedTotalTime(sleepInfoList.size() - sleepData.getOffBedAllTime());//在床总时长(秒)
        analysisReultEnd.setLeaveBedTimes(sleepData.getLeaveDatas().size());//离床次数
        analysisReultEnd.setSleepSplitNum(onBedDataList.size());//睡眠分段数量
        analysisReultEnd.setSleepTotalTime(sleepTotalTime);//睡眠总时长
        analysisReultEnd.setShallowSleepTotalTime(shallowSleepTotalTime);//浅睡眠总时长
        analysisReultEnd.setDeepSleepTotalTime(deepSleepTotalTime);//深睡眠总时长
        analysisReultEnd.setRemSleepTotalTime(remSleepTotalTime);//rem总时长
        return analysisReultEnd;
    }

    @Override
    public void writeAnalysisResult(AnalysisReult analysisReult) {

    }

    /**
     * 计算睡眠全程心率最大、最小、平均值
     * @param Data_smooth
     * @param onBedDataList
     * @param sleepData
     * @return
     */
    private AnalysisReult.HrStatInfo sleepPhaseHrDate(List<SleepInfo> Data_smooth,List<SleepData.OnBedData> onBedDataList,SleepData sleepData){

        List<AnalysisReult.HrStatInfo> hrStatInfoList = new ArrayList<>();
        AnalysisReult analysisReult = new AnalysisReult();
        
        for (int i = 0; i < onBedDataList.size(); i++) {
            AnalysisReult.HrStatInfo hrStatInfo = new AnalysisReult.HrStatInfo();
            int start = onBedDataList.get(i).getHrStartTime();
            int end = onBedDataList.get(i).getOnBenEndTime();
            if (start < end){
                //各睡眠段心率最大、最小、平均
                double phaseHr_max = Math.round(Data_smooth.get(start).getHr());//假设第一个为最大值
                double phaseHr_min = Math.round(Data_smooth.get(start).getHr());//假设第一个为最小值
                double phaseHr_avg = 0.0;
                double avaTemp = 0;
                for (int j = start; j < end; j++) {
                    if (Math.round(Data_smooth.get(j).getHr()) > phaseHr_max){//求心率最大值
                        phaseHr_max = Math.round(Data_smooth.get(j).getHr());
                    }
                    if (Math.round(Data_smooth.get(j).getHr()) < phaseHr_min){//求心率最小值
                        phaseHr_min = Math.round(Data_smooth.get(j).getHr());
                    }
                    avaTemp += Math.round(Data_smooth.get(j).getHr());//心率平均值
                }
                phaseHr_avg = avaTemp/(end-start);
                hrStatInfo.setMax(phaseHr_max);
                hrStatInfo.setMin(phaseHr_min);
                hrStatInfo.setAvg(Math.round(phaseHr_avg));
                hrStatInfoList.add(hrStatInfo);
            }
        }
        //各睡眠段心率最大、最小、平均值计算end
        analysisReult.setHrStatInfoList(hrStatInfoList);
        
        //全程心率最大、最小、平均值计算start
        double hr_max_all = hrStatInfoList.get(0).getMax();
        double hr_min_all = hrStatInfoList.get(0).getMin();
        double hr_avg_all = 0.0;
        double temp = 0.0;
        for (int i = 0; i < hrStatInfoList.size(); i++) {
            if (hrStatInfoList.get(i).getMax() > hr_max_all){
                hr_max_all = hrStatInfoList.get(i).getMax();
            }
            if (hrStatInfoList.get(i).getMin() < hr_min_all){
                hr_min_all = hrStatInfoList.get(i).getMin();
            }
            temp += hrStatInfoList.get(i).getAvg();
        }
        hr_avg_all =  temp/hrStatInfoList.size();
        AnalysisReult.HrStatInfo hrStatInfo = new AnalysisReult.HrStatInfo();
        hrStatInfo.setMax(Math.round(hr_max_all));
        hrStatInfo.setMin(Math.round(hr_min_all));
        hrStatInfo.setAvg(Math.round(hr_avg_all));
        return hrStatInfo;
    }


    /**
     * 计算该在床时间段的睡眠分期
     * @param sleepInfoList 整个睡眠数据
     * @param onBedData     当前睡眠段
     * @param first         是否为首次入睡睡眠段
     * @param htStatByWhole 整个睡眠周期的心率统计值
     * @return
     */
    private List<AnalysisReult.SleepStage> sleepStageSplit(List<SleepInfo> sleepInfoList, SleepData.OnBedData onBedData, boolean first, AnalysisReult.HrStatInfo htStatByWhole) {
        //先获取该睡眠段开始100秒内的最大心率
        int maxHr = CommonUtils.maxOrMinHr(sleepInfoList, onBedData.getHrStartTime(), onBedData.getHrStartTime() + 100, Constants.Max)[0];

        List<AnalysisReult.SleepStage> sleepStageList = new ArrayList<>();

        //首次入睡时的心率 阈值
        double firstSleepHrPrecent = 1 - 0.088;
        double firstSleepHrThreshold = maxHr * firstSleepHrPrecent;

        //非首次入睡时的心率 阈值
        double noFirstSleepHrPrecent = 1 - 0.033;
        double noFirstSleepHrThreshold = maxHr * noFirstSleepHrPrecent;

        //从开始有心率的时刻开始找入睡期
        //刚开始入睡是心率下降期  所以这里需要找到入睡点
        for (int i = onBedData.getHrStartTime(); i <= onBedData.getOnBenEndTime(); i++) {
            if (i<sleepInfoList.size()){
                SleepInfo sleepInfo = sleepInfoList.get(i);
                //判断是首次入睡 还是非首次入睡 心率阈值不一样
                boolean flag = first ? sleepInfo.getHr() <= firstSleepHrThreshold : sleepInfo.getHr() <= noFirstSleepHrThreshold;
                if (flag) {
                    sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.WakeUP.getValue(), onBedData.getOnBedStartTime(), i));
                    break;
                }
            }
        }

        //开始找浅睡期 先计算心率波峰和波谷
        List<Integer[]> troughAndPeakList = calTroughAndPeakByBedData(sleepInfoList, onBedData);

        //与最低心率差值 阈值
        int minHrDiffThreshold = 6;
        //波谷前偏移时间
        int troughBeforeTimeThreshold = 3 * 60;
        //波谷后偏移时间
        int troughAfterTimeThreshold1 = 2 * 60;
        //波谷后偏移时间
        int troughAfterTimeThreshold2 = 1 * 60;

        //波峰与最大心率差值阈值
        int maxHrDiffThreshold = 8;
        //波峰前时间阈值
        int peakBeforeTimeThreshold = 2 * 60;
        //波峰后时间阈值
        int peakAfterTimeThreshold = 3 * 60;

        //基于上面的心率波峰、波谷 计算浅睡期
        for (int i=0; i < troughAndPeakList.size(); i++) {
            AnalysisReult.SleepStage lastSleepStage = sleepStageList.get(sleepStageList.size() - 1);
            int troughOrPeakvalue = troughAndPeakList.get(i)[0];
            int troughOrPeakTime = troughAndPeakList.get(i)[1];
            if (i % 2 == 0) { //波谷
                if(troughOrPeakvalue - htStatByWhole.getMin() < minHrDiffThreshold) {
                    if (AnalysisReult.SleepStageType.ShallowSleep.getValue() == lastSleepStage.getType()) { //浅睡期
                        lastSleepStage.setEndTime(troughOrPeakTime - troughBeforeTimeThreshold);
                    } else { //如前期不是浅睡眠，则建立新浅睡眠分期
                        //第n期为浅睡、起始时刻为前期的结束时刻、终止时刻为波谷前3分钟
                        sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.ShallowSleep.getValue(),
                                lastSleepStage.getEndTime(), troughOrPeakTime - troughBeforeTimeThreshold));
                        //第n期为深睡、起始时刻为前期的结束时刻、终止时刻为波谷后2分钟
                        sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.DeepSleep.getValue(),
                                troughOrPeakTime - troughBeforeTimeThreshold, troughOrPeakTime + troughAfterTimeThreshold1));
                    }
                } else { //整段为浅睡眠
                    if (AnalysisReult.SleepStageType.ShallowSleep.getValue() == lastSleepStage.getType()) { //如果前段已经是浅睡眠，则延续
                        //将前期的浅睡结束时刻后延至为终止时刻为波谷后1分钟，n不变
                        lastSleepStage.setEndTime(troughOrPeakTime + troughAfterTimeThreshold2);
                    } else {
                        //第n期为浅睡、起始时刻为前期的结束时刻、终止时刻为波谷后1分钟
                        sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.ShallowSleep.getValue(),
                                lastSleepStage.getEndTime(), troughOrPeakTime + troughAfterTimeThreshold2));
                    }
                }
            } else { //波峰
                if (Math.abs(troughOrPeakTime - sleepInfoList.size()) < 1 * 60 && (htStatByWhole.getMax() - troughOrPeakvalue) < maxHrDiffThreshold) {
                    if (AnalysisReult.SleepStageType.ShallowSleep.getValue() == lastSleepStage.getType()) { //浅睡期
                        lastSleepStage.setEndTime(troughOrPeakTime - peakBeforeTimeThreshold);
                    } else {
                        //第n期为浅睡、起始时刻为前期的结束时刻、终止时刻为波峰前2分钟
                        sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.ShallowSleep.getValue(),
                                lastSleepStage.getEndTime(), troughOrPeakTime - peakBeforeTimeThreshold));
                        //第n期为觉醒、起始时刻为前期的结束时刻、终止时刻为监测终时刻
                        sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.WakeUP.getValue(),
                                troughOrPeakTime - peakBeforeTimeThreshold, sleepInfoList.size()));
                    }
                } else {
                    if (AnalysisReult.SleepStageType.ShallowSleep.getValue() == lastSleepStage.getType()) { //浅睡期
                        lastSleepStage.setEndTime(troughOrPeakTime - peakAfterTimeThreshold);
                    } else {
                        //第n期为浅睡、起始时刻为前期的结束时刻、终止时刻为波峰前3分钟
                        sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.ShallowSleep.getValue(),
                                lastSleepStage.getEndTime(), troughOrPeakTime - peakAfterTimeThreshold));
                        //第n期为REM、起始时刻为前期的结束时刻、终止时刻为波峰后3分钟
                        sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.RemSleep.getValue(),
                                troughOrPeakTime - peakAfterTimeThreshold, troughOrPeakTime + peakAfterTimeThreshold));
                    }
                }
            }
        }
        return sleepStageList;
    }

    /**
     * 计算每个睡眠段的波峰/波谷值、波峰/波谷时刻
     * @param sleepInfoList
     * @param onBedData
     * @return 返回长度为的数组 0:波峰/波谷值 1:所在位置
     */
    private List<Integer[]> calTroughAndPeakByBedData(List<SleepInfo> sleepInfoList, SleepData.OnBedData onBedData) {
        //开始时刻为0心率 开始时刻后20分钟？？？合理吗
        int startTime = onBedData.getHrStartTime() + 20 * 60;
        //结束时间
        Integer endTime = onBedData.getOnBenEndTime() - 12 * 60;

        List<Integer[]> result = new ArrayList<>();

        int windows = 50 * 60;

        while (startTime < endTime) {
            int k = result.size() + 1;
            int endCursor = 0;
            if (startTime + windows > sleepInfoList.size() - 120) { //防止超出数据长度
                endCursor = sleepInfoList.size() - 120;
            } else {
                endCursor += windows;
            }
            if (startTime < endCursor){
                Integer[] res = CommonUtils.maxOrMinHr(sleepInfoList, startTime, endCursor, k % 2 > 0 ? Constants.Min : Constants.Max);
                startTime = res[1] + 1; //指标向右移动
                result.add(new Integer[]{res[0], res[1]});
            }else {
                break;
            }
        }
        return result;
    }

    /**
     * 搜寻离床时间段：得出各离床时间段数组、离床次数与总时长数组
     * @param sleepInfo
     * @return
     */
    private SleepData leaveOnBed(List<SleepInfo> sleepInfo) {

        List<LeaveOnBedInfo> leaveOnBedList = new ArrayList<>();
        int flag = 0;
        LeaveOnBedInfo leaveOnBedInfo = new LeaveOnBedInfo();
        for (int i = 0; i < sleepInfo.size()-1; i++) {
            if (sleepInfo.get(i).getStatus() == Constants.SleepStatus.LeaveBed.getValue()){//离床状态
                if (flag == 0){//i时刻刚变为离床状态
                    leaveOnBedInfo.setLeaveOnBedStartTime(i);//离床开始
                    flag = 1;
                    if (sleepInfo.get(i+1).getStatus() - sleepInfo.get(i).getStatus() != 0){
                        leaveOnBedInfo.setLeaveOnBedEndTime(i);
                        leaveOnBedList.add(leaveOnBedInfo);
                        flag= 0;
                        leaveOnBedInfo = new LeaveOnBedInfo();
                    }
                }else {
                    if (sleepInfo.get(i+1).getStatus() - sleepInfo.get(i).getStatus() != 0 && flag != 0){
                        leaveOnBedInfo.setLeaveOnBedEndTime(i);
                        leaveOnBedList.add(leaveOnBedInfo);
                        flag= 0;
                        leaveOnBedInfo = new LeaveOnBedInfo();
                    }
                }
            }
        }
        if (leaveOnBedInfo.getLeaveOnBedStartTime() != 0){//如果最后序号的离床是不完整的，说明睡眠监测结束时一直是离床
            leaveOnBedInfo.setLeaveOnBedEndTime(sleepInfo.size());
            leaveOnBedList.add(leaveOnBedInfo);
        }
        //过滤极短的离床时间段（离床状态短于5秒，两次离床小于10秒）
        int leaveStart = 0;
        int leaveEnd = 0;
        if (leaveOnBedList.size() > 0){//有离床
            for (int i = 0; i < leaveOnBedList.size() - 1; i++) {
                if (leaveOnBedList.get(i).getLeaveOnBedEndTime() - leaveOnBedList.get(i).getLeaveOnBedStartTime() <= 5){
                    leaveOnBedList.remove(i);
                    i--;
                }else {
                    if (leaveOnBedList.get(i+1).getLeaveOnBedStartTime() - leaveOnBedList.get(i).getLeaveOnBedEndTime() <= 10){
                        leaveOnBedList.get(i).setLeaveOnBedEndTime(leaveOnBedList.get(i+1).getLeaveOnBedEndTime());
                        leaveOnBedList.remove(i+1);
                        i--;
                    }
                }
            }
            //验证最后一个离床段间距是否小于等于5，不满足，剔除
            if (leaveOnBedList.get(leaveOnBedList.size()-1).getLeaveOnBedEndTime() - leaveOnBedList.get(leaveOnBedList.size()-1).getLeaveOnBedStartTime() <= 5){
                leaveOnBedList.remove(leaveOnBedList.size()-1);
            }
            //刚开始监测的离床状态去除（不做为夜间起床）
            if (leaveOnBedList.get(0).getLeaveOnBedStartTime() == 0){
                leaveStart = 1;
            }else {
                leaveStart = 0;
            }
            if (leaveOnBedList.get(leaveOnBedList.size()-1).getLeaveOnBedEndTime() == leaveOnBedList.size()){
                leaveEnd = leaveOnBedList.size()-1;
            }else {
                leaveEnd = leaveOnBedList.size();
            }
        }
        SleepData sleepData = new SleepData();
        int leaveOnBedTime = 0;//离床时长
        for (int i = leaveStart; i < leaveOnBedList.size(); i++) {
            leaveOnBedTime += (leaveOnBedList.get(i).getLeaveOnBedEndTime() - leaveOnBedList.get(i).getLeaveOnBedStartTime() + 1);
        }
        sleepData.setLeaveDatas(leaveOnBedList);//离床数据
        sleepData.setOffBedAllTime(leaveOnBedTime);//离床总时长
        sleepData.setOffBedTime(leaveEnd - leaveStart);//离床次数
        return sleepData;
    }

    /**
     * 获取在床数据
     * @param leaveDatas
     * @param sleepInfoList
     * @return
     */
    private List<SleepData.OnBedData> getOnBedDataInfo(List<LeaveOnBedInfo> leaveDatas,List<SleepInfo> sleepInfoList) {
        List<SleepData.OnBedData> onBedDataList = new ArrayList<>();
        if (leaveDatas.size() == 0 || leaveDatas == null){//全程为睡眠段
            SleepData.OnBedData onBedData = new SleepData().new OnBedData();
            onBedData.setOnBedStartTime(1);
            onBedData.setOnBenEndTime(leaveDatas.size());
            for (int i = 1; i < 90; i++) {
                if (0.0 != sleepInfoList.get(i).getHr() && 0.0 != sleepInfoList.get(i).getRe()){
                    onBedData.setHrStartTime(i);
                }
                onBedDataList.add(onBedData);
            }
        }else {
            int start = 0;
            int j = 0;
            SleepData.OnBedData onBedData = new SleepData().new OnBedData();
            if (leaveDatas.get(0).getLeaveOnBedStartTime() == 0){//如果监测从离床状态开始
                onBedData.setOnBedStartTime(leaveDatas.get(0).getLeaveOnBedEndTime());
                start = 1;
            }else {
                onBedData.setOnBedStartTime(1);
                start = 0;
            }
            for (int i = start; i < leaveDatas.size(); i++) {
                onBedData.setOnBenEndTime(leaveDatas.get(i).getLeaveOnBedStartTime() - 1);
                onBedDataList.add(onBedData);
                j = onBedDataList.get(onBedDataList.size()-1).getOnBedStartTime();
                if ((j+ 90) < sleepInfoList.size()){
                    while (j < onBedDataList.get(onBedDataList.size()-1).getOnBedStartTime() + 90){
                        if (0.0 != sleepInfoList.get(j).getHr() && 0.0 != sleepInfoList.get(j).getRe() || j == sleepInfoList.size()){
                            onBedData.setHrStartTime(j);
                            j = onBedDataList.get(onBedDataList.size()-1).getOnBedStartTime()+90;
                            break;
                        }
                        j += 1;
                    }
                }
                onBedData = new SleepData().new OnBedData();
                onBedData.setOnBedStartTime(leaveDatas.get(i).getLeaveOnBedEndTime() + 1);
                if (i == leaveDatas.size() - 1){//获取最后一个睡眠段
                    onBedDataList.add(onBedData);
                    j = onBedDataList.get(onBedDataList.size()-1).getOnBedStartTime()-1;
                    if ((j+ 90) < sleepInfoList.size()) {
                        while (j < onBedDataList.get(onBedDataList.size() - 1).getOnBedStartTime() + 90) {
                            if (0.0 != sleepInfoList.get(j).getHr() && 0.0 != sleepInfoList.get(j).getRe() || j == sleepInfoList.size()) {
                                onBedData.setHrStartTime(j);
                                j = onBedDataList.get(onBedDataList.size() - 1).getOnBedStartTime() + 90;
                                break;
                            }
                            j += 1;
                        }
                    }
                }
            }
            if (onBedDataList.get(onBedDataList.size()-1).getOnBedStartTime() >= sleepInfoList.size()){
                onBedDataList.remove(onBedDataList.size()-1);
            }else {
                //设此睡眠段结束时刻为监测结束时刻
                onBedDataList.get(onBedDataList.size()-1).setOnBenEndTime(sleepInfoList.size());
            }
        }
        //如果末次睡眠段未找到非0心率与非0呼吸率时刻，将此时刻设为监测终止时刻（最后一段睡眠记录不完整时出现此情况）
        if (onBedDataList.get(onBedDataList.size()-1).getHrStartTime() == 0){
            onBedDataList.get(onBedDataList.size()-1).setHrStartTime(onBedDataList.get(onBedDataList.size()-1).getOnBenEndTime());
        }
        return onBedDataList;
    }

    /**
     * 临近插值
     * @param onBedDataList
     * @param sleepInfoList
     * @return
     */
    private List<SleepInfo> insertValue(List<SleepData.OnBedData> onBedDataList, List<SleepInfo> sleepInfoList) {
        for (int j = 0; j < onBedDataList.size(); j++) {//对各睡眠段数据的0数据进行插值处理
            //在首次出现非0心率非0呼吸率后面寻找其他0心率和0呼吸率数据，并插值替换0数据值
            for (int i = onBedDataList.get(j).getHrStartTime(); i < onBedDataList.get(j).getOnBenEndTime(); i++) {
                if (0.0 == sleepInfoList.get(i).getHr()) {
                    sleepInfoList.get(i).setHr(sleepInfoList.get(i).getHr());
                    System.out.println("第"+i+"时刻出现0心率，插值。");
                }
                if (0.0 == sleepInfoList.get(i).getRe()) {
                    sleepInfoList.get(i).setRe(sleepInfoList.get(i).getRe());
                }
            }
        }
        return sleepInfoList;
    }

    /**
     * 打鼾数据
     * @param sleepInfo
     * @return
     */
    private SleepData getSnoreData(List<SleepInfo> sleepInfo) {

        List<SnoreInfo> snoreInfoList = new ArrayList<>();
        int flag = 0;
        int snortTime = 0;//打鼾时长
        int snortTimes = 0;//打鼾次数
        SnoreInfo snoreInfo = new SnoreInfo();
        for (int i = 0; i < sleepInfo.size(); i++) {
            if (sleepInfo.get(i).getStatus() == Constants.SleepStatus.Snoring.getValue()){//判定为打鼾状态
                if (flag == 0){
                    snoreInfo.setSnoreStartTime(i);//打鼾开始
                    flag = 1;
                    if (sleepInfo.get(i+1).getStatus() - sleepInfo.get(i).getStatus() != 0){//下一时刻不为打鼾，打鼾仅一秒
                        snoreInfo.setSnoreEndTime(i);
                        snoreInfoList.add(snoreInfo);//一个打鼾段形成
                        flag= 0 ;
                        snoreInfo = new SnoreInfo();
                    }
                }else {
                    if (sleepInfo.get(i+1).getStatus() - sleepInfo.get(i).getStatus() != 0 && flag != 0){
                        snoreInfo.setSnoreEndTime(i);
                        snoreInfoList.add(snoreInfo);//一个打鼾段形成
                        flag= 0 ;
                        snoreInfo = new SnoreInfo();
                    }
                }
                //记录打鼾时长
                snortTime += 1;
                //记录打鼾次数
                snortTimes = snoreInfoList.size();
            }
        }
        SleepData sleepData = new SleepData();
        sleepData.setSnoreInfoList(snoreInfoList);
        sleepData.setSnoreAllTime(snortTime);
        sleepData.setSnoreAllTimes(snortTimes);
        return sleepData;
    }

    /**
     * 平滑滤波
     * @param list
     */
    public static List<SleepInfo> smooth(List<SleepInfo> list,int start,int end) {
        if (list.size() >= 5 && (start+2) < list.size()){
            list.get(start).setHr(list.get(start).getHr());
            list.get(start+1).setHr((list.get(start).getHr()+list.get(start+1).getHr()+list.get(start+2).getHr())/3);
            for (int i = start+2; i < end - 2; i++) {
                list.get(i).setHr((list.get(i-2).getHr()+list.get(i-1).getHr()+
                        list.get(i).getHr()+list.get(i+1).getHr()+list.get(i+2).getHr())/5);
            }
            list.get(end-2).setHr((list.get(end-3).getHr()+list.get(end-2).getHr()+list.get(end-12).getHr())/3);
            list.get(end-1).setHr(list.get(end-1).getHr());
        }
        return list;
    }

}
