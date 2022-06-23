package com.zxkkj.sleepAnalysis.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.zxkkj.sleepAnalysis.constants.Constants;
import com.zxkkj.sleepAnalysis.model.AnalysisReult;
import com.zxkkj.sleepAnalysis.model.SleepData;
import com.zxkkj.sleepAnalysis.model.SleepInfo;
import com.zxkkj.sleepAnalysis.service.IAnalysisService;
import com.zxkkj.sleepAnalysis.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalysisServiceImpl implements IAnalysisService {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public SleepData loadDataByLoaclFile(File localFile) {
        //解析csv文件
        CsvReadConfig csvConfig = new CsvReadConfig();
        List<CsvRow> rowList = CsvUtil.getReader(csvConfig).read(localFile).getRows();
        //离床数据
        //读取离床数据
        SleepData sleepData = new SleepData();
        sleepData = this.leaveOnBed(rowList);
        //原始数据
        sleepData.setRowList(rowList);
        return sleepData;
    }

    @Override
    public AnalysisReult executeAnalysis(SleepData sleepData) {
        //搜索睡眠分析段:得到各在床段数组sleep，信息包含：在床起始时刻、心率呼吸率产生时刻、在床结束时刻
        List<String[]> leaveDatas = sleepData.getLeaveDatas();
        List<CsvRow> rowList = sleepData.getRowList();
        int n = 0;//第1个睡眠段（离床将睡眠分为几个睡眠段）
        //String[] array = new String[3];//睡眠分析段：行号为睡眠段序号，第1列为在床起始时刻、第2列为心率呼吸产生时刻、第3列为在床结束时刻
        if ("0".equals(leaveDatas.get(0)[0])) {//如果无离床段
            String[] array = new String[3];
            //全程为睡眠段
            array[0] = "1";
            array[2] = String.valueOf(rowList.size());
            for (int i = 0; i < 90; i++) {//从开始监测的90秒内搜寻非0心率与非0呼吸率时刻(因为在床后60秒即可测出心率和呼吸率)
                if (!"0".equals(rowList.get(i).get(1)) && !"0".equals(rowList.get(i).get(2))) {
                    array[1] = String.valueOf(i);
                }
            }
            sleepData.getBedData().add(array);
        } else {
            String[] array = new String[3];
            int k1 = 0;
            if ("1".equals(leaveDatas.get(0)[0])) {//如果监测从离床状态开始
                array[0] = leaveDatas.get(0)[1];//以离床状态结束作为第一睡眠段的开始时刻
                k1 = 1;//从第2离床段开始搜寻睡眠段
            } else {
                array[0] = "1";//如果监测从在床状态开始，开始监测时刻为第一睡眠段的开始时刻
                k1 = 0;//从第1离床段开始搜索睡眠段
            }
            for (int i = k1; i < leaveDatas.size(); i++) {//从在床后第1次离床开始逐段确认
                sleepData.getBedData().add(array);
                array[2] = String.valueOf(Integer.valueOf(leaveDatas.get(i)[0]) - 1);//第n段睡眠结束段为第i次离床开始的前时刻
                int j = Integer.valueOf(sleepData.getBedData().get(n)[0]);
                while (j < Integer.valueOf(sleepData.getBedData().get(n)[0]) + 90) {//搜寻非0心率非0呼吸率起始时刻
                    if (!"0".equals(rowList.get(j).get(1)) && !"0".equals(rowList.get(j).get(2)) || j == rowList.size()) {
                        array[1] = String.valueOf(j);
                        j = sleepData.getBedData().size() + 90;//结束搜寻
                        break;
                    }
                    j += 1;
                }
                n += 1;
                array = new String[3];
                array[0] = String.valueOf(Integer.valueOf(leaveDatas.get(i)[1]) + 1);//新一段睡眠开始时刻为第i次离床结束的后时刻
            }
             if (Integer.valueOf(sleepData.getBedData().get(sleepData.getBedData().size() - 1)[0]) >= rowList.size()) {//如果最后一次睡眠段起始时刻超过监测终点时刻
                //删除最后一次睡眠段（说明最后记录为永久离床）
                sleepData.getBedData().remove(n - 1);
            } else {//最后一次睡眠起始时刻未超过监测终点，则以监测终点为该睡眠段终点，并从起始时刻搜寻非0数据起始位置
                sleepData.getBedData().get(n - 1)[2] = String.valueOf(rowList.size());
                int j = Integer.valueOf(sleepData.getBedData().get(n - 1)[0]);
                while (j < Integer.valueOf(sleepData.getBedData().get(n - 1)[0]) + 90) {//搜寻非0心率非0呼吸率起始时刻
                    if (!"0".equals(rowList.get(j).get(1)) && !"0".equals(rowList.get(j).get(2)) || j == rowList.size()) {
                        sleepData.getBedData().get(n - 1)[1] = String.valueOf(j);
                        j = Integer.valueOf(sleepData.getBedData().get(n - 1)[0]) + 90;//结束搜寻
                        break;
                    }
                    j += 1;
                }
            }
        }
        if ("0".equals(sleepData.getBedData().get(sleepData.getBedData().size() - 1)[1])) {//如果末次睡眠段未找到非0心率与非0呼吸率时刻，将此时刻设为监测终止时刻（最后一段睡眠记录不完整时出现此情况）
            sleepData.getBedData().get(sleepData.getBedData().size() - 1)[1] = sleepData.getBedData().get(sleepData.getBedData().size() - 1)[2];
        }
        //剔除各睡眠段偶发0数据并进行临近插值处理
        for (int j = 0; j < sleepData.getBedData().size(); j++) {//对各睡眠段数据的0数据进行插值处理
            //在首次出现非0心率非0呼吸率后面寻找其他0心率和0呼吸率数据，并插值替换0数据值
            for (int i = Integer.valueOf(sleepData.getBedData().get(j)[1]); i < Integer.valueOf(sleepData.getBedData().get(j)[2]); i++) {
                if ("0".equals(rowList.get(i).get(1))) {
                    CsvRow csvRow = rowList.get(i);
                    csvRow.set(1,rowList.get(i-1).get(1));
                }
                if ("0".equals(rowList.get(i).get(2))){
                    CsvRow csvRow = rowList.get(i);
                    csvRow.set(2,rowList.get(i-1).get(2));
                }
            }
        }
        //对非0心率0呼吸段的数据进行平滑滤波
        List<CsvRow> Data_smooth = rowList;
        for (int i = 0; i < sleepData.getBedData().size(); i++) {

            int start = Integer.valueOf(sleepData.getBedData().get(i)[1]);
            int end = Integer.valueOf(sleepData.getBedData().get(i)[2]);
            Data_smooth = this.smooth(Data_smooth,start,end);
        }
        //各睡眠段分期：对各睡眠段，进行睡眠分期，划分为觉醒期、浅睡眠期、深睡眠期、快速眼动睡眠期
        for (int i = 0; i < sleepData.getBedData().size(); i++) {
            int start = Integer.valueOf(sleepData.getBedData().get(i)[1]);
            int end = Integer.valueOf(sleepData.getBedData().get(i)[2]);

            String[] hr_max_min_i = new String[3];//各睡眠段心率最大、最小、平均
            hr_max_min_i[0] = Data_smooth.get(start).get(1);//假设第一个为最大值
            hr_max_min_i[1] = Data_smooth.get(start).get(1);//假设第一个为最小值
            hr_max_min_i[2] = "";
            double avaTemp = 0;
            for (int j = start; j < end-1; j++) {
                if (Integer.valueOf(Data_smooth.get(j).get(1)) > Integer.valueOf(hr_max_min_i[0])){//求心率最大值
                    hr_max_min_i[0] = Data_smooth.get(j).get(1);
                }
                if (Integer.valueOf(Data_smooth.get(j).get(1)) < Integer.valueOf(hr_max_min_i[1])){//求心率最大值
                    hr_max_min_i[1] = Data_smooth.get(j).get(1);
                }
                avaTemp += Double.valueOf(Data_smooth.get(j).get(1));//心率平均值
            }
            double avas = avaTemp/(end-start);
            hr_max_min_i[2] = String.format("%.1f",avas);
            sleepData.getHrMaxMin().add(hr_max_min_i);
        }

        String[] hr_max_min_all = new String[3];//全程心率最大、最小、平均
        hr_max_min_all[0] = sleepData.getHrMaxMin().get(0)[0];//假设第一个为最大心率
        hr_max_min_all[1] = sleepData.getHrMaxMin().get(0)[1];//假设第一个为最小心率
        double temp = 0.0;
        for (int j = 0; j < sleepData.getHrMaxMin().size(); j++) {
            if (Integer.valueOf(sleepData.getHrMaxMin().get(j)[0]) > Integer.valueOf(hr_max_min_all[0])){
                hr_max_min_all[0] = sleepData.getHrMaxMin().get(j)[0];
            }
            if (Integer.valueOf(sleepData.getHrMaxMin().get(j)[1]) < Integer.valueOf(hr_max_min_all[1])){
                hr_max_min_all[1] = sleepData.getHrMaxMin().get(j)[1];
            }
            temp += Double.valueOf(sleepData.getHrMaxMin().get(j)[2]);
        }
        double hrAll = temp/(sleepData.getHrMaxMin().size());
        hr_max_min_all[2] = String.format("%.1f",hrAll);
        sleepData.getHrMaxMinAll().add(hr_max_min_all);

        List<String[][]> sleepPhaseList = new ArrayList<>();
        //对各睡眠段进行睡眠分期
        for (int i = 0; i < sleepData.getBedData().size(); i++) {
            int start = Integer.valueOf(sleepData.getBedData().get(i)[1]);
            int end = Integer.valueOf(sleepData.getBedData().get(i)[1]) + 100;
            double a = 0.0;//取开始100秒内最大心率值
            double max = Double.valueOf(Data_smooth.get(start).get(1));//假定第一个为最大
            for (int j = start; j < end; j++) {
                if (Integer.valueOf(Data_smooth.get(j).get(1)) > max){
                    max = Double.valueOf(Data_smooth.get(j).get(1));
                }
            }
            a = max;
            int m = 1;
            //确定该睡眠段的第1期（觉醒期）的结束时刻
            int k = 0;
            if (i == 0){//如果是初次入睡段，起始心率高
                k = 0;
                List<Map<Integer,List<Map>>> listPhase = new ArrayList();//睡眠分期list
                Map mapPhase = new HashMap();
                Map mapSleepTime = new HashMap();//key：分期序号-醒，value：起始时刻-结束时刻
                for (int j = start; j < Integer.valueOf(sleepData.getBedData().get(i)[2]); j++) {

                    if (Double.valueOf(Data_smooth.get(j).get(1)) <= a*(1-0.088) && k==0){//计算首段入眠心率阈值

                        mapSleepTime.put(1,sleepData.getBedData().get(i)[0] + "-" + j);
                        mapPhase.put(m,mapSleepTime);

                        m += 1;k=1;
                    }
                }
                listPhase.add(mapPhase);
            }else {

            }
        }
        return null;
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
        int maxHr = CommonUtils.maxOrMinHr(sleepInfoList.subList(onBedData.getHrStartTime(), onBedData.getHrStartTime() + 100), Constants.Max)[0];

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
            SleepInfo sleepInfo = sleepInfoList.get(i);
            //判断是首次入睡 还是非首次入睡 心率阈值不一样
            boolean flag = first ? sleepInfo.getHr() <= firstSleepHrThreshold : sleepInfo.getHr() <= noFirstSleepHrThreshold;
            if (flag) {
                sleepStageList.add(new AnalysisReult.SleepStage(AnalysisReult.SleepStageType.WakeUP.getValue(), onBedData.getOnBedStartTime(), i));
                break;
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
        int startTime = onBedData.getOnBedStartTime() + 20 * 60;
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
            int type = k % 2 > 0 ? Constants.Min : Constants.Max;
            Integer[] res = CommonUtils.maxOrMinHr(sleepInfoList.subList(startTime, endCursor), type);
            startTime += res[1]; //指标向右移动
            result.add(new Integer[]{res[0], startTime});
        }
        return result;
    }

    @Override
    public void writeAnalysisResult(AnalysisReult analysisReult) {

    }

    /**
     * 搜寻离床时间段：得出各离床时间段数组off_bed、离床次数与总时长数组off_bed_sum
     *
     * @param rowList
     * @return
     */
    private SleepData leaveOnBed(List<CsvRow> rowList) {
        SleepData sleepData = new SleepData();
        List<String[]> list = new ArrayList<>();
        int n = 1;//第1个离床时间段
        int start = 0;//离床时间段起始时刻，0为未计时
        String leaveOn = "";
        for (int i = 0; i < rowList.size() - 1; i++) {
            String[] array = new String[2];//离床数据组：行号为离床顺序号，第1列为该次离床起始时间，第2列为该次离床结束时间（返回在床）
            leaveOn = rowList.get(i).get(0);//暂时取第一列为离床数据
            if (leaveOn.equals("1")) {//为离床状态
                if (start == 0) {//i时刻刚变为离床状态
                    array[0] = String.valueOf(i);
                    start = 1;
                    //如果下时刻状态值改变，则此次离床状态结束（仅1秒离床时才会发生此情况）
                    if (Integer.valueOf(rowList.get(i + 1).get(0)) - Integer.valueOf(rowList.get(i).get(0)) != 0) {
                        array[1] = String.valueOf(i);//记录此次离床结束时刻
                        start = 0;
                        n += 1;
                    }
                } else {
                    if (Integer.valueOf(rowList.get(i + 1).get(0)) - Integer.valueOf(rowList.get(i).get(0)) != 0 && start != 0) {
                        array[1] = String.valueOf(i);
                        start = 0;
                        n += 1;
                    }
                }
                if (array[0] != null || array[1] != null) {//过滤array为null的数组
                    list.add(array);
                }
            }
            sleepData.setLeaveDatas(list);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.size() > i + 1) {
                if (list.get(i)[1] == null && list.get(i + 1)[0] == null) {
                    list.get(i)[1] = list.get(i + 1)[1];
                    list.remove(i + 1);
                    i--;
                }
            }
        }
        if (n == 1) {//如果n为1，即没有离床段
            //删除首行后面的所有行，首行数据为0 0
            String[] temp = new String[]{"0", "0"};
            List<String[]> listTemp = new ArrayList<>();
            listTemp.add(temp);
            sleepData.setLeaveDatas(listTemp);
        } else {
            if (null != list.get(n - 1)[0]) {//如果最后的第n次为离床
                if (null == list.get(n - 1)[1]) {//如果最后序号的离床是不完整的，说明睡眠监测结束时一直是离床
                    list.get(n - 1)[1] = String.valueOf(rowList.size());//将此次离床结束时间设为睡眠监测结束时刻
                    for (int i = n; i < list.size(); i++) {//删除n+1行后无记录的数据项
                        list.remove(i + 1);
                        i--;
                    }
                }
            } else {//如果最后的第n次不为离床
                for (int i = n; i < list.size(); i++) {//删除n行后无记录的数据项
                    list.remove(i);
                    i--;
                }
            }
        }
        //删除极短的离床时间段，并计算离床次数及总离床时长
        int off_bed_start;
        int off_bed_end;
        if (null != list.get(0)[0]) {
            int i = 0;
            while (i < list.size()) {
                if (Integer.valueOf(list.get(i)[1]) - Integer.valueOf(list.get(i)[0]) <= 5) {
                    list.remove(i);
                    i--;
                } else {
                    if (i < list.size() - 1) {
                        if (Integer.valueOf(list.get(i + 1)[0]) - Integer.valueOf(list.get(i)[1]) <= 10) {
                            list.get(i)[1] = list.get(i + 1)[1];
                            list.remove(i + 1);
                            i--;
                        } else {
                            i += 1;
                        }
                    } else {
                        break;
                    }
                }
            }
            //刚开始监测的离床状态去除（不做为夜间起床）
            if ("1".equals(list.get(0)[0])) {
                off_bed_start = 2;
            } else {
                off_bed_start = 1;
            }
            //结束监测的离床状态去除（不做为夜间起床）
            if (list.get(list.size() - 1)[1].equals(String.valueOf(rowList.size()))) {
                off_bed_end = list.size() - 1;
            } else {
                off_bed_end = list.size();
            }
            int offBedTime = off_bed_end - off_bed_start + 1;//离床次数
            int offBedAllTime = 0;//离床总时长
            for (int j = off_bed_start; j <= off_bed_end; j++) {
                offBedAllTime += Integer.valueOf(list.get(j - 1)[1]) - Integer.valueOf(list.get(j - 1)[0]) + 1;
            }
            sleepData.setOffBedTime(offBedTime);
            sleepData.setOffBedAllTime(offBedAllTime);
        }
        return sleepData;
    }

    /**
     * 平滑滤波
     * @param list
     */
    public static List<CsvRow> smooth(List<CsvRow> list,int start,int end) {
        if (list.size() >= 5){
            list.get(start).set(1,list.get(start).get(1));
            list.get(start+1).set(1,String.valueOf((Integer.valueOf(list.get(start).get(1))+
                    Integer.valueOf(list.get(start+1).get(1))+
                    Integer.valueOf(list.get(start+2).get(1)))/3));
            for (int i = start+2; i < end - 2; i++) {
                CsvRow csvRow = (CsvRow) list.get(i);
                csvRow.set(1,String.valueOf((Integer.valueOf(list.get(i-2).get(1))+
                        Integer.valueOf(list.get(i-1).get(1))+
                        Integer.valueOf(list.get(i).get(1))+
                        Integer.valueOf(list.get(i+1).get(1))+
                        Integer.valueOf(list.get(i+2).get(1))
                        )/5));
            }
            list.get(end-2).set(1,String.valueOf((Integer.valueOf(list.get(end-3).get(1))+
                    Integer.valueOf(list.get(end-2).get(1))+
                    Integer.valueOf(list.get(end-1).get(1)))/3));
            list.get(end-1).set(1,String.valueOf(Integer.valueOf(list.get(end-1).get(1))));
        }
        return list;
    }

}
