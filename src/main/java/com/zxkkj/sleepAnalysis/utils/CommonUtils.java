package com.zxkkj.sleepAnalysis.utils;

import java.math.BigDecimal;

public class CommonUtils {
    public static double twoDecimalD(double value){
        BigDecimal bigDecimal = new BigDecimal(value);
        return bigDecimal.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
    }
}