package com.zxkkj.sleepAnalysis.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.text.csv.*;
import com.zxkkj.sleepAnalysis.constants.Constants;
import com.zxkkj.sleepAnalysis.model.*;
import com.zxkkj.sleepAnalysis.service.IAnalysisService;
import com.zxkkj.sleepAnalysis.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AnalysisServiceImpl implements IAnalysisService {

    public Logger logger = LoggerFactory.getLogger(getClass());

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
            sleepInfo.setBo(Double.valueOf(rowList.get(i).get(1)));
            sleepInfo.setStatus(Integer.parseInt(rowList.get(i).get(2)));//胸部监测带状态
            sleepInfo.setHr(Double.valueOf(rowList.get(i).get(3)));//心率
            sleepInfo.setRe(Double.valueOf(rowList.get(i).get(4)));//呼吸
            list.add(sleepInfo);
        }
        return list;
    }

    @Override
    public AnalysisReult executeAnalysis(SleepData sleepData) {

        List<SleepInfo> sleepInfoList = sleepData.getSleepInfoList();

        //离床数据分析
        SleepData leaveData = this.leaveOnBed(sleepInfoList);
        sleepData.setLeaveDatas(leaveData.getLeaveDatas());
        sleepData.setOffBedAllTime(leaveData.getOffBedAllTime());
        sleepData.setOffBedTime(leaveData.getOffBedTime());

        //打鼾数据分析
        SleepData sonreData = this.getSnoreData(sleepInfoList);
        sleepData.setSnoreInfoList(sonreData.getSnoreInfoList());
        sleepData.setSnoreAllTimes(sonreData.getSnoreAllTimes());
        sleepData.setSnoreAllTime(sonreData.getSnoreAllTime());

        //弱呼吸数据分析
        SleepData shallowBreath = this.getShallowBreath(sleepInfoList);
        sleepData.setShallowBreathInfoList(shallowBreath.getShallowBreathInfoList());
        sleepData.setShallowBreathTimes(shallowBreath.getShallowBreathTimes());
        sleepData.setShallowBreathTime(shallowBreath.getShallowBreathTime());

        //在床数据分析
        List<LeaveOnBedInfo> leaveDatas = sleepData.getLeaveDatas();
        List<SleepData.OnBedData> onBedDataList = this.getOnBedDataInfo(leaveDatas,sleepInfoList);
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

        //结果汇总
        AnalysisReult analysisReultEnd = new AnalysisReult();

        //全程心率、呼吸率、血氧饱和度对应最大、最小、平均值
        analysisReultEnd = this.sleepPhaseHrReBoDate(Data_smooth,onBedDataList);
        //睡眠分期
        List<AnalysisReult.SleepStage> analysisReult = new ArrayList<>();
        //睡眠分期里增加离床时刻
        List<AnalysisReult.SleepStage> onLeaveBed = new ArrayList<>();
        for (int i = 0; i < sleepData.getLeaveDatas().size(); i++) {
            AnalysisReult.SleepStage sleepStage = new AnalysisReult.SleepStage();
            sleepStage.setType(0);
            sleepStage.setStartTime(sleepData.getLeaveDatas().get(i).getLeaveOnBedStartTime());
            sleepStage.setEndTime(sleepData.getLeaveDatas().get(i).getLeaveOnBedEndTime());
            onLeaveBed.add(sleepStage);
        }

        //将开始监测时刻为离床状态的事件拼到睡眠事件中
        if (onLeaveBed.size() > 0 && onLeaveBed.get(0).getStartTime() == 0){
            analysisReult.add(new AnalysisReult.SleepStage(0,onLeaveBed.get(0).getStartTime(),onLeaveBed.get(0).getEndTime()));
        }

        int onBedTotalTime = 0;//在床总时长
        for (int i = 0; i < onBedDataList.size(); i++) {
            if (onBedDataList.get(i).getHrStartTime() < Data_smooth.size() - 100){
                if (i == 0){
                    analysisReult.addAll(sleepStageSplit(Data_smooth,onBedDataList.get(i),true,analysisReultEnd.getHrStatInfoList().get(0), onLeaveBed));
                }else {
                    analysisReult.addAll(sleepStageSplit(Data_smooth,onBedDataList.get(i),false,analysisReultEnd.getHrStatInfoList().get(0),onLeaveBed));
                }
            }
            //在床总时长
            onBedTotalTime += (onBedDataList.get(i).getOnBenEndTime() - onBedDataList.get(i).getOnBedStartTime());
        }
        if (onBedTotalTime < 5){
            onBedTotalTime = 0;
        }
        if (analysisReult.size() > 0){
            analysisReult.get(0).setStartTime(1);
        }
        int sleepTotalTime = 0;//睡眠总时长
        int wakeUpTotalTime = 0;//觉醒总时长
        int shallowSleepTotalTime = 0;//浅睡眠总时长
        int deepSleepTotalTime = 0;//深睡眠总时长
        int remSleepTotalTime = 0;//REM总时长
        for (int i = 0; i < analysisReult.size(); i++) {
            if (analysisReult.get(i).getType() != 0){
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
                if (analysisReult.get(i).getType() == AnalysisReult.SleepStageType.WakeUP.getValue()){
                    wakeUpTotalTime += (analysisReult.get(i).getEndTime() - analysisReult.get(i).getStartTime());
                }
            }
        }
        sleepTotalTime = sleepTotalTime - wakeUpTotalTime;

        //睡眠分期统计数据（时长单位为分钟）
        analysisReultEnd.setMonitorTotalTime(sleepInfoList.size());//监测总时长(分)
        //analysisReultEnd.setOnBedTotalTime((sleepInfoList.size() - sleepData.getOffBedAllTime()));//在床总时长(分)
        analysisReultEnd.setOnBedTotalTime(onBedTotalTime);//在床总时长(分)
        analysisReultEnd.setSleepTotalTime(sleepTotalTime);//睡眠总时长
        analysisReultEnd.setShallowSleepTotalTime(shallowSleepTotalTime);//浅睡眠总时长
        if (sleepTotalTime > 0){
            analysisReultEnd.setShallowSleepRatio(CommonUtils.twoDecimalI(shallowSleepTotalTime,sleepTotalTime)*100);//浅睡眠比例
            analysisReultEnd.setDeepSleepRatio(CommonUtils.twoDecimalI(deepSleepTotalTime,sleepTotalTime)*100);//深睡眠比例
            analysisReultEnd.setRemSleepRatio(CommonUtils.twoDecimalI(remSleepTotalTime,sleepTotalTime)*100);
        }else {
            analysisReultEnd.setShallowSleepRatio(0.0);
            analysisReultEnd.setDeepSleepRatio(0.0);
            analysisReultEnd.setRemSleepRatio(0.0);
        }
        analysisReultEnd.setDeepSleepTotalTime(deepSleepTotalTime);//深睡眠总时长
        analysisReultEnd.setRemSleepTotalTime(remSleepTotalTime);//rem总时长
        analysisReultEnd.setLeaveBedTimes(sleepData.getOffBedTime());//离床次数
        analysisReultEnd.setLeaveBedTotalTime(sleepData.getOffBedAllTime());//离床总时间
        analysisReultEnd.setSleepSplitNum(onBedDataList.size());//睡眠分段数量
        analysisReultEnd.setAnalysisReult(analysisReult);//睡眠分期
        //呼吸事件统计数据（时长为秒）
        analysisReultEnd.setShallowBreathTimes(sleepData.getShallowBreathTimes());//弱呼吸次数
        analysisReultEnd.setShallowBreathTime(sleepData.getShallowBreathTime());//弱呼吸总时长
        analysisReultEnd.setSnoreAllTimes(sleepData.getSnoreAllTimes());//打鼾次数
        analysisReultEnd.setSnoreAllTime(sleepData.getSnoreAllTime());//打鼾总时长

        //呼吸阻塞
        if (analysisReultEnd.getSleepTotalTime() > 0){
            this.judgeSleepApnea(analysisReultEnd);
        }else {
            analysisReultEnd.setSleepApnea(0);
        }
        //睡眠总体评价(根据深睡眠比例：大于30% 好、15% - 30% 较好、小于15%)
        if (analysisReultEnd.getDeepSleepRatio() >= 30.0){
            analysisReultEnd.setOverallEvaluationOfSleep(2);
        }else if (analysisReultEnd.getDeepSleepRatio() < 30.0 && analysisReultEnd.getDeepSleepRatio() >= 15.0){
            analysisReultEnd.setOverallEvaluationOfSleep(1);
        }else {
            analysisReultEnd.setOverallEvaluationOfSleep(0);
        }
        //@20230410 add 如果最后一个分期的结束时刻不等于检测总时长，将该值置为检测总时长
        if (analysisReultEnd.getAnalysisReult().size() > 0 && analysisReultEnd.getAnalysisReult().get(analysisReultEnd.getAnalysisReult().size() - 1).getEndTime() != analysisReultEnd.getMonitorTotalTime()) {
            analysisReultEnd.getAnalysisReult().get(analysisReultEnd.getAnalysisReult().size() - 1).setEndTime(analysisReultEnd.getMonitorTotalTime());
        }
        return analysisReultEnd;
    }

    /**
     * 判断呼吸阻塞
     * @param analysisReultEnd
     */
    private AnalysisReult judgeSleepApnea(AnalysisReult analysisReultEnd) {
        /*
         * 判断呼吸阻塞因子
         */
        //全夜血氧饱和度平均值
        double allNightBoAvg = analysisReultEnd.getBoStatInfoList().get(0).getAvg();
        //全夜血氧饱和度最小值
        double allNightBoMin = analysisReultEnd.getBoStatInfoList().get(0).getMin();
        //低通气次数
        int shallowBrTimes = analysisReultEnd.getShallowBreathTimes();
        //低通气时长(分)
        double shallowBrTime = CommonUtils.twoDecimalI(analysisReultEnd.getShallowBreathTime(),60);
        //全夜睡眠总时长（小时）
        double sleepTotalTime = CommonUtils.twoDecimalD(analysisReultEnd.getSleepTotalTime()/60);
        //全夜睡眠期间平均每小时低通气次数
        double avgHourShallowBrTimes = CommonUtils.twoDecimalD(shallowBrTimes / sleepTotalTime);
        //（全夜血氧饱和度平均值-全夜血氧饱和度最小值）/全夜血氧饱和度平均值
        if (allNightBoAvg != 0.0){
            double bo = CommonUtils.twoDecimalD((allNightBoAvg - allNightBoMin) / allNightBoAvg);
            if (allNightBoMin < 0.85 || bo > 0.1 || avgHourShallowBrTimes > 20.0){
                analysisReultEnd.setSleepApnea(2);//呼吸暂停：明显
            }else if (bo > 0.04 || avgHourShallowBrTimes > 5 || shallowBrTime > 2.0){
                analysisReultEnd.setSleepApnea(1);//呼吸暂停：有
            }else {
                analysisReultEnd.setSleepApnea(0);//呼吸暂停：无
            }
        }else {//如果全夜血氧饱和度均值为0，即可判定无血氧数据，判断呼吸暂停方法剔除血氧因子
            if (avgHourShallowBrTimes > 20.0){
                analysisReultEnd.setSleepApnea(2);//呼吸暂停：明显
            }else if (avgHourShallowBrTimes > 5 || shallowBrTime > 2.0){
                analysisReultEnd.setSleepApnea(1);//呼吸暂停：有
            }else {
                analysisReultEnd.setSleepApnea(0);//呼吸暂停：无
            }
        }
        return analysisReultEnd;
    }

    @Override
    public void writeAnalysisResult(AnalysisReult analysisReult,String outTxtPath,File file) {
        //分析结果写入
        File fileCurrent = new File(outTxtPath + file.getName().substring(0,file.getName().lastIndexOf(".")) + ".txt");
        FileWriter fw = null;
        //睡眠分期结果写入csv文件
        CsvWriter writer = null;
        try {
            //文件不存在才创建
            if (!fileCurrent.exists()){
                fileCurrent.createNewFile();
            }
            fw = new FileWriter(fileCurrent.getPath());
            //生理参数统计数据
            fw.write(analysisReult.getHrStatInfoList().get(0).getMax() + "" + " ");
            fw.write(analysisReult.getHrStatInfoList().get(0).getMin() + "" + " ");
            fw.write(analysisReult.getHrStatInfoList().get(0).getAvg() + "" + " ");
            fw.write(analysisReult.getReStatInfoList().get(0).getMax() + "" + " ");
            fw.write(analysisReult.getReStatInfoList().get(0).getMin() + "" + " ");
            fw.write(analysisReult.getReStatInfoList().get(0).getAvg() + "" + " ");
            fw.write(analysisReult.getBoStatInfoList().get(0).getMax() + "" + " ");
            fw.write(analysisReult.getBoStatInfoList().get(0).getMin() + "" + " ");
            fw.write(analysisReult.getBoStatInfoList().get(0).getAvg() + "" + " ");
            //睡眠分期统计数据（时长单位为分钟）
            fw.write(analysisReult.getMonitorTotalTime() + "" + " ");
            fw.write(analysisReult.getOnBedTotalTime() + "" + " ");
            fw.write(analysisReult.getSleepTotalTime() + "" + " ");
            fw.write(analysisReult.getShallowSleepTotalTime() + "" + " ");
            fw.write(analysisReult.getShallowSleepRatio() + "" + " ");
            fw.write(analysisReult.getDeepSleepTotalTime() + "" + " ");
            fw.write(analysisReult.getDeepSleepRatio() + "" + " ");
            fw.write(analysisReult.getRemSleepTotalTime() + "" + " ");
            fw.write(analysisReult.getRemSleepRatio() + "" + " ");
            fw.write(analysisReult.getLeaveBedTimes() + "" + " ");
            fw.write(analysisReult.getLeaveBedTotalTime() + "" + " ");
            fw.write(analysisReult.getSleepSplitNum() + "" + " ");
            //呼吸事件统计数据（时长为秒）
            fw.write(analysisReult.getShallowBreathTimes() + "" + " ");
            fw.write(analysisReult.getShallowBreathTime()+ "" + " ");
            fw.write(analysisReult.getSnoreAllTimes() + "" + " ");
            fw.write(analysisReult.getSnoreAllTime() + "" + " ");
            fw.write(analysisReult.getSleepApnea() + "" + " ");
            //睡眠总体评价
            fw.write(analysisReult.getOverallEvaluationOfSleep() + "" + " ");

            FileOutputStream fileOutputStream = new FileOutputStream(outTxtPath + file.getName().substring(0,file.getName().lastIndexOf(".")) + ".csv");
            fileOutputStream.write(0xef);
            fileOutputStream.write(0xbb);
            fileOutputStream.write(0xbf);
            List<Integer[]> list = new ArrayList<>();
            for (int i = 0; i < analysisReult.getAnalysisReult().size(); i++) {
                AnalysisReult.SleepStage sleepStage = new AnalysisReult.SleepStage();
                sleepStage = analysisReult.getAnalysisReult().get(i);
                list.add(new Integer[]{sleepStage.getType(),sleepStage.getStartTime(),sleepStage.getEndTime()});
            }
            if (list.size() == 0 && list != null) {
                list.add(new Integer[]{0,0,analysisReult.getOnBedTotalTime()});
            }
            writer = new CsvWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8.name()));
            writer.write(list);
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
        logger.info(
                "\n"+"全程最大心率:"+analysisReult.getHrStatInfoList().get(0).getMax()
                        +"\n"+"全程最小心率:"+analysisReult.getHrStatInfoList().get(0).getMin()
                        +"\n"+"全程平均心率:"+analysisReult.getHrStatInfoList().get(0).getAvg()
                        +"\n"+"全程最大呼吸率:"+analysisReult.getReStatInfoList().get(0).getMax()
                        +"\n"+"全程最小呼吸率:"+analysisReult.getReStatInfoList().get(0).getMin()
                        +"\n"+"全程平均呼吸率:"+analysisReult.getReStatInfoList().get(0).getAvg()
                        +"\n"+"全程最大血氧饱和度:"+analysisReult.getBoStatInfoList().get(0).getMax()
                        +"\n"+"全程最小血氧饱和度:"+analysisReult.getBoStatInfoList().get(0).getMin()
                        +"\n"+"全程平均血氧饱和度:"+analysisReult.getBoStatInfoList().get(0).getAvg()

                        +"\n"+"监测总时长:"+analysisReult.getMonitorTotalTime()
                        +"\n"+"在床总时长:"+analysisReult.getOnBedTotalTime()
                        +"\n"+"睡眠总时长:"+analysisReult.getSleepTotalTime()
                        +"\n"+"浅睡眠总时长:"+analysisReult.getShallowSleepTotalTime()
                        +"\n"+"浅睡眠比例:"+analysisReult.getShallowSleepRatio()
                        +"\n"+"深睡眠总时长:"+analysisReult.getDeepSleepTotalTime()
                        +"\n"+"深睡眠比例:"+analysisReult.getDeepSleepRatio()
                        +"\n"+"rem总时长:"+analysisReult.getRemSleepTotalTime()
                        +"\n"+"rem比例:"+analysisReult.getRemSleepRatio()
                        +"\n"+"离床次数:"+analysisReult.getLeaveBedTimes()
                        +"\n"+"离床总时间:"+analysisReult.getLeaveBedTotalTime()
                        +"\n"+"睡眠分段数量:"+analysisReult.getSleepSplitNum()

                        +"\n"+"低通气次数:"+analysisReult.getShallowBreathTimes()
                        +"\n"+"低通气总时长:"+analysisReult.getShallowBreathTime()
                        +"\n"+"打鼾次数:"+analysisReult.getSnoreAllTimes()
                        +"\n"+"打鼾总时长:"+analysisReult.getSnoreAllTime()
                        +"\n"+"呼吸阻塞:"+analysisReult.getSleepApnea()

                        +"\n"+"睡眠总体评价:"+analysisReult.getOverallEvaluationOfSleep()
        );
    }

    /**
     *
     * @param file 校验文件
     * @return
     */
    @Override
    public boolean isCalculated(File file) {
        //先创建以当前日期命名的文件
        File fileCurrent = this.createNewFile();
        //校验源数据文件名是否存在创建的文本中
        String fileCurrentPath = fileCurrent.getPath();
        List<String> list = new ArrayList<String>();
        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            fis = new FileInputStream(fileCurrentPath);
            isr = new InputStreamReader(fis,"UTF-8");
            br = new BufferedReader(isr);
            String line = "";
            //逐行读取并存入list
            while ((line = br.readLine()) != null){
                list.add(line);
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                fis.close();
                isr.close();
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String writeContent = file.getPath();
        for (int i = 0; i < list.size(); i++) {
            if (writeContent.equals(list.get(i))){
                logger.info("该数据已进行过校验！");
                return false;
            }
        }
        return true;
    }

    private File createNewFile() {

        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        String date = format.format(new Date());
        String fileNameByCurrentTime = date + ".txt";
        File fileCurrent = new File(fileNameByCurrentTime);
        try {
            //文件不存在才创建
            if (!fileCurrent.exists()){
                fileCurrent.createNewFile();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return fileCurrent;
    }

    /**
     * 将计算的文件名及上级目录写入文本
     * @param file
     */
    @Override
    public void writeSourceTxtName(File file) {

        String writeContent = file.getPath();
        File fileCurrent = this.createNewFile();
        FileWriter fw = null;
        try {
            fw = new FileWriter(fileCurrent.getPath(),true);
            fw.write(writeContent + "\r\n");
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 全程心率、呼吸率、血氧饱和度最大，最小，平均值计算
     * @param Data_smooth
     * @param onBedDataList
     * @return
     */
    private AnalysisReult sleepPhaseHrReBoDate(List<SleepInfo> Data_smooth,List<SleepData.OnBedData> onBedDataList){

        //心率
        List<AnalysisReult.HrStatInfo> hrStatInfoList = new ArrayList<>();
        //呼吸率
        List<AnalysisReult.ReStatInfo> reStatInfoList = new ArrayList<>();
        //血氧饱和度
        List<AnalysisReult.BoStatInfo> boStatInfoList = new ArrayList<>();
        if (onBedDataList.size() <= 0) {
            AnalysisReult.BoStatInfo boStatInfo = new AnalysisReult.BoStatInfo();
            List<Double> boList = new ArrayList<>();
            for (int i = 0; i < Data_smooth.size(); i++) {
                if (Data_smooth.get(i).getBo() != 0.0){
                    boList.add(Data_smooth.get(i).getBo());
                }
            }
            //各睡眠段血氧最大、最小、平均
            double phaseBo_max = Math.round(boList.get(0));
            double phaseBo_min = Math.round(boList.get(0));
            double phaseBo_avg = 0.0;
            double avaTempBo = 0;
            for (int i = 1; i < boList.size(); i++) {
                if (Math.round(boList.get(i)) > phaseBo_max){
                    phaseBo_max = Math.round(boList.get(i));
                }
                if (Math.round(Data_smooth.get(i).getBo()) < phaseBo_min){
                    phaseBo_min = Math.round(boList.get(i));
                }
                avaTempBo += Math.round(boList.get(i));
            }
            phaseBo_avg = avaTempBo/boList.size();
            boStatInfo.setMax(phaseBo_max);
            boStatInfo.setMin(phaseBo_min);
            boStatInfo.setAvg(Math.round(phaseBo_avg));
            boStatInfoList.add(boStatInfo);
        }else {
            for (int i = 0; i < onBedDataList.size(); i++) {

                AnalysisReult.HrStatInfo hrStatInfo = new AnalysisReult.HrStatInfo();
                AnalysisReult.ReStatInfo reStatInfo = new AnalysisReult.ReStatInfo();
                AnalysisReult.BoStatInfo boStatInfo = new AnalysisReult.BoStatInfo();

                //以心率产生时刻为开始时刻
                int start = onBedDataList.get(i).getHrStartTime();
                int end = onBedDataList.get(i).getOnBenEndTime();
                if (start < end){

                    //各睡眠段心率最大、最小、平均
                    double phaseHr_max = Math.round(Data_smooth.get(start).getHr());//假设第一个为最大值
                    double phaseHr_min = Math.round(Data_smooth.get(start).getHr());//假设第一个为最小值
                    double phaseHr_avg = 0.0;
                    double avaTempHr = 0;

                    //各睡眠段呼吸率最大、最小、平均
                    double phaseRe_max = Math.round(Data_smooth.get(start).getRe());
                    double phaseRe_min = Math.round(Data_smooth.get(start).getRe());
                    double phaseRe_avg = 0.0;
                    double avaTempRe = 0;

                    //各睡眠段血氧最大、最小、平均
                    double phaseBo_max = Math.round(Data_smooth.get(start).getBo());
                    double phaseBo_min = Math.round(Data_smooth.get(start).getBo());
                    double phaseBo_avg = 0.0;
                    double avaTempBo = 0;

                    for (int j = start; j < end; j++) {

                        if (Math.round(Data_smooth.get(j).getHr()) > phaseHr_max){//求最大值
                            phaseHr_max = Math.round(Data_smooth.get(j).getHr());
                        }
                        if (Math.round(Data_smooth.get(j).getHr()) < phaseHr_min){//求最小值
                            phaseHr_min = Math.round(Data_smooth.get(j).getHr());
                        }
                        avaTempHr += Math.round(Data_smooth.get(j).getHr());//平均值

                        if (Math.round(Data_smooth.get(j).getRe()) > phaseRe_max){
                            phaseRe_max = Math.round(Data_smooth.get(j).getRe());
                        }
                        if (Math.round(Data_smooth.get(j).getRe()) < phaseRe_min){
                            phaseRe_min = Math.round(Data_smooth.get(j).getRe());
                        }
                        avaTempRe += Math.round(Data_smooth.get(j).getRe());

                        if (Math.round(Data_smooth.get(j).getBo()) > phaseBo_max){
                            phaseBo_max = Math.round(Data_smooth.get(j).getBo());
                        }
                        if (Math.round(Data_smooth.get(j).getBo()) < phaseBo_min){
                            phaseBo_min = Math.round(Data_smooth.get(j).getBo());
                        }
                        avaTempBo += Math.round(Data_smooth.get(j).getBo());
                    }

                    phaseHr_avg = avaTempHr/(end-start);
                    hrStatInfo.setMax(phaseHr_max);
                    hrStatInfo.setMin(phaseHr_min);
                    hrStatInfo.setAvg(Math.round(phaseHr_avg));
                    hrStatInfoList.add(hrStatInfo);

                    phaseRe_avg = avaTempRe/(end-start);
                    reStatInfo.setMax(phaseRe_max);
                    reStatInfo.setMin(phaseRe_min);
                    reStatInfo.setAvg(Math.round(phaseRe_avg));
                    reStatInfoList.add(reStatInfo);

                    phaseBo_avg = avaTempBo/(end-start);
                    boStatInfo.setMax(phaseBo_max);
                    boStatInfo.setMin(phaseBo_min);
                    boStatInfo.setAvg(Math.round(phaseBo_avg));
                    boStatInfoList.add(boStatInfo);
                }
            }
        }
        //全程最大、最小、平均值计算start
        AnalysisReult.HrStatInfo hrStatInfo = new AnalysisReult.HrStatInfo();
        AnalysisReult.ReStatInfo reStatInfo = new AnalysisReult.ReStatInfo();
        AnalysisReult.BoStatInfo boStatInfo = new AnalysisReult.BoStatInfo();
        if (hrStatInfoList.size() > 0){
            double hr_max_all = hrStatInfoList.get(0).getMax();
            double hr_min_all = hrStatInfoList.get(0).getMin();
            double hr_avg_all = 0.0;
            double tempHr = 0.0;
            for (int i = 0; i < hrStatInfoList.size(); i++) {
                if (hrStatInfoList.get(i).getMax() > hr_max_all){
                    hr_max_all = hrStatInfoList.get(i).getMax();
                }
                if (hrStatInfoList.get(i).getMin() < hr_min_all){
                    hr_min_all = hrStatInfoList.get(i).getMin();
                }
                tempHr += hrStatInfoList.get(i).getAvg();
            }
            hr_avg_all = tempHr/hrStatInfoList.size();
            hrStatInfo.setMax(Math.round(hr_max_all));
            hrStatInfo.setMin(Math.round(hr_min_all));
            hrStatInfo.setAvg(Math.round(hr_avg_all));
        }else {
            hrStatInfo.setMax(0.0);
            hrStatInfo.setMin(0.0);
            hrStatInfo.setAvg(0.0);
        }

        if (reStatInfoList.size() > 0){
            double re_max_all = reStatInfoList.get(0).getMax();
            double re_min_all = reStatInfoList.get(0).getMin();
            double re_avg_all = 0.0;
            double tempRe = 0.0;
            for (int i = 0; i < reStatInfoList.size(); i++) {
                if (reStatInfoList.get(i).getMax() > re_max_all){
                    re_max_all = reStatInfoList.get(i).getMax();
                }
                if (reStatInfoList.get(i).getMin() < re_min_all){
                    re_min_all = reStatInfoList.get(i).getMin();
                }
                tempRe += reStatInfoList.get(i).getAvg();
            }
            re_avg_all = tempRe/reStatInfoList.size();
            reStatInfo.setMax(Math.round(re_max_all));
            reStatInfo.setMin(Math.round(re_min_all));
            reStatInfo.setAvg(Math.round(re_avg_all));
        }else {
            reStatInfo.setMax(0.0);
            reStatInfo.setMin(0.0);
            reStatInfo.setAvg(0.0);
        }

        if (boStatInfoList.size() > 0){
            double bo_max_all = boStatInfoList.get(0).getMax();
            double bo_min_all = boStatInfoList.get(0).getMin();
            double bo_avg_all = 0.0;
            double tempBo = 0.0;
            for (int i = 0; i < boStatInfoList.size(); i++) {
                if (boStatInfoList.get(i).getMax() > bo_max_all){
                    bo_max_all = boStatInfoList.get(i).getMax();
                }
                if (boStatInfoList.get(i).getMin() < bo_min_all){
                    bo_min_all = boStatInfoList.get(i).getMin();
                }
                tempBo += boStatInfoList.get(i).getAvg();
            }
            bo_avg_all = tempBo/boStatInfoList.size();
            boStatInfo.setMax(Math.round(bo_max_all));
            boStatInfo.setMin(Math.round(bo_min_all));
            boStatInfo.setAvg(Math.round(bo_avg_all));
        }else {
            boStatInfo.setMax(0.0);
            boStatInfo.setMin(0.0);
            boStatInfo.setAvg(0.0);
        }

        AnalysisReult analysisReult = new AnalysisReult();
        List<AnalysisReult.HrStatInfo> hrStatInfoList1 = new ArrayList<>();//全程最大、最小、平均心率值
        hrStatInfoList1.add(hrStatInfo);
        analysisReult.setHrStatInfoList(hrStatInfoList1);
        List<AnalysisReult.ReStatInfo> reStatInfoList1 = new ArrayList<>();//全程最大、最小、平均呼吸率值
        reStatInfoList1.add(reStatInfo);
        analysisReult.setReStatInfoList(reStatInfoList1);
        List<AnalysisReult.BoStatInfo> boStatInfoList1 = new ArrayList<>();//全程最大、最小、平均血氧饱和度值
        boStatInfoList1.add(boStatInfo);
        analysisReult.setBoStatInfoList(boStatInfoList1);
        return analysisReult;
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
     * 计算睡眠全程呼吸率最大、最小、平均值
     * @param Data_smooth
     * @param onBedDataList
     * @param sleepData
     * @return
     */
    private AnalysisReult.ReStatInfo sleepPhaseReDate(List<SleepInfo> Data_smooth,List<SleepData.OnBedData> onBedDataList,SleepData sleepData){

        List<AnalysisReult.ReStatInfo> reStatInfoList = new ArrayList<>();
        AnalysisReult analysisReult = new AnalysisReult();

        for (int i = 0; i < onBedDataList.size(); i++) {
            AnalysisReult.ReStatInfo reStatInfo = new AnalysisReult.ReStatInfo();
            int start = onBedDataList.get(i).getHrStartTime();
            int end = onBedDataList.get(i).getOnBenEndTime();
            if (start < end){
                //各睡眠段心率最大、最小、平均
                double phaseRe_max = Math.round(Data_smooth.get(start).getRe());//假设第一个为最大值
                double phaseHrRe_min = Math.round(Data_smooth.get(start).getRe());//假设第一个为最小值
                double phaseRe_avg = 0.0;
                double avaTemp = 0;
                for (int j = start; j < end; j++) {
                    if (Math.round(Data_smooth.get(j).getRe()) > phaseRe_max){//求心率最大值
                        phaseRe_max = Math.round(Data_smooth.get(j).getRe());
                    }
                    if (Math.round(Data_smooth.get(j).getRe()) < phaseHrRe_min){//求心率最小值
                        phaseHrRe_min = Math.round(Data_smooth.get(j).getRe());
                    }
                    avaTemp += Math.round(Data_smooth.get(j).getRe());//心率平均值
                }
                phaseRe_avg = avaTemp/(end-start);
                reStatInfo.setMax(phaseRe_max);
                reStatInfo.setMin(phaseHrRe_min);
                reStatInfo.setAvg(Math.round(phaseRe_avg));
                reStatInfoList.add(reStatInfo);
            }
        }
        //各睡眠段心率最大、最小、平均值计算end
        analysisReult.setReStatInfoList(reStatInfoList);

        //全程心率最大、最小、平均值计算start
        double re_max_all = reStatInfoList.get(0).getMax();
        double re_min_all = reStatInfoList.get(0).getMin();
        double re_avg_all = 0.0;
        double temp = 0.0;
        for (int i = 0; i < reStatInfoList.size(); i++) {
            if (reStatInfoList.get(i).getMax() > re_max_all){
                re_max_all = reStatInfoList.get(i).getMax();
            }
            if (reStatInfoList.get(i).getMin() < re_min_all){
                re_min_all = reStatInfoList.get(i).getMin();
            }
            temp += reStatInfoList.get(i).getAvg();
        }
        re_avg_all =  temp/reStatInfoList.size();
        AnalysisReult.ReStatInfo reStatInfo = new AnalysisReult.ReStatInfo();
        reStatInfo.setMax(Math.round(re_max_all));
        reStatInfo.setMin(Math.round(re_min_all));
        reStatInfo.setAvg(Math.round(re_avg_all));
        return reStatInfo;
    }


    /**
     * 计算该在床时间段的睡眠分期
     * @param sleepInfoList 整个睡眠数据
     * @param onBedData     当前睡眠段
     * @param first         是否为首次入睡睡眠段
     * @param htStatByWhole 整个睡眠周期的心率统计值
     * @return
     */
    private List<AnalysisReult.SleepStage> sleepStageSplit(List<SleepInfo> sleepInfoList, SleepData.OnBedData onBedData, boolean first,
                                                           AnalysisReult.HrStatInfo htStatByWhole,List<AnalysisReult.SleepStage> onLeaveBed) {
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
        /*for (int i = 0; i < troughAndPeakList.size(); i++) {
            if (troughAndPeakList.get(troughAndPeakList.size()-1)[1] > onBedData.getOnBenEndTime()){
                troughAndPeakList.get(troughAndPeakList.size()-1)[1] = onBedData.getOnBenEndTime();
            }
        }*/

        //与最低心率差值 阈值
        int minHrDiffThreshold = 6;
        //波谷前偏移时间
        //int troughBeforeTimeThreshold = 3 * 60;
        int troughBeforeTimeThreshold = 9 * 60;
        //波谷后偏移时间
        //int troughAfterTimeThreshold1 = 2 * 60;
        int troughAfterTimeThreshold1 = 8 * 60;
        //波谷后偏移时间
        int troughAfterTimeThreshold2 = 1 * 60;

        //波峰与最大心率差值阈值
        int maxHrDiffThreshold = 8;
        //波峰前时间阈值
        //int peakBeforeTimeThreshold = 2 * 60;
        int peakBeforeTimeThreshold = 4 * 60;
        //波峰后时间阈值
        //int peakAfterTimeThreshold = 3 * 60;
        int peakAfterTimeThreshold = 6 * 60;

        //基于上面的心率波峰、波谷 计算浅睡期
        if (sleepStageList.size() > 0 ){
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
        }
        //对异常睡眠分析段处理：如果分期开始时间大于结束时间，将该分期段拼接到上一个分期段，并舍弃该分期段
        for (int i = 0; i < sleepStageList.size() - 1; i++) {
            if (sleepStageList.get(i).getStartTime() > sleepStageList.get(i).getEndTime()){
                sleepStageList.get(i+1).setStartTime(sleepStageList.get(i-1).getEndTime());
                sleepStageList.remove(i);
                i--;
            }
        }
        //如果最后一个分期段的结束时间不是该睡眠段的结束时间
        if (sleepStageList.size() > 0 && sleepStageList.get(sleepStageList.size()-1).getEndTime() != onBedData.getOnBenEndTime()){
            sleepStageList.get(sleepStageList.size()-1).setEndTime(onBedData.getOnBenEndTime());
        }
        //将离床段拼接到分期段中
        if (sleepStageList.size() > 0){
            int end = sleepStageList.get(sleepStageList.size()-1).getEndTime();
            for (int i = 0; i < onLeaveBed.size(); i++) {
                if (onLeaveBed.get(i).getStartTime() - end >= 0){
                    onLeaveBed.get(i).setStartTime(onLeaveBed.get(i).getStartTime());
                    onLeaveBed.get(i).setEndTime(onLeaveBed.get(i).getEndTime());
                    sleepStageList.add(onLeaveBed.get(i));
                    return sleepStageList;
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
        //开始时刻为0心率 开始时刻后20分钟
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
                //endCursor += windows;
                endCursor = startTime + windows;
            }
            if (startTime < endCursor){
                if (endCursor > onBedData.getOnBenEndTime()){
                    endCursor = onBedData.getOnBenEndTime();
                    Integer[] res = CommonUtils.maxOrMinHr(sleepInfoList, startTime, endCursor, k % 2 > 0 ? Constants.Min : Constants.Max);
                    startTime = res[1] + 1; //指标向右移动
                    result.add(new Integer[]{res[0], res[1]});
                    return result;
                }
                Integer[] res = CommonUtils.maxOrMinHr(sleepInfoList, startTime, endCursor, k % 2 > 0 ? Constants.Min : Constants.Max);
                startTime = res[1] + 1; //指标向右移动
                result.add(new Integer[]{res[0], res[1]});
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
        SleepData sleepData = new SleepData();
        //监测时长-离床时长小于等于300秒，判定全程为离床
        List<Integer> listLeaveBed = new ArrayList();
        for (int i = 0; i < sleepInfo.size(); i++) {
            if (sleepInfo.get(i).getStatus() == Constants.SleepStatus.LeaveBed.getValue()){
                listLeaveBed.add(i);
            }
        }
        if (sleepInfo.size() - listLeaveBed.size() < 300){
            leaveOnBedInfo.setLeaveOnBedStartTime(1);
            leaveOnBedInfo.setLeaveOnBedEndTime(sleepInfo.size());
            leaveOnBedList.add(leaveOnBedInfo);
            sleepData.setLeaveDatas(leaveOnBedList);//离床数据
            sleepData.setOffBedAllTime(sleepInfo.size());//离床总时长
            sleepData.setOffBedTime(1);//离床次数
        }else {
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
                if (leaveOnBedList.size() > 0 && leaveOnBedList.get(0).getLeaveOnBedStartTime() == 0){
                    leaveStart += 1;
                }else {
                    leaveStart = 0;
                }
                //结束监测的离床状态去除（不做为夜间起床）
                if (leaveOnBedList.size() > 0 && leaveOnBedList.get(leaveOnBedList.size()-1).getLeaveOnBedEndTime() == sleepInfo.size()){
                    leaveEnd = leaveOnBedList.size()-1;
                }else {
                    leaveEnd = leaveOnBedList.size();
                }
            }
            int leaveOnBedTime = 0;//离床时长
            for (int i = leaveStart; i < leaveEnd; i++) {
                leaveOnBedTime += (leaveOnBedList.get(i).getLeaveOnBedEndTime() - leaveOnBedList.get(i).getLeaveOnBedStartTime() + 1);
            }
            sleepData.setLeaveDatas(leaveOnBedList);//离床数据
            sleepData.setOffBedAllTime(leaveOnBedTime);//离床总时长
            sleepData.setOffBedTime(leaveEnd - leaveStart);//离床次数
        }

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
            onBedData.setOnBedStartTime(0);
            onBedData.setOnBenEndTime(sleepInfoList.size());
            for (int i = 1; i < 90; i++) {
                if (0.0 != sleepInfoList.get(i).getHr() && 0.0 != sleepInfoList.get(i).getRe()){
                    onBedData.setHrStartTime(i);
                    break;
                }
            }
            onBedDataList.add(onBedData);
        }else {
            if (leaveDatas.get(0).getLeaveOnBedEndTime() != sleepInfoList.size()){
                int start = 0;
                int j = 0;
                SleepData.OnBedData onBedData = new SleepData().new OnBedData();
                if (leaveDatas.get(0).getLeaveOnBedStartTime() == 0){//如果监测从离床状态开始
                    onBedData.setOnBedStartTime(leaveDatas.get(0).getLeaveOnBedEndTime());
                    start = 1;
                }else {
                    onBedData.setOnBedStartTime(0);
                    start = 0;
                }
                for (int i = start; i < leaveDatas.size(); i++) {
                    onBedData.setOnBenEndTime(leaveDatas.get(i).getLeaveOnBedStartTime());
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
                    onBedData.setOnBedStartTime(leaveDatas.get(i).getLeaveOnBedEndTime());
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
                    j = onBedDataList.get(onBedDataList.size()-1).getOnBedStartTime();
                    while (j < onBedDataList.get(onBedDataList.size() - 1).getOnBedStartTime() + 90) {
                        if (j < sleepInfoList.size() && 0.0 != sleepInfoList.get(j).getHr() && 0.0 != sleepInfoList.get(j).getRe() || j == sleepInfoList.size()) {
                            onBedData.setHrStartTime(j);
                            j = onBedDataList.get(onBedDataList.size() - 1).getOnBedStartTime() + 90;
                            break;
                        }
                        j += 1;
                    }
                }
            }
        }
        //如果末次睡眠段未找到非0心率与非0呼吸率时刻，将此时刻设为监测终止时刻（最后一段睡眠记录不完整时出现此情况）
        /*if (onBedDataList.get(onBedDataList.size()-1).getHrStartTime() == 0){
            onBedDataList.get(onBedDataList.size()-1).setHrStartTime(onBedDataList.get(onBedDataList.size()-1).getOnBenEndTime());
        }*/
        for (int i = 0; i < onBedDataList.size(); i++) {
            if (onBedDataList.get(i).getHrStartTime() < onBedDataList.get(i).getOnBedStartTime() || onBedDataList.get(i).getHrStartTime() == 0){
                onBedDataList.get(i).setHrStartTime(onBedDataList.get(i).getOnBenEndTime());
            }
            if (onBedDataList.get(i).getHrStartTime() > onBedDataList.get(i).getOnBenEndTime()){
                onBedDataList.get(i).setHrStartTime(onBedDataList.get(i).getOnBenEndTime());
            }
        }
        return onBedDataList;
    }

    /**
     * 打鼾数据
     * @param sleepInfo
     * @return
     */
    private SleepData getSnoreData(List<SleepInfo> sleepInfo) {

        List<SnoreInfo> snoreInfoList = new ArrayList<>();
        int flag = 0;
        SnoreInfo snoreInfo = new SnoreInfo();
        for (int i = 0; i < sleepInfo.size() - 1; i++) {
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
            }
        }
        SleepData sleepData = new SleepData();
        if (snoreInfoList.size() > 0 && snoreInfoList != null){
            if (snoreInfoList.get(snoreInfoList.size()-1).getSnoreStartTime() != 0){//如果最后的第n次为打鼾
                if (snoreInfoList.get(snoreInfoList.size()-1).getSnoreEndTime() == 0){
                    snoreInfoList.get(snoreInfoList.size()-1).setSnoreEndTime(sleepInfo.size());//将此次打鼾结束时间设为睡眠监测结束时刻
                }
            }
            int snortTime = 0;//打鼾总时长
            int snortTimes = 0;//打鼾次数
            for (int i = 0; i < snoreInfoList.size(); i++) {
                snortTime += (snoreInfoList.get(i).getSnoreEndTime() - snoreInfoList.get(i).getSnoreStartTime() + 1);
            }
            //记录打鼾次数
            snortTimes = snoreInfoList.size();
            sleepData.setSnoreInfoList(snoreInfoList);
            sleepData.setSnoreAllTime(snortTime);
            sleepData.setSnoreAllTimes(snortTimes);
        }
        return sleepData;
    }

    /**
     * 弱呼吸（低通气）数据
     * @param sleepInfo
     * @return
     */
    private SleepData getShallowBreath(List<SleepInfo> sleepInfo){
        List<ShallowBreathInfo> shallowBreathInfoList = new ArrayList<>();
        int flag = 0;
        ShallowBreathInfo shallowBreathInfo = new ShallowBreathInfo();
        for (int i = 0; i < sleepInfo.size() - 1; i++) {
            if (sleepInfo.get(i).getStatus() == Constants.SleepStatus.ShallowBreath.getValue()){//判定为弱呼吸状态
                if (flag == 0){
                    shallowBreathInfo.setShallowBreathStart(i);
                    flag = 1;
                    if (sleepInfo.get(i+1).getStatus() - sleepInfo.get(i).getStatus() != 0){
                        shallowBreathInfo.setShallowBreathEnd(i);
                        shallowBreathInfoList.add(shallowBreathInfo);
                        flag = 0;
                        shallowBreathInfo = new ShallowBreathInfo();
                    }
                }else {
                    if (sleepInfo.get(i+1).getStatus() - sleepInfo.get(i).getStatus() != 0 && flag != 0){
                        shallowBreathInfo.setShallowBreathEnd(i);
                        shallowBreathInfoList.add(shallowBreathInfo);
                        flag = 0;
                        shallowBreathInfo = new ShallowBreathInfo();
                    }
                }
            }
        }
        SleepData sleepData = new SleepData();
        if (shallowBreathInfoList.size() > 0 && shallowBreathInfoList != null){
            if (shallowBreathInfoList.get(shallowBreathInfoList.size() - 1).getShallowBreathStart() != 0){
                if (shallowBreathInfoList.get(shallowBreathInfoList.size() - 1).getShallowBreathEnd() == 0){
                    shallowBreathInfoList.get(shallowBreathInfoList.size() - 1).setShallowBreathEnd(sleepInfo.size());//将此次弱呼吸结束时间设为睡眠监测结束时刻
                }
            }
            int shallowBreathTime = 0;//弱呼吸总时长
            int shallowBreathTimes = 0;//弱呼吸次数
            shallowBreathTimes = shallowBreathInfoList.size();
            for (int i = 0; i < shallowBreathInfoList.size(); i++) {
                shallowBreathTime += (shallowBreathInfoList.get(i).getShallowBreathEnd() - shallowBreathInfoList.get(i).getShallowBreathStart() + 1);
            }
            //记录弱呼吸次数
            sleepData.setShallowBreathInfoList(shallowBreathInfoList);
            sleepData.setShallowBreathTime(shallowBreathTime);
            sleepData.setShallowBreathTimes(shallowBreathTimes);
        }
        return sleepData;
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
                    sleepInfoList.get(i).setHr(sleepInfoList.get(i-1).getHr());
                }
                if (0.0 == sleepInfoList.get(i).getRe()) {
                    sleepInfoList.get(i).setRe(sleepInfoList.get(i-1).getRe());
                }
                if (0.0 == sleepInfoList.get(i).getBo()){
                    sleepInfoList.get(i).setBo(sleepInfoList.get(i-1).getBo());
                }
            }
        }
        return sleepInfoList;
    }

    /**
     * 对心率&呼吸率进行平滑滤波处理
     * @param list
     */
    public static List<SleepInfo> smooth(List<SleepInfo> list, int start, int end) {
        if (list.size() >= 5 && (start + 2) < list.size()) {
            list.get(start).setHr(list.get(start).getHr());
            list.get(start + 1).setHr(CommonUtils.twoDecimalD((list.get(start).getHr() + list.get(start + 1).getHr() + list.get(start + 2).getHr()) / 3));

            list.get(start).setRe(list.get(start).getRe());
            list.get(start + 1).setRe(CommonUtils.twoDecimalD((list.get(start).getRe() + list.get(start + 1).getRe() + list.get(start + 2).getRe()) / 3));

            list.get(start).setBo(list.get(start).getBo());
            list.get(start + 1).setBo(CommonUtils.twoDecimalD((list.get(start).getBo() + list.get(start + 1).getBo() + list.get(start + 2).getBo()) / 3));

            for (int i = start + 2; i < end - 2; i++) {
                list.get(i).setHr(CommonUtils.twoDecimalD(
                        (list.get(i - 2).getHr() + list.get(i - 1).getHr() + list.get(i).getHr() + list.get(i + 1).getHr() + list.get(i + 2).getHr()) / 5));

                list.get(i).setRe(CommonUtils.twoDecimalD(
                        (list.get(i - 2).getRe() + list.get(i - 1).getRe() +list.get(i).getRe() + list.get(i + 1).getRe() + list.get(i + 2).getRe()) / 5));

                list.get(i).setBo(CommonUtils.twoDecimalD(
                        (list.get(i - 2).getBo() + list.get(i - 1).getBo() +list.get(i).getBo() + list.get(i + 1).getBo() + list.get(i + 2).getBo()) / 5));
            }

            (list.get(end - 2)).setHr(CommonUtils.twoDecimalD(((list.get(end - 3)).getHr() + (list.get(end - 2)).getHr() + (list.get(end - 12)).getHr()) / 3.0D));
            (list.get(end - 1)).setHr((list.get(end - 1)).getHr());

            (list.get(end - 2)).setRe(CommonUtils.twoDecimalD(((list.get(end - 3)).getRe() + (list.get(end - 2)).getRe() + (list.get(end - 12)).getRe()) / 3.0D));
            (list.get(end - 1)).setRe((list.get(end - 1)).getRe());

            (list.get(end - 2)).setBo(CommonUtils.twoDecimalD(((list.get(end - 3)).getBo() + (list.get(end - 2)).getBo() + (list.get(end - 12)).getBo()) / 3.0D));
            (list.get(end - 1)).setBo((list.get(end - 1)).getBo());
        }
        return list;
    }


    private static List<SleepInfo> smoothNew(List<SleepInfo> list, int start,int end, int window) {

        if (CollectionUtil.isEmpty(list) || start > end || list.size() <= start || list.size() <= end) {
            throw new RuntimeException("参数错误");
        }

        window = window % 2 == 0 ? window + 1 : window;

        for (int i = start + 1; i < end; i++) {
            if (i-start < window/2) {
                int step = i-start;
                double avg = list.stream().skip(start).limit(2 * step + 1).mapToDouble(SleepInfo::getHr).sum() / (2 * step + 1);
                list.get(i).setHr(avg);
            } else if (end - i < window/2){
                int step = end - i;
                double avg = list.stream().skip(i - step).limit(2*step + 1).mapToDouble(SleepInfo::getHr).sum() / (2 * step + 1);
                list.get(i).setHr(avg);
            } else {
                double avg = list.stream().skip(i - window/2).limit(window).mapToDouble(SleepInfo::getHr).sum() / window;
                list.get(i).setHr(avg);
            }
        }
        return list;
    }
}
