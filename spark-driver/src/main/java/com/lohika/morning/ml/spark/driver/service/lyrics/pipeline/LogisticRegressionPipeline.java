package com.lohika.morning.ml.spark.driver.service.lyrics.pipeline;

import static com.lohika.morning.ml.spark.distributed.library.function.map.lyrics.Column.*;
import com.lohika.morning.ml.spark.driver.service.lyrics.MultiClassGenrePrediction;
import com.lohika.morning.ml.spark.driver.service.lyrics.transformer.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.Transformer;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.classification.LogisticRegressionModel;
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator;
import org.apache.spark.ml.feature.IndexToString;
import org.apache.spark.ml.feature.StopWordsRemover;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.ml.feature.Tokenizer;
import org.apache.spark.ml.feature.Word2Vec;
import org.apache.spark.ml.feature.Word2VecModel;
import org.apache.spark.ml.linalg.DenseVector;
import org.apache.spark.ml.param.ParamMap;
import org.apache.spark.ml.tuning.CrossValidator;
import org.apache.spark.ml.tuning.CrossValidatorModel;
import org.apache.spark.ml.tuning.ParamGridBuilder;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.springframework.stereotype.Component;

@Component("LogisticRegressionPipeline")
public class LogisticRegressionPipeline extends CommonLyricsPipeline {

    public CrossValidatorModel classify() {
        Dataset<Row> sentences = readLyrics();

        Cleanser cleanser = new Cleanser();

        Numerator numerator = new Numerator();

        Tokenizer tokenizer = new Tokenizer()
                .setInputCol(CLEAN.getName())
                .setOutputCol(WORDS.getName());

        StopWordsRemover stopWordsRemover = new StopWordsRemover()
                .setInputCol(WORDS.getName())
                .setOutputCol(FILTERED_WORDS.getName());

        Exploder exploder = new Exploder();

        Stemmer stemmer = new Stemmer();

        Uniter uniter = new Uniter();
        Verser verser = new Verser();

        Word2Vec word2Vec = new Word2Vec()
                                    .setInputCol(VERSE.getName())
                                    .setOutputCol("features")
                                    .setMinCount(0);

        LogisticRegression logisticRegression = new LogisticRegression();

        Pipeline pipeline = new Pipeline().setStages(
                new PipelineStage[]{
                        cleanser,
                        numerator,
                        tokenizer,
                        stopWordsRemover,
                        exploder,
                        stemmer,
                        uniter,
                        verser,
                        word2Vec,
                        logisticRegression});

        ParamMap[] paramGrid = new ParamGridBuilder()
                .addGrid(verser.sentencesInVerse(), new int[]{4, 8, 16})
                .addGrid(word2Vec.vectorSize(), new int[] {100, 200, 300})
                .addGrid(logisticRegression.regParam(), new double[] {0.01D})
                .addGrid(logisticRegression.maxIter(), new int[] {100, 200})
                .build();

        CrossValidator crossValidator = new CrossValidator()
                .setEstimator(pipeline)
                .setEvaluator(new BinaryClassificationEvaluator())
                .setEstimatorParamMaps(paramGrid)
                .setNumFolds(10);

        CrossValidatorModel model = crossValidator.fit(sentences);

        saveModel(model, getModelDirectory());

        return model;
    }

    public Map<String, Object> getModelStatistics(CrossValidatorModel model) {
        Map<String, Object> modelStatistics = super.getModelStatistics(model);

        PipelineModel bestModel = (PipelineModel) model.bestModel();
        Transformer[] stages = bestModel.stages();

        modelStatistics.put("Sentences in verse", ((Verser) stages[7]).getSentencesInVerse());
        modelStatistics.put("Word2Vec vocabulary", ((Word2VecModel) stages[8]).getVectors().count());
        modelStatistics.put("Vector size", ((Word2VecModel) stages[8]).getVectorSize());
        modelStatistics.put("Reg parameter", ((LogisticRegressionModel) stages[9]).getRegParam());
        modelStatistics.put("Max iterations", ((LogisticRegressionModel) stages[9]).getMaxIter());

        printModelStatistics(modelStatistics);

        return modelStatistics;
    }

