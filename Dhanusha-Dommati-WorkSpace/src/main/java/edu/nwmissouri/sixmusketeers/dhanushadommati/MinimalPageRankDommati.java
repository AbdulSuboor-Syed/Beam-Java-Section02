/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.nwmissouri.sixmusketeers.dhanushadommati;
import java.util.Arrays;


import java.util.Collection;

import java.util.ArrayList;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.CollectionCoder;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.TypeDescriptors;

public class MinimalPageRankDommati {
   // DEFINE DOFNS
  // ==================================================================
  // You can make your pipeline assembly code less verbose by defining
  // your DoFns statically out-of-line.
  // Each DoFn<InputT, OutputT> takes previous output
  // as input of type InputT
  // and transforms it to OutputT.
  // We pass this DoFn to a ParDo in our pipeline.

  /**
   * DoFn Job1Finalizer takes KV(String, String List of outlinks) and transforms
   * the value into our custom RankedPage Value holding the page's rank and list
   * of voters.
   * 
   * The output of the Job1 Finalizer creates the initial input into our
   * iterative Job 2.
   */
  static class Job1Finalizer extends DoFn<KV<String, Iterable<String>>, KV<String, RankedPage>> {
    @ProcessElement
    public void processElement(@Element KV<String, Iterable<String>> element,
        OutputReceiver<KV<String, RankedPage>> receiver) {
      Integer contributorVotes = 0;
      if (element.getValue() instanceof CollectionCoder) {
        contributorVotes = ((Collection<String>) element.getValue()).size();
      }
      ArrayList<VotingPage> voters = new ArrayList<VotingPage>();
      for (String voterName : element.getValue()) {
        if (!voterName.isEmpty()) {
          voters.add(new VotingPage(voterName, contributorVotes));
        }
      }
      receiver.output(KV.of(element.getKey(), new RankedPage(element.getKey(), voters)));
    }
  }

  public static void main(String[] args) {

    // Create a PipelineOptions object. This object lets us set various execution
    // options for our pipeline, such as the runner you wish to use. This example
    // will run with the DirectRunner by default, based on the class path configured
    // in its dependencies.
    PipelineOptions options = PipelineOptionsFactory.create();

    Pipeline p = Pipeline.create(options);

   
    String folder="web04";
   PCollection<KV<String,String>> pCollectionKVList1 = batchuMapper1(p,"go.md",folder);
   PCollection<KV<String,String>> pCollectionKVList2 = batchuMapper1(p,"python.md",folder);
   PCollection<KV<String,String>> pCollectionKVList3 = batchuMapper1(p,"java.md",folder);
   PCollection<KV<String,String>> pCollectionKVList4 = batchuMapper1(p,"README.md",folder);
   PCollection<KV<String,String>> pCollectionKVList5 = batchuMapper1(p,"c.md",folder);

   
    PCollectionList<KV<String, String>> pCollList = PCollectionList.of(pCollectionKVList1).and(pCollectionKVList2).and(pCollectionKVList3).and(pCollectionKVList4).and(pCollectionKVList5);
    PCollection<KV<String, String>> l = pCollList.apply(Flatten.<KV<String,String>>pCollections());
    PCollection<KV<String, Iterable<String>>> grouped =l.apply(GroupByKey.create());
    PCollection<String> pColLinkString = grouped.apply(MapElements.into(TypeDescriptors.strings()).via((mergeOut)->mergeOut.toString()));
    pColLinkString.apply(TextIO.write().to("batchuout"));  
    p.run().waitUntilFinish();
    //batchuMapper1(p);
    //pcolLinks.apply(TextIO.write().to("batchuout"));

      
        
  }

  private static PCollection<KV<String, String>> batchuMapper1(Pipeline p,String dataFile ,String dataFolder ) {
    String dataPath = dataFolder + "/" + dataFile;
    //p.apply(TextIO.read().from("gs://apache-beam-samples/shakespeare/kinglear.txt"))

    PCollection<String> pcolInputLines=p.apply(TextIO.read().from(dataPath));
      // .apply(Filter.by((String line) -> !line.isEmpty()))
      // .apply(Filter.by((String line)->!line.equals(" ")))
      PCollection<String> pcolLinkLines= pcolInputLines.apply(Filter.by((String line)->line.startsWith("[")));
      PCollection<String> pcolLinks=pcolLinkLines.apply(MapElements
      .into(TypeDescriptors.strings())
      .via(
        (String linkline)->
        linkline.substring(linkline.indexOf("(")+1,linkline.length()-1)));

      // from README.md to Key Value Pairs
        PCollection<KV<String,String>> pColKeyValuePairs=pcolLinks.apply(MapElements

      .into(
        TypeDescriptors.kvs(
          TypeDescriptors.strings(),TypeDescriptors.strings()
          )
          )

      .via(
       outlink->KV.of(dataFile,outlink)

      ));
      return pColKeyValuePairs;

  }  
}