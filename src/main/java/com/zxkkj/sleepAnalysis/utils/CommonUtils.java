package com.zxkkj.sleepAnalysis.utils;
import cn.hutool.core.collection.CollectionUtil;
import com.zxkkj.sleepAnalysis.constants.Constants;
import com.zxkkj.sleepAnalysis.model.SleepInfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CommonUtils {

    /**
     * 求心率最大值/最小值
     * @param sleepInfoList
     * @param start 开始下标
     * @param end  结束下标
     * @param type type 1:求最小  2:求最大
     * @return
     */
    public static Integer[] maxOrMinHr(List<SleepInfo> sleepInfoList, int start, int end, int type) {
        if (end > sleepInfoList.size()){
            end = sleepInfoList.size();
        }
        if (CollectionUtil.isEmpty(sleepInfoList)) {
            throw new IllegalArgumentException("Number array must not empty !");
        } else {
            double current = sleepInfoList.get(start).getHr();
            int index = start;
            for(int i = start+1; i < end; ++i) {
                double curHr = sleepInfoList.get(i).getHr();
                if (type == Constants.Min) { //取最小
                    if (current > curHr) {
                        current = curHr;
                        index = i;
                    }
                } else if (type == Constants.Max) { //取最小
                    if (current < curHr) {
                        current = curHr;
                        index = i;
                    }
                }
            }
            return new Integer[]{(int) current, index};
        }
    }

    public static double twoDecimal(Integer value){
        return new BigDecimal((float)value).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static double twoDecimalD(double value){
        BigDecimal bigDecimal = new BigDecimal(value);
        return bigDecimal.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static double twoDecimalI(Integer value1,Integer value2){
        double result = new BigDecimal((float)value1/value2).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    public static List<SleepInfo> smoothNew(List<SleepInfo> list, int start,int end, int window) {

        if (CollectionUtil.isEmpty(list) || start > end || list.size() <= start || list.size() <= end) {
            throw new RuntimeException("参数错误");
        }

        List<SleepInfo> listNew =new ArrayList<>();
        SleepInfo sleepInfo = new SleepInfo();
        sleepInfo.setHr(list.get(0).getHr());
        sleepInfo.setStatus(list.get(0).getStatus());
        sleepInfo.setBo(list.get(0).getBo());
        sleepInfo.setRe(list.get(0).getRe());
        listNew.add(sleepInfo);
        for (int i = start + 1; i < end; i++) {
            double avg = 0.0;
            if (i-start < window/2) {
                int step = i-start;
                avg = list.stream().skip(start).limit(2 * step + 1).mapToDouble(SleepInfo::getHr).sum() / (2 * step + 1);
            } else if (end - i < window/2){
                int step = end - i;
                avg = list.stream().skip(i - step).limit(2*step + 1).mapToDouble(SleepInfo::getHr).sum() / (2 * step + 1);
            } else {
                avg = list.stream().skip(i - window/2).limit(window).mapToDouble(SleepInfo::getHr).sum() / window;
            }
            SleepInfo sleepInfo1 = new SleepInfo();
            sleepInfo1.setHr(avg);
            sleepInfo1.setRe(list.get(i).getRe());
            sleepInfo1.setStatus(list.get(i).getStatus());
            sleepInfo1.setBo(list.get(i).getBo());
            listNew.add(sleepInfo1);
        }
        SleepInfo sleepInfoEnd = new SleepInfo();
        sleepInfoEnd.setHr(list.get(list.size()-1).getHr());
        sleepInfoEnd.setBo(list.get(list.size()-1).getBo());
        sleepInfoEnd.setRe(list.get(list.size()-1).getRe());
        sleepInfoEnd.setStatus(list.get(list.size()-1).getStatus());
        listNew.add(sleepInfoEnd);
        return listNew;
    }
}