    @Override
    protected String getModelDirectory() {
        return getLyricsModelDirectoryPath() + "/logistic-regression/";
    }

    @Override
    public Map<String, Object> classifyFromCsv() {
        Dataset<Row> data = readLyricsFromCsv();

        StringIndexer labelIndexer = new StringIndexer()
                .setInputCol("genre")
                .setOutputCol(LABEL.getName())
                .setHandleInvalid("keep");

        StringIndexerModel labelIndexerModel = labelIndexer.fit(data);
        Dataset<Row> indexedData = labelIndexerModel.transform(data);

        Dataset<Row>[] splits = indexedData.randomSplit(new double[]{0.8, 0.2}, 42L);
        Dataset<Row> trainData = splits[0];
        Dataset<Row> testData = splits[1];

        System.out.println("Train size = " + trainData.count() + ", Test size = " + testData.count());

        Tokenizer tokenizer = new Tokenizer()
                .setInputCol("lyrics")
                .setOutputCol(WORDS.getName());

        StopWordsRemover stopWordsRemover = new StopWordsRemover()
                .setInputCol(WORDS.getName())
                .setOutputCol(FILTERED_WORDS.getName());

        Word2Vec word2Vec = new Word2Vec()
                .setInputCol(FILTERED_WORDS.getName())
                .setOutputCol("features")
                .setMinCount(1);

        LogisticRegression logisticRegression = new LogisticRegression()
                .setMaxIter(100)
                .setRegParam(0.01);

        IndexToString labelConverter = new IndexToString()
                .setInputCol("prediction")
                .setOutputCol("predictedGenre")
                .setLabels(labelIndexerModel.labels());

        Pipeline pipeline = new Pipeline().setStages(new PipelineStage[]{
                tokenizer, stopWordsRemover, word2Vec, logisticRegression, labelConverter
        });

        PipelineModel model = pipeline.fit(trainData);

        Dataset<Row> predictions = model.transform(testData);

        MulticlassClassificationEvaluator accuracyEvaluator = new MulticlassClassificationEvaluator()
                .setMetricName("accuracy");
        double accuracy = accuracyEvaluator.evaluate(predictions);

        MulticlassClassificationEvaluator f1Evaluator = new MulticlassClassificationEvaluator()
                .setMetricName("f1");
        double f1 = f1Evaluator.evaluate(predictions);

        saveModel(model, getGenreModelDirectory());
        saveModel(labelIndexerModel, getGenreModelDirectory() + "/label-indexer");

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("Test accuracy", accuracy);
        statistics.put("F1 score", f1);
        statistics.put("Genres", labelIndexerModel.labels());

        printModelStatistics(statistics);

        return statistics;
    }

    @Override
    public MultiClassGenrePrediction predictMultiClass(String unknownLyrics) {
        Dataset<Row> lyricsDataset = sparkSession.createDataset(
                Arrays.asList(unknownLyrics.split("\\r?\\n")), Encoders.STRING())
                .toDF("lyrics");

        PipelineModel model = loadGenrePipelineModel(getGenreModelDirectory());

        Dataset<Row> predictions = model.transform(lyricsDataset);
        Row predictionRow = predictions.first();

        String predictedGenre = predictionRow.getAs("predictedGenre");
        DenseVector probability = predictionRow.getAs("probability");

        StringIndexerModel labelIndexerModel = StringIndexerModel.load(getGenreModelDirectory() + "/label-indexer");
        String[] labels = labelIndexerModel.labels();

        Map<String, Double> probabilities = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            probabilities.put(labels[i], probability.apply(i));
        }

        System.out.println("\n------------------------------------------------");
        System.out.println("Predicted genre: " + predictedGenre);
        System.out.println("Probabilities: " + probabilities);
        System.out.println("------------------------------------------------\n");

        return new MultiClassGenrePrediction(predictedGenre, probabilities);
    }

    private String getGenreModelDirectory() {
        return getGenreModelDirectoryPath() + "/logistic-regression/";
    }

}
