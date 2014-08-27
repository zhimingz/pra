package edu.cmu.ml.rtw.pra.graphs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.MapUtil;
import edu.cmu.ml.rtw.util.Pair;

/**
 * Given an (already constructed) KB graph, convert it to a GraphChi graph suitable for using with
 * the PRA code, including the necessary dictionary files.  This just produces three files: an
 * edges.tsv file that will be directly input to GraphChi, and two dictionaries, a node dictionary
 * and an edge dictionary, that give the mapping from the integers in the edges.tsv file to node
 * and edge names.
 */
public class GraphCreator {
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(GraphCreator.class);

    private final List<RelationSet> relationSets;
    private final String outdir;

    public GraphCreator(List<RelationSet> relationSets, String outdir) {
        this.relationSets = relationSets;
        if (!outdir.endsWith("/")) {
            outdir += "/";
        }
        this.outdir = outdir;
    }

    public void createGraphChiRelationGraph() throws IOException {
        // Some preparatory stuff
        FileUtil.mkdirOrDie(outdir);

        new File(outdir + "graph_chi/").mkdirs();
        FileWriter intEdgeFile = new FileWriter(outdir + "graph_chi/edges.tsv");

        List<Pair<String, Map<String, List<String>>>> aliases = Lists.newArrayList();
        for (RelationSet relationSet : relationSets) {
            if (relationSet.getIsKb()) {
                String aliasRelation = relationSet.getAliasRelation();
                Map<String, List<String>> kbAliases = relationSet.getAliases();
                aliases.add(new Pair<String, Map<String, List<String>>>(aliasRelation, kbAliases));
            }
        }

        Dictionary nodeDict = new Dictionary();
        Dictionary edgeDict = new Dictionary();

        Set<String> seenNps = new HashSet<String>();
        Map<RelationSet, String> prefixes = getSvoPrefixes();
        int numEdges = 0;
        for (RelationSet relationSet : relationSets) {
            System.out.println("Adding edges to the graph from " + relationSet.getRelationFile());
            String prefix = null;
            if (prefixes != null) {
                prefix = prefixes.get(relationSet);
            }
            numEdges += relationSet.writeRelationEdgesToGraphFile(intEdgeFile,
                                                                  prefix,
                                                                  seenNps,
                                                                  aliases,
                                                                  nodeDict,
                                                                  edgeDict);
        }
        intEdgeFile.close();

        // Adding edges is now finished, and the dictionaries aren't getting any more entries, so
        // we can output them.
        outputDictionariesToDisk(nodeDict, edgeDict);

        // Now decide how many shards to do, based on the number of edges that are in the graph.
        int numShards = getNumShards(numEdges);
        FileWriter writer = new FileWriter(outdir + "num_shards.tsv");
        writer.write(numShards + "\n");
        writer.close();
    }

    ////////////////////////////////////////////////////////
    // Other boilerplate
    ////////////////////////////////////////////////////////

    public void outputDictionariesToDisk(Dictionary nodeDict,
                                         Dictionary edgeDict) throws IOException {
        System.out.println("Outputting dictionaries to disk");
        FileWriter nodeDictFile = new FileWriter(new File(outdir + "node_dict.tsv"));
        nodeDict.writeToWriter(nodeDictFile);
        nodeDictFile.close();

        FileWriter edgeDictFile = new FileWriter(new File(outdir + "edge_dict.tsv"));
        edgeDict.writeToWriter(edgeDictFile);
        edgeDictFile.close();
    }

    public int getNumShards(int numEdges) {
        if (numEdges < 50000000) {
            return 2;
        } else if (numEdges < 10000000) {
            return 3;
        } else if (numEdges < 40000000) {
            return 4;
        } else if (numEdges < 100000000) {
            return 5;
        } else if (numEdges < 150000000) {
            return 6;
        } else if (numEdges < 250000000) {
            return 7;
        } else if (numEdges < 350000000) {
            return 8;
        } else if (numEdges < 500000000) {
            return 9;
        } else {
            return 10;
        }
    }

    /**
     * Create a prefix for each SVO file as necessary, according to how they were embedded.
     *
     * If the edges are embedded, we need to differentiate the latent representations if they
     * were not made together.  That is, if we have two or more embedded SVO files, and they
     * have embeddings files that are _different_, that means that a +L1 edge from one and a
     * +L1 edge from another are not the same edge type.  So we add a prefix to the edge type
     * that is specific to each embedding.  This isn't a problem with KB edges vs. SVO edges,
     * because the "alias" relation assures that the two kinds of edges will never share the
     * same space.
     */
    public Map<RelationSet, String> getSvoPrefixes() {
        Map<String, List<RelationSet>> embeddingsToRels = Maps.newHashMap();
        for (RelationSet relationSet : relationSets) {
            if (relationSet.getIsKb()) continue;
            if (relationSet.getEmbeddingsFile() == null) continue;
            MapUtil.addValueToKeyList(embeddingsToRels,
                                      relationSet.getEmbeddingsFile(),
                                      relationSet);
        }
        Map<RelationSet, String> prefixes = null;
        if (embeddingsToRels.size() > 1) {
            prefixes = Maps.newHashMap();
            int prefix = 0;
            for (Map.Entry<String, List<RelationSet>> entry : embeddingsToRels.entrySet()) {
                prefix++;
                for (RelationSet relationSet : entry.getValue()) {
                    prefixes.put(relationSet, prefix + "-");
                }
            }
        }
        return prefixes;
    }

    static String usage = "GraphCreator [outdir] [relation_set_file]+";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println(usage);
            return;
        }
        String outdir = args[0];
        List<RelationSet> relationSets = Lists.newArrayList();
        for (int i = 1; i < args.length; i++) {
            relationSets.add(RelationSet.fromFile(args[i]));
        }
        new GraphCreator(relationSets, outdir).createGraphChiRelationGraph();
    }
}