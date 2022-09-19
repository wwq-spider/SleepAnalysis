package com.zxkkj.sleepAnalysis;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.zxkkj.sleepAnalysis.model.AnalysisReult;
import com.zxkkj.sleepAnalysis.model.ExecuteResult;
import com.zxkkj.sleepAnalysis.model.SleepData;
import com.zxkkj.sleepAnalysis.service.IAnalysisService;
import com.zxkkj.sleepAnalysis.service.impl.AnalysisServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.List;

/**
 * 应用程序
 */
public class Application {

    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {

        try {
            /*String args0 = "C:\\Users\\Heyq\\Desktop\\sleepTestNew\\testOld";
            String args1 = "C:\\Users\\Heyq\\Desktop\\sleepTestNew\\";
            ExecuteResult executeResult = startAnalysis(args0,args1);*/
            ExecuteResult executeResult = startAnalysis(args[0],args[1]);
            logger.info("analysis finished: %s", JSONObject.toJSONString(executeResult));
        } catch (Exception e) {
            logger.error("startAnalysis error,", e);
        }
    }

    public static ExecuteResult startAnalysis(String fileDir,String outTxtPath) {

        if (!FileUtil.exist(fileDir)) {
            logger.error("filePath: %s is not exist", fileDir);
            return new ExecuteResult();
        }

        //执行结果: 成功多少 失败多少
        ExecuteResult executeResult = new ExecuteResult();

        List<File> fileList = FileUtil.loopFiles(fileDir);

        for (File file : fileList) {

            logger.info("file: %s begin analysis", file.getName());

            IAnalysisService analysisService = new AnalysisServiceImpl();

            SleepData sleepData = analysisService.loadDataByLoaclFile(file);

            //分析结果
            AnalysisReult analysisReult = analysisService.executeAnalysis(sleepData);

            //结果输出
            analysisService.writeAnalysisResult(analysisReult,outTxtPath,file);

        }
       return executeResult;
    }
}
