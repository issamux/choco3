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

package solver.constraints.nary.sum;

import solver.constraints.IntConstraint;
import solver.variables.IntVar;
import util.ESat;
import util.tools.ArrayUtils;

/**
 * Constraint for Sum(x_i) = y
 *
 * @author Jean-Guillaume Fages
 * @since 21/07/13
 */
public class Sum extends IntConstraint<IntVar> {

    public Sum(IntVar[] x, IntVar y) {
        super(ArrayUtils.append(x,new IntVar[]{y}), y.getSolver());
		setPropagators(new PropSumEq(x,y));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ESat isSatisfied(int[] tuple) {
        int sum = 0;
		int n = tuple.length-1;
        for (int i = 0; i < n; i++) {
            sum += tuple[i];
        }
		return ESat.eval(sum == tuple[n]);
    }

    @Override
    public String toString() {
        StringBuilder sumst = new StringBuilder(20);
        for (int i = 0; i < vars.length-1; i++) {
            sumst.append(vars[i].getName()).append(" + ");
        }
        sumst.append(" = ");
        sumst.append(vars[vars.length - 1]);
        return sumst.toString();
    }
}
