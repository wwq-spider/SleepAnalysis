package com.zxkkj.sleepAnalysis.service.impl;

import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.zxkkj.sleepAnalysis.model.AnalysisReult;
import com.zxkkj.sleepAnalysis.model.SleepData;
import com.zxkkj.sleepAnalysis.service.IAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.zip.Adler32;

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
        int n = 1;//第1个睡眠段（离床将睡眠分为几个睡眠段）
        //String[] array = new String[3];//睡眠分析段：行号为睡眠段序号，第1列为在床起始时刻、第2列为心率呼吸产生时刻、第3列为在床结束时刻
        if ("0".equals(leaveDatas.get(0)[0])){//如果无离床段
            String[] array = new String[3];
            sleepData.getBedData().add(n-1,array);
            //全程为睡眠段
            array[0] = "1";
            array[2] = String.valueOf(rowList.size());
            for (int i = 0; i < 90; i++) {//从开始监测的90秒内搜寻非0心率与非0呼吸率时刻(因为在床后60秒即可测出心率和呼吸率)
                if (!"0".equals(rowList.get(i).get(1)) && !"0".equals(rowList.get(i).get(2))){
                    array[1] = String.valueOf(i);
                }
            }
        }else {
            String[] array = new String[3];
            int k1 = 0;
            if ("1".equals(leaveDatas.get(0)[0])){//如果监测从离床状态开始
                array[0] = leaveDatas.get(0)[1];//以离床状态结束作为第一睡眠段的开始时刻
                k1 = 2;//从第2离床段开始搜寻睡眠段
            }else {
                array[0] = "1";//如果监测从在床状态开始，开始监测时刻为第一睡眠段的开始时刻
                k1 = 1;//从第1离床段开始搜索睡眠段
            }
            for (int i = k1; i < leaveDatas.size(); i++) {//从在床后第1次离床开始逐段确认
                sleepData.getBedData().add(n-1,array);
                array[2] = String.valueOf(Integer.valueOf(leaveDatas.get(0)[0]) - 1);//第n段睡眠结束段为第i次离床开始的前时刻
                int j = Integer.valueOf(sleepData.getBedData().get(n-1)[0]);
                while (j < Integer.valueOf(sleepData.getBedData().get(n-1)[0]) + 90){//搜寻非0心率非0呼吸率起始时刻
                    if (!"0".equals(rowList.get(j).get(1)) && !"0".equals(rowList.get(j).get(2)) || j==rowList.size()){
                        array[1] = String.valueOf(j);
                        j = sleepData.getBedData().size() + 90;//结束搜寻
                    }
                    j += 1;
                }
                n += 1;
                array = new String[3];
                array[0] = String.valueOf(Integer.valueOf(leaveDatas.get(i)[1])+1);//新一段睡眠开始时刻为第i次离床结束的后时刻
            }
            if (Integer.valueOf(sleepData.getBedData().get(sleepData.getBedData().size()-1)[0]) >= rowList.size()){//如果最后一次睡眠段起始时刻超过监测终点时刻
                //删除最后一次睡眠段（说明最后记录为永久离床）
                sleepData.getBedData().remove(n-1);
            }else {//最后一次睡眠起始时刻未超过监测终点，则以监测终点为该睡眠段终点，并从起始时刻搜寻非0数据起始位置
                sleepData.getBedData().get(n-1)[2] = String.valueOf(rowList.size());
                sleepData.getBedData().remove(n);
                int j = Integer.valueOf(sleepData.getBedData().get(n-1)[0]);
                while (j < Integer.valueOf(sleepData.getBedData().get(n-1)[0]) + 90){//搜寻非0心率非0呼吸率起始时刻
                    if (!"0".equals(rowList.get(j).get(1)) && !"0".equals(rowList.get(j).get(2)) || j==rowList.size()){
                        sleepData.getBedData().get(n-1)[1] = String.valueOf(j);
                        j = Integer.valueOf(sleepData.getBedData().get(n-1)[0]) + 90;//结束搜寻
                    }
                    j += 1;
                }
            }
        }
        if ("0".equals(sleepData.getBedData().get(sleepData.getBedData().size()-1)[1])){//如果末次睡眠段未找到非0心率与非0呼吸率时刻，将此时刻设为监测终止时刻（最后一段睡眠记录不完整时出现此情况）
            sleepData.getBedData().get(sleepData.getBedData().size()-1)[1] = sleepData.getBedData().get(sleepData.getBedData().size()-1)[2];
        }
        //剔除各睡眠段偶发0数据并进行临近插值处理
        for (int j = 1; j < sleepData.getBedData().size(); j++) {//对各睡眠段数据的0数据进行插值处理
            for (int i = Integer.valueOf(sleepData.getBedData().get(j)[1]); i < Integer.valueOf(sleepData.getBedData().get(j)[2]); i++) {
                if ("0".equals(rowList.get(i).get(1))){
                    //
                }
            }
        }
        //对非0心率0呼吸段的数据进行平滑滤波
        return null;
    }

    @Override
    public void writeAnalysisResult(AnalysisReult analysisReult) {

    }

    /**
     * 搜寻离床时间段：得出各离床时间段数组off_bed、离床次数与总时长数组off_bed_sum
     * @param rowList
     * @return
     */
    private SleepData leaveOnBed(List<CsvRow> rowList) {
        SleepData sleepData = new SleepData();
        List<String[]> list = new ArrayList<>();
        int n = 1;//第1个离床时间段
        int start = 0;//离床时间段起始时刻，0为未计时
        String leaveOn = "";
        for (int i = 1; i < rowList.size() - 1; i++) {
            String[] array = new String[2];//离床数据组：行号为离床顺序号，第1列为该次离床起始时间，第2列为该次离床结束时间（返回在床）
            leaveOn = rowList.get(i).get(0);//暂时取第一列为离床数据
            if (leaveOn.equals("1")) {//为离床状态
                if (start == 0) {//i时刻刚变为离床状态
                    array[0] = String.valueOf(i);
                    start = 1;
                    if (Integer.valueOf(rowList.get(i + 1).get(0)) - Integer.valueOf(rowList.get(i).get(0)) != 0) {
                        array[1] = String.valueOf(i);
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
                if (array[0]!=null || array[1] != null){//过滤array为null的数组
                    list.add(array);
                }
            }
            sleepData.setLeaveDatas(list);
        }
        if (n == 1) {//如果n为1，即没有离床段
            //删除首行后面的所有行，首行数据为0 0
            String[] temp = new String[]{"0","0"};
            List<String[]> listTemp = new ArrayList<>();
            listTemp.add(temp);
            sleepData.setLeaveDatas(listTemp);
        } else {
            if (null != list.get(n)[0]) {//如果最后的第n次为离床
                if (null == list.get(n)[1]) {//如果最后序号的离床是不完整的，说明睡眠监测结束时一直是离床
                    list.get(n)[1] = String.valueOf(rowList.size());//将此次离床结束时间设为睡眠监测结束时刻
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
            //对数据为null的置为0
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i)[0] == null){
                    list.get(i)[0] = "0";
                }
                if (list.get(i)[1] == null){
                    list.get(i)[1] = "0";
                }
            }
            int i = 0;
            while (i < list.size()) {
                if (Integer.valueOf(list.get(i)[1]) - Integer.valueOf(list.get(i)[0]) <= 5) {
                    list.remove(i);
                } else {
                    if (Integer.valueOf(list.get(i + 1)[0]) - Integer.valueOf(list.get(i)[1]) <= 10) {
                        list.get(i)[1] = list.get(i + 1)[1];
                        list.remove(i + 1);
                    } else {
                        i += 1;
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
            if (list.get(list.size()-1)[1].equals(rowList.size())) {
                off_bed_end = list.size() - 1;
            } else {
                off_bed_end = list.size();
            }
            int offBedTime = off_bed_end - off_bed_start + 1;//离床次数
            int offBedAllTime = 0;//离床总时长
            for (int j = off_bed_start; j < off_bed_end; j++) {
                offBedAllTime += Integer.valueOf(list.get(j)[1]) - Integer.valueOf(list.get(j)[0]) + 1;
            }
            sleepData.setOffBedTime(offBedTime);
            sleepData.setOffBedAllTime(offBedAllTime);
        }
        return sleepData;
    }

}
