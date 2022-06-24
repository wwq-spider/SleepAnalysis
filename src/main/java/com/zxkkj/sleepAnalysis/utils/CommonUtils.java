package com.zxkkj.sleepAnalysis.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.text.csv.CsvRow;
import com.zxkkj.sleepAnalysis.constants.Constants;
import com.zxkkj.sleepAnalysis.model.SleepInfo;

import java.util.List;

public class CommonUtils {

    /**
     * 求最大/最小心率
     * @param rowList
     * @param type 1:求最小  2:求最大
     * @return
     */
    public static Integer[] maxOrMinHr(List<SleepInfo> rowList, int type) {
        if (CollectionUtil.isEmpty(rowList)) {
            throw new IllegalArgumentException("Number array must not empty !");
        } else {
            double current = rowList.get(0).getHr();
            int index = 0;
            for(int i = 1; i < rowList.size(); ++i) {
                double curHr = rowList.get(i).getHr();
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
