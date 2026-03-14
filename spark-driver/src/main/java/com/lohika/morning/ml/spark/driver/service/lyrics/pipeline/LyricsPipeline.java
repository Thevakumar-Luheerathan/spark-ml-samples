package com.lohika.morning.ml.spark.driver.service.lyrics.pipeline;

import com.lohika.morning.ml.spark.driver.service.lyrics.GenrePrediction;
import com.lohika.morning.ml.spark.driver.service.lyrics.MultiClassGenrePrediction;
import java.util.Map;
import org.apache.spark.ml.tuning.CrossValidatorModel;

public interface LyricsPipeline {

    CrossValidatorModel classify();

    GenrePrediction predict(String unknownLyrics);

    Map<String, Object> getModelStatistics(CrossValidatorModel model);

    default Map<String, Object> classifyFromCsv() {
        throw new UnsupportedOperationException("classifyFromCsv() is not supported by this pipeline.");
    }

    default MultiClassGenrePrediction predictMultiClass(String unknownLyrics) {
        throw new UnsupportedOperationException("predictMultiClass() is not supported by this pipeline.");
    }

}
