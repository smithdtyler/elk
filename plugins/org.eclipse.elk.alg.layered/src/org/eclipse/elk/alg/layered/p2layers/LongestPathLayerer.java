/*******************************************************************************
 * Copyright (c) 2010, 2015 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.alg.layered.p2layers;

import java.util.List;

import org.eclipse.elk.alg.layered.LayeredPhases;
import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.intermediate.IntermediateProcessorStrategy;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.WideNodesStrategy;
import org.eclipse.elk.core.alg.ILayoutPhase;
import org.eclipse.elk.core.alg.LayoutProcessorConfiguration;
import org.eclipse.elk.core.util.IElkProgressMonitor;

/**
 * The most basic layering algorithm, which assign layers according to the
 * longest path to a sink.
 * 
 * <dl>
 *   <dt>Precondition:</dt><dd>the graph has no cycles</dd>
 *   <dt>Postcondition:</dt><dd>all nodes have been assigned a layer such that
 *     edges connect only nodes from layers with increasing indices</dd>
 * </dl>
 *
 * @author msp
 * @kieler.design 2012-08-10 chsch grh
 * @kieler.rating yellow 2012-11-13 review KI-33 by grh, akoc
 */
public final class LongestPathLayerer implements ILayoutPhase<LayeredPhases, LGraph> {
    
    /** intermediate processing configuration. */
    private static final LayoutProcessorConfiguration<LayeredPhases, LGraph> BASELINE_PROCESSING_CONFIGURATION =
        LayoutProcessorConfiguration.<LayeredPhases, LGraph>create()
            .addBefore(LayeredPhases.P1_CYCLE_BREAKING,
                    IntermediateProcessorStrategy.EDGE_AND_LAYER_CONSTRAINT_EDGE_REVERSER)
            .addBefore(LayeredPhases.P3_NODE_ORDERING, IntermediateProcessorStrategy.LAYER_CONSTRAINT_PROCESSOR);
    
    /** additional processor dependencies for handling big nodes. */
    private static final LayoutProcessorConfiguration<LayeredPhases, LGraph> BIG_NODES_PROCESSING_ADDITIONS_AGGRESSIVE =
            LayoutProcessorConfiguration.<LayeredPhases, LGraph>create()
                    .addBefore(LayeredPhases.P2_LAYERING, IntermediateProcessorStrategy.BIG_NODES_PREPROCESSOR)
                    .addBefore(LayeredPhases.P3_NODE_ORDERING,
                            IntermediateProcessorStrategy.BIG_NODES_INTERMEDIATEPROCESSOR)
                    .addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.BIG_NODES_POSTPROCESSOR);

    /** additional processor dependencies for handling big nodes after cross min. */
    private static final LayoutProcessorConfiguration<LayeredPhases, LGraph> BIG_NODES_PROCESSING_ADDITIONS_CAREFUL =
            LayoutProcessorConfiguration.<LayeredPhases, LGraph>create()
                    .addBefore(LayeredPhases.P4_NODE_PLACEMENT, IntermediateProcessorStrategy.BIG_NODES_SPLITTER)
                    .addAfter(LayeredPhases.P5_EDGE_ROUTING, IntermediateProcessorStrategy.BIG_NODES_POSTPROCESSOR);

    /** the layered graph to which layers are added. */
    private LGraph layeredGraph;
    /** map of nodes to their height in the layering. */
    private int[] nodeHeights;
    
    /**
     * {@inheritDoc}
     */
    public LayoutProcessorConfiguration<LayeredPhases, LGraph> getLayoutProcessorConfiguration(final LGraph graph) {
        // Basic strategy
        LayoutProcessorConfiguration<LayeredPhases, LGraph> strategy =
                LayoutProcessorConfiguration.createFrom(BASELINE_PROCESSING_CONFIGURATION);

        // Additional dependencies
        if (graph.getProperty(LayeredOptions.LAYERING_WIDE_NODES_ON_MULTIPLE_LAYERS) == WideNodesStrategy.AGGRESSIVE) {
            strategy.addAll(BIG_NODES_PROCESSING_ADDITIONS_AGGRESSIVE);
            
        } else if (graph.getProperty(LayeredOptions.LAYERING_WIDE_NODES_ON_MULTIPLE_LAYERS) 
                        == WideNodesStrategy.CAREFUL) {
            strategy.addAll(BIG_NODES_PROCESSING_ADDITIONS_CAREFUL);
        }
        
        return strategy;
    }
    
    /**
     * {@inheritDoc}
     */
    public void process(final LGraph thelayeredGraph, final IElkProgressMonitor monitor) {
        monitor.begin("Longest path layering", 1);
        
        layeredGraph = thelayeredGraph;
        List<LNode> nodes = layeredGraph.getLayerlessNodes();
        
        // initialize values required for the computation
        nodeHeights = new int[nodes.size()];
        int index = 0;
        for (LNode node : nodes) {
            // the node id is used as index for the nodeHeights array
            node.id = index;
            nodeHeights[index] = -1;
            index++;
        }
        
        // process all nodes
        for (LNode node : nodes) {
            visit(node);
        }
        
        // empty the list of unlayered nodes
        nodes.clear();
        
        // release the created resources
        this.layeredGraph = null;
        this.nodeHeights = null;
        
        monitor.done();
    }
    

    /**
     * Visit a node: if not already visited, find the longest path to a sink.
     * 
     * @param node node to visit
     * @return height of the given node in the layered graph
     */
    private int visit(final LNode node) {
        int height = nodeHeights[node.id];
        if (height >= 0) {
            // the node was already visited (the case height == 0 should never occur)
            return height;
        } else {
            int maxHeight = 1;
            for (LPort port : node.getPorts()) {
                for (LEdge edge : port.getOutgoingEdges()) {
                    LNode targetNode = edge.getTarget().getNode();
                    
                    // ignore self-loops
                    if (node != targetNode) {
                        int targetHeight = visit(targetNode);
                        maxHeight = Math.max(maxHeight, targetHeight + 1);
                    }
                }
            }
            putNode(node, maxHeight);
            return maxHeight;
        }
    }
    
    /**
     * Puts the given node into the layered graph, adding new layers as necessary.
     * 
     * @param node a node
     * @param height height of the layer where the node shall be added
     *          (height = number of layers - layer index)
     */
    private void putNode(final LNode node, final int height) {
        List<Layer> layers = layeredGraph.getLayers();
        
        // add layers so as to guarantee that number of layers >= height
        for (int i = layers.size(); i < height; i++) {
            layers.add(0, new Layer(layeredGraph));
        }
        
        // layer index = number of layers - height
        node.setLayer(layers.get(layers.size() - height));
        nodeHeights[node.id] = height;
    }

}
