package com.zxkkj.sleepAnalysis;

import com.alibaba.fastjson.JSONObject;
import com.zxkkj.sleepAnalysis.model.ExecuteResult;
import com.zxkkj.sleepAnalysis.service.IAnalysisService;
import com.zxkkj.sleepAnalysis.service.impl.AnalysisServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用程序
 */
public class Application {
    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        try {
            //本地调试用
            /*String fileDir = "/Users/heyuqi/Desktop/sleep/问题数据";
            String outTxtPath = "/Users/heyuqi/Desktop/sleep/outTxtPath/";*/
            IAnalysisService analysisService = new AnalysisServiceImpl();
            //开始数据分析
            ExecuteResult executeResult = analysisService.startAnalysis(args[0],args[1]);
            logger.info("analysis finished: %s", JSONObject.toJSONString(executeResult));
        } catch (Exception e) {
            logger.error("startAnalysis error,", e);
        }
    }
}
