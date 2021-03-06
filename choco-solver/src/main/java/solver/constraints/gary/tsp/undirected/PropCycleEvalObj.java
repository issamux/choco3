/*
 * Copyright (c) 1999-2012, Ecole des Mines de Nantes
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Ecole des Mines de Nantes nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package solver.constraints.gary.tsp.undirected;

import solver.constraints.Propagator;
import solver.constraints.PropagatorPriority;
import solver.exception.ContradictionException;
import solver.variables.EventType;
import solver.variables.IntVar;
import solver.variables.Variable;
import solver.variables.graph.UndirectedGraphVar;
import util.ESat;
import util.objects.setDataStructures.ISet;

/**
 * Compute the cost of the graph by summing edge costs
 * Supposes that each node must have two neighbors (cycle)
 * - For minimization problem
 */
public class PropCycleEvalObj extends Propagator {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    protected UndirectedGraphVar g;
    protected int n;
    protected IntVar sum;
    protected int[][] distMatrix;
    protected int[] replacementCost;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    public PropCycleEvalObj(UndirectedGraphVar graph, IntVar obj, int[][] costMatrix) {
        super(new Variable[]{graph, obj}, PropagatorPriority.LINEAR, true);
        g = graph;
        sum = obj;
        n = g.getEnvelopGraph().getNbNodes();
        distMatrix = costMatrix;
        replacementCost = new int[n];
    }

    //***********************************************************************************
    // METHODS
    //***********************************************************************************

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        propagate(0);
    }

    @Override
    public int getPropagationConditions(int vIdx) {
        return EventType.REMOVEARC.mask + EventType.ENFORCEARC.mask + EventType.DECUPP.mask;
    }

    @Override
    public ESat isEntailed() {
        int minSum = 0;
        int maxSum = 0;
        for (int i = 0; i < n; i++) {
            ISet env = g.getEnvelopGraph().getNeighborsOf(i);
            ISet ker = g.getKernelGraph().getNeighborsOf(i);
            for (int j = env.getFirstElement(); j >= 0; j = env.getNextElement()) {
                if (i <= j) {
                    maxSum += distMatrix[i][j];
                    if (ker.contain(j)) {
                        minSum += distMatrix[i][j];
                    }
                }
            }
        }
        if (maxSum < 0) {
            maxSum = Integer.MAX_VALUE;
        }
        if (minSum > sum.getUB() || maxSum < sum.getLB()) {
            return ESat.FALSE;
        }
        if (maxSum == minSum && sum.instantiated()) {
            return ESat.TRUE;
        }
        return ESat.UNDEFINED;
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        int minSum = 0;
        int maxSum = 0;
        for (int i = 0; i < n; i++) {
            minSum += findTwoBest(i);
            maxSum += findTwoWorst(i);
        }
        if (maxSum % 2 != 0) {
            maxSum++;
        }
        if (minSum % 2 != 0) {
            minSum--;
        }
        minSum /= 2;
        maxSum /= 2;
        if (maxSum < 0) {
            maxSum = Integer.MAX_VALUE;
        }
        sum.updateLowerBound(minSum, aCause);
        sum.updateUpperBound(maxSum, aCause);
        filter(minSum);
    }

    protected void filter(int minSum) throws ContradictionException {
        ISet succs;
        int delta = sum.getUB() - minSum;
        for (int i = 0; i < n; i++) {
            succs = g.getEnvelopGraph().getNeighborsOf(i);
            for (int j = succs.getFirstElement(); j >= 0; j = succs.getNextElement()) {
                if (i < j && !g.getKernelGraph().edgeExists(i, j)) {
                    if (replacementCost[i] == -1 || replacementCost[j] == -1) {
                        g.removeArc(i, j, aCause);
                    }
                    if ((2 * distMatrix[i][j] - replacementCost[i] - replacementCost[j]) / 2 > delta) {
                        g.removeArc(i, j, aCause);
                    }
                }
            }
        }
    }

    protected int findTwoBest(int i) throws ContradictionException {
        int mc1 = g.getKernelGraph().getNeighborsOf(i).getFirstElement();
        if (mc1 != -1) {
            int mc2 = g.getKernelGraph().getNeighborsOf(i).getNextElement();
            if (mc2 != -1) {
                replacementCost[i] = -1;
                return distMatrix[i][mc1] + distMatrix[i][mc2];
            }
            int cost = distMatrix[i][getBestNot(i, mc1)];
            replacementCost[i] = cost;
            return distMatrix[i][mc1] + cost;
        }
        mc1 = getBestNot(i, -2);
        int cost = distMatrix[i][getBestNot(i, mc1)];
        replacementCost[i] = cost;
        return distMatrix[i][mc1] + cost;
    }

    protected int getBestNot(int i, int not) throws ContradictionException {
        ISet nei = g.getEnvelopGraph().getNeighborsOf(i);
        int cost = -1;
        int idx = -1;
        for (int j = nei.getFirstElement(); j >= 0; j = nei.getNextElement()) {
            if (j != not && (idx == -1 || cost > distMatrix[i][j])) {
                idx = j;
                cost = distMatrix[i][j];
            }
        }
        if (idx == -1) {
            contradiction(g, "");
        }
        return idx;
    }

    protected int findTwoWorst(int i) throws ContradictionException {
        int mc1 = g.getKernelGraph().getNeighborsOf(i).getFirstElement();
        if (mc1 != -1) {
            int mc2 = g.getKernelGraph().getNeighborsOf(i).getNextElement();
            if (mc2 != -1) {
                return distMatrix[i][mc1] + distMatrix[i][mc2];
            }
            return distMatrix[i][mc1] + distMatrix[i][getWorstNot(i, mc1)];
        }
        mc1 = getWorstNot(i, -2);
        return distMatrix[i][mc1] + distMatrix[i][getWorstNot(i, mc1)];
    }

    protected int getWorstNot(int i, int not) throws ContradictionException {
        ISet nei = g.getEnvelopGraph().getNeighborsOf(i);
        int cost = -1;
        int idx = -1;
        for (int j = nei.getFirstElement(); j >= 0; j = nei.getNextElement()) {
            if (j != not && (idx == -1 || cost < distMatrix[i][j])) {
                idx = j;
                cost = distMatrix[i][j];
            }
        }
        if (idx == -1) {
            contradiction(g, "");
        }
        return idx;
    }

}
