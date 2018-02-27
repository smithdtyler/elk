/*******************************************************************************
 * Copyright (c) 2012, 2015 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.alg.layered.intermediate;

import java.util.List;
import java.util.ListIterator;

import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LLabel;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LNode.NodeType;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.options.InternalProperties;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.core.alg.ILayoutProcessor;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.LabelSide;
import org.eclipse.elk.core.util.IElkProgressMonitor;

/**
 * <p>Processor that removes the inserted center label dummies and places the labels on their
 * position.</p>
 * 
 * <dl>
 *   <dt>Precondition:</dt>
 *     <dd>a layered graph<dd>
 *     <dd>nodes are placed</dd>
 *     <dd>edges are routed</dd>
 *     <dd>center labels are represented by and attached to center label dummy nodes.</dd>
 *   <dt>Postcondition:</dt>
 *     <dd>labels are placed</dd>
 *     <dd>there are no dummy nodes of type
 *       {@link org.eclipse.elk.alg.layered.options.NodeType#LABEL}.</dd>
 *     <dd>center labels are attached to their original edges again.</dd>
 *   <dt>Slots:</dt>
 *     <dd>After phase 5.</dd>
 *   <dt>Same-slot dependencies:</dt>
 *     <dd>{@link HierarchicalPortOrthogonalEdgeRouter}</dd>
 * </dl>
 */
public final class LabelDummyRemover implements ILayoutProcessor<LGraph> {

    /**
     * {@inheritDoc}
     */
    public void process(final LGraph layeredGraph, final IElkProgressMonitor monitor) {
        monitor.begin("Label dummy removal", 1);
        
        double edgeLabelSpacing = layeredGraph.getProperty(LayeredOptions.SPACING_EDGE_LABEL);
        double labelLabelSpacing = layeredGraph.getProperty(LayeredOptions.SPACING_LABEL_LABEL);
        Direction layoutDirection = layeredGraph.getProperty(LayeredOptions.DIRECTION);
        
        for (Layer layer : layeredGraph.getLayers()) {
            // An iterator is necessary for traversing nodes, since dummy nodes might be removed
            ListIterator<LNode> nodeIterator = layer.getNodes().listIterator();
            
            while (nodeIterator.hasNext()) {
                LNode node = nodeIterator.next();
                
                if (node.getType() == NodeType.LABEL) {
                    // First, place labels on position of dummy node 
                    LEdge originEdge = (LEdge) node.getProperty(InternalProperties.ORIGIN);
                    double thickness = originEdge.getProperty(LayeredOptions.EDGE_THICKNESS).doubleValue();
                    boolean labelsBelowEdge = node.getProperty(InternalProperties.LABEL_SIDE) == LabelSide.BELOW;
                    
                    KVector currLabelPos = new KVector(node.getPosition());
                    
                    // If the labels are to be placed below their edge, we need to move the first label's
                    // position down a bit to respect the label spacing
                    if (labelsBelowEdge) {
                        currLabelPos.y += thickness + edgeLabelSpacing;
                    }
                    
                    // Calculate the space available for the placement of labels
                    KVector labelSpace = new KVector(
                            node.getSize().x,
                            node.getSize().y - thickness - edgeLabelSpacing);
                    
                    // Place labels
                    List<LLabel> representedLabels = node.getProperty(InternalProperties.REPRESENTED_LABELS);
                    
                    if (layoutDirection.isVertical()) {
                        placeLabelsForVerticalLayout(
                                representedLabels,
                                currLabelPos,
                                labelLabelSpacing,
                                labelSpace,
                                labelsBelowEdge);
                    } else {
                        placeLabelsForHorizontalLayout(
                                representedLabels,
                                currLabelPos,
                                labelLabelSpacing,
                                labelSpace);
                    }
                    
                    // Add represented labels back to the original edge
                    originEdge.getLabels().addAll(representedLabels);
                    
                    // Join the edges without adding unnecessary bend points
                    LongEdgeJoiner.joinAt(node, false);
                    
                    // Remove the node
                    nodeIterator.remove();
                }
            }
        }
        monitor.done();
    }

    private void placeLabelsForHorizontalLayout(final List<LLabel> labels, final KVector labelPos,
            final double labelSpacing, final KVector labelSpace) {
        
        for (LLabel label : labels) {
            label.getPosition().x = labelPos.x + (labelSpace.x - label.getSize().x) / 2.0;
            label.getPosition().y = labelPos.y;
            
            labelPos.y += label.getSize().y + labelSpacing;
        }
    }

    private void placeLabelsForVerticalLayout(final List<LLabel> labels, final KVector labelPos,
            final double labelSpacing, final KVector labelSpace, final boolean leftAligned) {
        
        for (LLabel label : labels) {
            label.getPosition().x = labelPos.x;
            label.getPosition().y = leftAligned
                    ? labelPos.y
                    : labelPos.y + labelSpace.y - label.getSize().y;
            
            labelPos.x += label.getSize().x + labelSpacing;
        }
    }

}
