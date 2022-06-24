package com.zxkkj.sleepAnalysis.utils;
import cn.hutool.core.collection.CollectionUtil;
import com.zxkkj.sleepAnalysis.constants.Constants;
import com.zxkkj.sleepAnalysis.model.SleepInfo;
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
}