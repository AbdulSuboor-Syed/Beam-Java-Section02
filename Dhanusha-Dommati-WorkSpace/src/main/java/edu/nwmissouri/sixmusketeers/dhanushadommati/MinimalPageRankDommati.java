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
import java.io.*;


import java.util.Collection;

import java.util.ArrayList;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.CollectionCoder;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.Max;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.TypeDescriptors;

public class MinimalPageRankDommati {
  
    static class Job1Finalizer extends DoFn<KV<String, Iterable<String>>, KV<String, RankedPage>> {
    @ProcessElement
    public void processElement(@Element KV<String, Iterable<String>> element,
        OutputReceiver<KV<String, RankedPage>> receiver) {
      Integer contributorVotes = 0;
      if (element.getValue() instanceof Collection) {
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
  
  static class Job2Mapper extends DoFn<KV<String, RankedPage>, KV<String, RankedPage>> {
    @ProcessElement
    public void processElement(@Element KV<String, RankedPage> element,
        OutputReceiver<KV<String, RankedPage>> receiver) {
      Integer votes = 0;
      ArrayList<VotingPage> voters = element.getValue().getVoters();
      if (voters instanceof Collection) {
        votes = ((Collection<VotingPage>)voters).size();
      }
      for (VotingPage vp : voters) {
        String pageName=vp.getname();
        Double pageRank=vp.getRank();
        String contributingPageName= element.getKey();
        Double contributingPageRank=element.getValue().getRank();
        VotingPage contributer=new VotingPage(contributingPageName, contributingPageRank, votes);
        ArrayList<VotingPage> arr =new ArrayList<VotingPage>();
        arr.add(contributer);
      receiver.output(KV.of(vp.getname(), new RankedPage(pageName,pageRank,arr)));
        
      }
    }
  }
  static class Job2Updater extends DoFn<KV<String, Iterable<RankedPage>>, KV<String, RankedPage>> {
    @ProcessElement
  public void processElement(@Element KV<String, Iterable<RankedPage>> element,
    OutputReceiver<KV<String, RankedPage>> receiver) {
      //Double dampingFactor = 0.85
      Double dampingFactor = 0.85;
      //Double updatedRank = (1 - dampingFactor) to start
      Double updatedRank = (1 - dampingFactor);
      //Create a  new array list for newVoters
      ArrayList<VotingPage> newVoters = new ArrayList<>();
      //For each pg in rankedPages, if pg isn't null, for each vp in pg.getVoters()
      for(RankedPage pg:element.getValue()){
        if (pg != null) {
          for(VotingPage vp:pg.getVoters()){
            newVoters.add(vp);
            updatedRank += (dampingFactor) * vp.getRank() / (double)vp.getVotes();

          }
        }
      }
      receiver.output(KV.of(element.getKey(),new RankedPage(element.getKey(), updatedRank, newVoters)));
  }
  }
  static class Job3 extends DoFn<KV<String, RankedPage>, KV<Double, String>> {
    @ProcessElement
    public void processElement(@Element KV<String, RankedPage> element,
        OutputReceiver<KV<Double, String>> receiver) {
      receiver.output(KV.of(element.getValue().getRank(), element.getKey()));
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
   PCollection<KV<String,String>> pCollectionKVList1 = dommatiMapper1(p,"go.md",folder);
   PCollection<KV<String,String>> pCollectionKVList2 = dommatiMapper1(p,"python.md",folder);
   PCollection<KV<String,String>> pCollectionKVList3 = dommatiMapper1(p,"java.md",folder);
   PCollection<KV<String,String>> pCollectionKVList4 = dommatiMapper1(p,"README.md",folder);
  // PCollection<KV<String,String>> pCollectionKVList5 = dommatiMapper1(p,"c.md",folder);

   
    PCollectionList<KV<String, String>> pCollList = PCollectionList.of(pCollectionKVList1).and(pCollectionKVList2).and(pCollectionKVList3).and(pCollectionKVList4);
    PCollection<KV<String, String>> l = pCollList.apply(Flatten.<KV<String,String>>pCollections());

    PCollection<KV<String, Iterable<String>>> grouped =l.apply(GroupByKey.<String, String>create());
    //PCollection<KV<String, Iterable<String>>> grouped =l.apply(GroupByKey.create());
     // Convert to a custom Value object (RankedPage) in preparation for Job 2
     PCollection<KV<String, RankedPage>> job2in = grouped.apply(ParDo.of(new Job1Finalizer()));
 
     //PCollection<String> pColLinkString = job2in.apply(MapElements.into(TypeDescriptors.strings()).via((mergeOut)->mergeOut.toString()));
     PCollection<KV<String, RankedPage>> job2out = null; 
     int iterations = 10;
     for (int i = 1; i <= iterations; i++) {
      job2out= runJob2Iteration(job2in);
      job2in =job2out;
     }

    PCollection<KV<Double, String>> jobThree = job2out.apply(ParDo.of(new Job3()));

    PCollection<KV<Double, String>> maxFinalRank = jobThree.apply(Combine.globally(Max.of(new RankedPage())));

    PCollection<String> pColLinkString = maxFinalRank.apply(MapElements.into(TypeDescriptors.strings()).via((mergeOut)->mergeOut.toString()));
    pColLinkString.apply(TextIO.write().to("dhanuuout"));  
    p.run().waitUntilFinish();
    
      
        
  }

  private static PCollection<KV<String, String>> dommatiMapper1(Pipeline p,String dataFile ,String dataFolder ) {
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
  
  /**
 * Run one iteration of the Job 2 Map-Reduce process
 * Notice how the Input Type to Job 2.
 * Matches the Output Type from Job 2.
 * How important is that for an iterative process?
 * 
 * @param kvReducedPairs - takes a PCollection<KV<String, RankedPage>> with
 *                       initial ranks.
 * @return - returns a PCollection<KV<String, RankedPage>> with updated ranks.
 */
private static PCollection<KV<String, RankedPage>> runJob2Iteration(
  PCollection<KV<String, RankedPage>> kvReducedPairs) {

PCollection<KV<String, RankedPage>> mappedKVs = kvReducedPairs.apply(ParDo.of(new Job2Mapper()));

// KV{README.md, README.md, 1.00000, 0, [java.md, 1.00000,1]}
// KV{README.md, README.md, 1.00000, 0, [go.md, 1.00000,1]}
// KV{java.md, java.md, 1.00000, 0, [README.md, 1.00000,3]}

 PCollection<KV<String, Iterable<RankedPage>>> reducedKVs = mappedKVs
     .apply(GroupByKey.<String, RankedPage>create());

// KV{java.md, [java.md, 1.00000, 0, [README.md, 1.00000,3]]}
// KV{README.md, [README.md, 1.00000, 0, [python.md, 1.00000,1], README.md,
// 1.00000, 0, [java.md, 1.00000,1], README.md, 1.00000, 0, [go.md, 1.00000,1]]}

 PCollection<KV<String, RankedPage>> updatedOutput = reducedKVs.apply(ParDo.of(new Job2Updater()));

// KV{README.md, README.md, 2.70000, 0, [java.md, 1.00000,1, go.md, 1.00000,1,
// python.md, 1.00000,1]}
// KV{python.md, python.md, 0.43333, 0, [README.md, 1.00000,3]}

//PCollection<KV<String, RankedPage>> updatedOutput = null;
return updatedOutput;
}

public static  void deleteFiles(){
  final File file = new File("./");
  for (File f : file.listFiles()){
    if(f.getName().startsWith("dhanuuout")){
      f.delete();
    }
  }
}

}

