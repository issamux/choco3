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
package solver.constraints.nary.nValue;

import gnu.trove.list.array.TIntArrayList;
import solver.constraints.Propagator;
import solver.constraints.PropagatorPriority;
import solver.exception.ContradictionException;
import solver.variables.EventType;
import solver.variables.IntVar;
import util.ESat;
import util.tools.ArrayUtils;

/**
 * Propagator for the atMostNValues constraint
 * The number of distinct values in the set of variables vars is at most equal to nValues
 * No level of consistency but better than BC in general (for enumerated domains with holes)
 *
 * @author Jean-Guillaume Fages
 */
public class PropNValues_Light extends Propagator<IntVar> {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    private TIntArrayList concernedValues;
    private int n;
    private int[] unusedValues, mate;
    private boolean allEnum; // all variables are enumerated

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    /**
     * Propagator for the NValues constraint
     * The number of distinct values among concerned values in the set of variables vars is exactly equal to nValues
     * No level of consistency for the filtering
     *
     * @param variables
     * @param concernedValues will be sorted!
     * @param nValues
     */
    public PropNValues_Light(IntVar[] variables, TIntArrayList concernedValues, IntVar nValues) {
        super(ArrayUtils.append(variables, new IntVar[]{nValues}), PropagatorPriority.QUADRATIC, true);
        n = variables.length;
        concernedValues.sort();
        this.concernedValues = concernedValues;
        unusedValues = new int[concernedValues.size()];
        mate = new int[concernedValues.size()];
        allEnum = true;
        for (int i = 0; i < n && allEnum; i++) {
            allEnum &= vars[i].hasEnumeratedDomain();
        }
    }

    //***********************************************************************************
    // PROPAGATION
    //***********************************************************************************

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        vars[n].updateLowerBound(0, aCause);
        vars[n].updateUpperBound(n, aCause);
        filter();
    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        forcePropagate(EventType.FULL_PROPAGATION);
    }

    private void filter() throws ContradictionException {
        int count = 0;
        int countMax = 0;
        int idx = 0;
        for (int i = concernedValues.size() - 1; i >= 0; i--) {
            boolean possible = false;
            boolean mandatory = false;
            mate[i] = -1;
            int value = concernedValues.get(i);
            for (int v = 0; v < n; v++) {
                if (vars[v].contains(value)) {
                    possible = true;
                    if (mate[i] == -1) {
                        mate[i] = v;
                    } else {
                        mate[i] = -2;
                        if (mandatory) {
                            break;
                        }
                    }
                    if (vars[v].instantiated()) {
                        mandatory = true;
                        if (mate[i] == -2) {
                            break;
                        }
                    }
                }
            }
            if (possible) {
                countMax++;
            }
            if (mandatory) {
                count++;
            } else {
                unusedValues[idx++] = value;
            }
        }
        // filtering cardinality variable
        vars[n].updateLowerBound(count, aCause);
        vars[n].updateUpperBound(countMax, aCause);
        // filtering decision variables
        if (count != countMax && vars[n].instantiated())
            if (count == vars[n].getUB()) {
                int val;
                for (int i = 0; i < idx; i++) {
                    val = unusedValues[i];
                    for (int v = 0; v < n; v++) {
                        vars[v].removeValue(val, aCause);
                    }
                }
                for (int i = idx - 1; i >= 0; i--) {
                    val = unusedValues[i];
                    for (int v = 0; v < n; v++) {
                        vars[v].removeValue(val, aCause);
                    }
                }
                if (allEnum) setPassive();
            } else if (countMax == vars[n].getLB()) {
                for (int i = concernedValues.size() - 1; i >= 0; i--) {
                    if (mate[i] >= 0) {
                        vars[mate[i]].instantiateTo(concernedValues.get(i), aCause);
                    }
                }
                if (allEnum) setPassive();
            }
    }

    //***********************************************************************************
    // INFO
    //***********************************************************************************

    @Override
    public int getPropagationConditions(int vIdx) {
        return EventType.INT_ALL_MASK();
    }

    @Override
    public ESat isEntailed() {
        int count = 0;
        int countMax = 0;
        for (int i = 0; i < concernedValues.size(); i++) {
            boolean possible = false;
            boolean mandatory = false;
            for (int v = 0; v < n; v++) {
                if (vars[v].contains(concernedValues.get(i))) {
                    possible = true;
                    if (vars[v].instantiated()) {
                        mandatory = true;
                        break;
                    }
                }
            }
            if (possible) {
                countMax++;
            }
            if (mandatory) {
                count++;
            }
        }
        if (count > vars[n].getUB()) {
            return ESat.FALSE;
        }
        if (countMax < vars[n].getLB()) {
            return ESat.FALSE;
        }
        if (count == countMax && vars[n].instantiated()) {
            return ESat.TRUE;
        }
        return ESat.UNDEFINED;
    }
}
