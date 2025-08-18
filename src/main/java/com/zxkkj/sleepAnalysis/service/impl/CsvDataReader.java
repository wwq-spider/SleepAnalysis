package com.zxkkj.sleepAnalysis.service.impl;

import com.zxkkj.sleepAnalysis.model2.SleepData;
import com.zxkkj.sleepAnalysis.service.DataReader;
import com.zxkkj.sleepAnalysis.service.SleepDataModel;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV数据读取器实现
 */
public class CsvDataReader implements DataReader {
    @Override
    public List<SleepDataModel> readData(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            return br.lines()
                    .map(line -> line.split(","))
                    .filter(values -> values.length >= 5)
                    .map(values -> new SleepData(
                            Integer.parseInt(values[0].trim()),
                            Double.parseDouble(values[1].trim()),
                            Integer.parseInt(values[2].trim()),
                            Integer.parseInt(values[3].trim()),
                            Double.parseDouble(values[4].trim())
                    )).collect(Collectors.toList());
        } catch (IOException | NumberFormatException e) {
            throw new RuntimeException("数据读取失败: " + e.getMessage(), e);
        }
    }
}
