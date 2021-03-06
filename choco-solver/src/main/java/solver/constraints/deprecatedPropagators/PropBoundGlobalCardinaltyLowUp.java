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
package solver.constraints.deprecatedPropagators;

import solver.exception.ContradictionException;
import solver.variables.EventType;
import solver.variables.IntVar;
import util.procedure.IntProcedure;

/**
 * A constraint to enforce BoundConsistency on a global cardinality
 * based on the implementation of :
 * C.-G. Quimper, P. van Beek, A. Lopez-Ortiz, A. Golynski, and S.B. Sadjad.
 * An efficient bounds consistency algorithm for the global cardinality constraint. CP-2003.
 * <br/>
 *
 * @author Hadrien Cambazard, Charles Prud'homme
 * @since 16/06/11
 */
@Deprecated
public class PropBoundGlobalCardinaltyLowUp extends PropBoundGlobalCardinality {

    private static final String MSG_INCONSISTENT = "inconsistent";

    public PropBoundGlobalCardinaltyLowUp(IntVar[] vars, int[] minOccurrences, int[] maxOccurrences,
                                          int firstCardValue, int lastCardValue) {
        super(vars, null, firstCardValue, lastCardValue);
        this.minOccurrences = minOccurrences;
        this.maxOccurrences = maxOccurrences;
//        l = new PartialSum(firstCardValue, this.range, minOccurrences);
//        u = new PartialSum(firstCardValue, this.range, maxOccurrences);
        rem_proc = new RemProc(this);
    }

    @Override
    int getMaxOcc(int i) {
        return maxOccurrences[i];
    }

    @Override
    int getMinOcc(int i) {
        return minOccurrences[i];
    }

    @Override
    public void initialize() throws ContradictionException {
        initBackDataStruct();
        for (int i = 0; i < vars.length; i++) {
            if (vars[i].instantiated()) {
                filterBCOnInst(vars[i].getValue());
            }
        }
        for (int i = 0; i < nbVars; i++) {
            for (int val = vars[i].getLB() + 1; val < vars[i].getUB(); val++) {
                if (!vars[i].contains(val))
                    filterBCOnRem(val);
            }
        }
        if (directInconsistentCount())
            contradiction(null, MSG_INCONSISTENT);

        l.compute(minOccurrences);
        u.compute(maxOccurrences);
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        if ((evtmask & EventType.FULL_PROPAGATION.mask) != 0) {
            initialize();
        }
        filter();
        for (int i = 0; i < idms.length; i++) {
            idms[i].unfreeze();
        }
    }

    @Override
    public void propagate(int varIdx, int mask) throws ContradictionException {
        if (EventType.isInstantiate(mask)) {
            int val = vars[varIdx].getValue();
            // if a value has been instantiated to its max number of occurrences
            // remove it from all variables
            val_minOcc[val - offset].add(1);
            filterBCOnInst(val);
        } else {
            if (EventType.isInclow(mask)) {
                if (!vars[varIdx].hasEnumeratedDomain()) {
                    filterBCOnInf(varIdx);
                }
            }
            if (EventType.isDecupp(mask)) {
                if (!vars[varIdx].hasEnumeratedDomain()) {
                    filterBCOnSup(varIdx);
                }
            }
            if (EventType.isRemove(mask)) {
                if (varIdx < nbVars) {
                    idms[varIdx].freeze();
                    idms[varIdx].forEach(rem_proc, EventType.REMOVE);
                    idms[varIdx].unfreeze();
                }
            }
        }
        forcePropagate(EventType.CUSTOM_PROPAGATION);
    }

    boolean directInconsistentCount() {
        for (int i = 0; i < range; i++) {
            if (val_maxOcc[i].get() < minOccurrences[i] ||
                    val_minOcc[i].get() > maxOccurrences[i])
                return true;
        }
        return false;
    }

    @Override
    void filter() throws ContradictionException {
        sortIt();

        // The variable domains must be inside the domain defined by
        // the lower bounds (l) and the upper bounds (u).
        assert (l.minValue() == u.minValue());
        assert (l.maxValue() == u.maxValue());
        assert (l.minValue() <= minsorted[0].var.getLB());
        assert (maxsorted[nbVars - 1].var.getUB() <= u.maxValue());
        assert (!directInconsistentCount());
        // Checks if there are values that must be assigned before the
        // smallest interval or after the last interval. If this is
        // the case, there is no solution to the problem
        // This is not an optimization since
        // filterLower{Min,Max} and
        // filterUpper{Min,Max} do not check for this case.

        if ((l.sum(l.minValue(), minsorted[0].var.getLB() - 1) > 0) ||
                (l.sum(maxsorted[getNbVars() - 1].var.getUB() + 1, l.maxValue()) > 0)) {
            contradiction(null, MSG_INCONSISTENT);
        }
        filterLowerMax();
        filterLowerMin();
        filterUpperMax();
        filterUpperMin();
    }

    void filterBCOnRem(int val) throws ContradictionException {
        int nbpos = val_maxOcc[val - offset].get();
        if (nbpos < getMinOcc(val - offset)) {
            this.contradiction(null, "inconsistent");
        } else if (nbpos == getMinOcc(val - offset)) {
            for (int j = 0; j < nbVars; j++) {
                if (vars[j].contains(val)) {
                    vars[j].instantiateTo(val, aCause);// CPRU not idempotent because data structure is maintained in awakeOnX methods
                }
            }
        }

    }

    private static class RemProc implements IntProcedure {

        private final PropBoundGlobalCardinaltyLowUp p;

        public RemProc(PropBoundGlobalCardinaltyLowUp p) {
            this.p = p;
        }

        @Override
        public void execute(int i) throws ContradictionException {
            int o = p.offset;
            p.val_maxOcc[i - o].add(-1);
            p.filterBCOnRem(i);
        }
    }
}
