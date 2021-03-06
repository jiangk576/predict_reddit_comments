package redditprediction.pipeline

import org.apache.spark.sql.DataFrame
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.ml.feature.{VectorAssembler, HashingTF, IDF}

class TfidfFeaturePipeline(bucketsc: Int) extends FeaturePipeline {

  val buckets: Int = bucketsc

  val hashingTF = new HashingTF()
    .setInputCol("words")
    .setOutputCol("tf")
    .setNumFeatures(buckets)

  val idf = new IDF()
    .setInputCol("tf")
    .setOutputCol("words_features")

  override def getPipeline: Pipeline = {
    new Pipeline().setStages(Array(tokenizer, tokenCleaner, hashingTF, idf, 
                                   processor, bucketizer, 
                                   hourEncoder, assembler))
  }

  override def fit(dataset: DataFrame): TfidfFeaturePipelineModel = {
    new TfidfFeaturePipelineModel(getPipeline.fit(dataset))
  }
}

class TfidfFeaturePipelineModel(modelc: PipelineModel) extends FeaturePipelineModel(modelc) {
  override def getCountVectorizerModel = null
}
