package com.lohika.morning.ml.spark.driver.service.lyrics;

import java.util.Map;

public class MultiClassGenrePrediction {

    private String genre;
    private Map<String, Double> probabilities;

    public MultiClassGenrePrediction(String genre, Map<String, Double> probabilities) {
        this.genre = genre;
        this.probabilities = probabilities;
    }

    public String getGenre() {
        return genre;
    }

    public Map<String, Double> getProbabilities() {
        return probabilities;
    }

}
