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
import java.util.Timer;
import java.util.TimerTask;

/**
 * 应用程序
 */
public class Application {

    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {

        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    ExecuteResult executeResult = startAnalysis(args[0]);

                    logger.info("analysis finished: %s", JSONObject.toJSONString(executeResult));
                } catch (Exception e) {
                    logger.error("startAnalysis error,", e);
                }
            }
        }, 1000L, 1000000L);
    }

    public static ExecuteResult startAnalysis(String fileDir) {

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

            //从本地文件中读取并封装睡眠数据
            SleepData sleepData = analysisService.loadDataByLoaclFile(file);

            //分析结果
            AnalysisReult analysisReult = analysisService.executeAnalysis(sleepData);

            //结果输出
            analysisService.writeAnalysisResult(analysisReult);

            logger.info("file: %s begin analysis", file.getName());
        }
       return executeResult;
    }
}
