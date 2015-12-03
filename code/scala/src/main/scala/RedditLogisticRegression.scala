package redditprediction

import org.apache.spark.sql.DataFrame
import org.apache.spark.ml.{Pipeline, Model, PipelineModel}
import org.apache.spark.ml.classification.{Classifier, OneVsRest, OneVsRestModel, LogisticRegression, LogisticRegressionModel}
import org.apache.spark.ml.feature.{CountVectorizerModel}
import org.apache.spark.ml.tuning.{ParamGridBuilder, TrainValidationSplit}
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator

import org.apache.spark.ml.feature.{CommentBucketizer, CommentBucketizerModel}

object RedditLogisticRegression extends RedditClassification {

  override def getClassifier: LogisticRegression = {
    new LogisticRegression()
      .setFeaturesCol("features")
      .setLabelCol("score_bucket");
  }

  override def explainTraining() = {
    val model = getModel 
    val multiLrModel = model.stages(2).asInstanceOf[OneVsRestModel];
    // bestModel.transform(test).show()
    
    // recover the cvModel from the Feature Pipeline
    val cvModel: CountVectorizerModel = 
      model.stages(0).asInstanceOf[PipelineModel]
           .stages(1).asInstanceOf[CountVectorizerModel];
    val bucketizerModel: CommentBucketizerModel =
      model.stages(1).asInstanceOf[CommentBucketizerModel];

    println("Extracting most important features"); 
    // display the most important feautres
    multiLrModel
      .models.zipWithIndex
      .foreach{ case (c, i) =>
        val lr = c.asInstanceOf[LogisticRegressionModel]
        println(s"""Classifier bucket ${bucketizerModel.getBucketRange(i)}, 
                    |Reg param: ${lr.getRegParam}""".stripMargin);
        lr.weights
          .toArray.toList.zipWithIndex
          .sortWith((a, b) => a._1 > b._1).take(5)
          .foreach{ case (w, i) => 
            if (i < cvModel.vocabulary.length) {
              println(s"\t${w}\t${cvModel.vocabulary(i)} (word ${i})");
            } else {
              println(s"\t${w}\tfeature ${i}");
            }
          };
      };
  }

  def trainWithRegularization(dataset: DataFrame, regs: Array[Double]) = {

    // Set up the pipeline
    val bucketizer: CommentBucketizer = new CommentBucketizer()
      .setScoreCol("score_double")
      .setScoreBucketCol("score_bucket")
    val lr = getClassifier
    val multiLr = new OneVsRest()
      .setClassifier(lr)
      .setFeaturesCol("features")
      .setLabelCol("score_bucket");
    val pipeline = new Pipeline()
      .setStages(Array(FeaturePipeline, bucketizer, multiLr));

    // Evaluation and tuning
    val evaluator = new MulticlassClassificationEvaluator()
      .setLabelCol("score_bucket")
      .setPredictionCol("prediction")
    val paramGrid = new ParamGridBuilder()
      .addGrid(lr.regParam, regs)
      .build();
    val trainValidationSplit = new TrainValidationSplit()
      .setEstimator(pipeline)
      .setEvaluator(evaluator)
      .setEstimatorParamMaps(paramGrid)
      .setTrainRatio(0.8);

    println("Training and tuning..."); 
    val allModels = trainValidationSplit.fit(dataset);
    val model = allModels.bestModel.asInstanceOf[PipelineModel];
    setModel(model)
  }
}
