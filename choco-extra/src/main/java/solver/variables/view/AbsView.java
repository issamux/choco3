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

package solver.variables.view;

import solver.ICause;
import solver.Solver;
import solver.exception.ContradictionException;
import solver.explanations.Explanation;
import solver.explanations.VariableState;
import solver.variables.EventType;
import solver.variables.IntVar;
import solver.variables.VariableFactory;
import solver.variables.delta.IIntDeltaMonitor;
import solver.variables.delta.IntDelta;
import solver.variables.delta.NoDelta;
import util.iterators.DisposableRangeIterator;
import util.iterators.DisposableValueIterator;


/**
 * declare an IntVar based on X, such |X|
 * <p/>
 * <p/>
 * "Views and Iterators for Generic Constraint Implementations" <br/>
 * C. Shulte and G. Tack.<br/>
 * Eleventh International Conference on Principles and Practice of Constraint Programming
 *
 * @author Charles Prud'homme
 * @since 09/08/11
 */
public final class AbsView extends IntView<IntDelta, IntVar<IntDelta>> {

    public AbsView(IntVar var, Solver solver) {
        super("|" + var.getName() + "|", var, solver);
    }

    @Override
    public IIntDeltaMonitor monitorDelta(ICause propagator) {
        var.createDelta();
        if (var.getDelta() == NoDelta.singleton) {
            return IIntDeltaMonitor.Default.NONE;
//            throw new UnsupportedOperationException();
        }
        return new ViewDeltaMonitor(var.monitorDelta(propagator), propagator) {

            @Override
            protected void filter() {
                int[] _values = values.toArray();
                values.clear();
                for (int i = 0; i < _values.length; i++) {
                    int v = _values[i];
                    if (!var.contains(-v)) {
                        boolean found = false;
                        for (int j = i + 1; !found && j < _values.length; j++) {
                            if (_values[j] == -v) {
                                found = true;
                            }
                        }
                        if (!found) {
                            values.add(v);
                        }
                    }
                }
            }

            @Override
            protected int transform(int value) {
                return Math.abs(value);
            }
        };
    }

    @Override
    public boolean instantiated() {
        if (var.instantiated()) {
            return true;
        } else {
            if (var.getDomainSize() == 2 && Math.abs(var.getLB()) == var.getUB()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean removeValue(int value, ICause cause) throws ContradictionException {
        assert cause != null;
        if (value < 0) {
            return false;
        }
        int inf = getLB();
        int sup = getUB();
        EventType evt = EventType.REMOVE;
        if (value == inf) {
            evt = EventType.INCLOW;
        } else if (value == sup) {
            evt = EventType.DECUPP;
        }
        boolean done = var.removeValue(-value, this);
        done |= var.removeValue(value, this);
        if (instantiated()) {
            evt = EventType.INSTANTIATE;
        }
        if (done) {
            notifyPropagators(evt, cause);
        }
        return done;
    }

    @Override
    public boolean removeInterval(int from, int to, ICause cause) throws ContradictionException {
        assert cause != null;
        if (from <= getLB()) {
            return updateLowerBound(to + 1, cause);
        } else if (getUB() <= to) {
            return updateUpperBound(from - 1, cause);
        } else {
            boolean done = var.removeInterval(-to, -from, this);
            done |= var.removeInterval(from, to, this);
            if (done) {
                notifyPropagators(EventType.REMOVE, cause);
            }
            return done;
        }
    }

    @Override
    public boolean instantiateTo(int value, ICause cause) throws ContradictionException {
        assert cause != null;
        int v = Math.abs(value);
        boolean done = var.updateLowerBound(-v, this);
        done |= var.updateUpperBound(v, this);
        EventType evt = EventType.DECUPP;
        if (var.hasEnumeratedDomain()) {
            done |= var.removeInterval(-v + 1, v - 1, this);
            evt = EventType.INSTANTIATE;
        }
        if (done) {
            notifyPropagators(evt, cause);
        }
        return done;
    }

    @Override
    public boolean updateLowerBound(int value, ICause cause) throws ContradictionException {
        assert cause != null;
        if (value <= 0) {
            return false;
        }
        boolean done = var.removeInterval(-value + 1, value - 1, this);
        if (done) {
            EventType evt = EventType.INCLOW;
            if (instantiated()) {
                evt = EventType.INSTANTIATE;
            }
            notifyPropagators(evt, cause);
        }
        return done;
    }

    @Override
    public boolean updateUpperBound(int value, ICause cause) throws ContradictionException {
        assert cause != null;
        boolean done = var.updateLowerBound(-value, this);
        done |= var.updateUpperBound(value, this);
        if (done) {
            EventType evt = EventType.DECUPP;
            if (instantiated()) {
                evt = EventType.INSTANTIATE;
            }
            notifyPropagators(evt, cause);
        }
        return done;
    }

    @Override
    public boolean contains(int value) {
        return var.contains(value) || var.contains(-value);
    }

    @Override
    public boolean instantiatedTo(int value) {
        if (var.contains(value) || var.contains(-value)) {
            return var.instantiated() ||
                    (var.getDomainSize() == 2 && Math.abs(var.getLB()) == var.getUB());          //<nj> fixed ABS bug
        }
        return false;
    }

    @Override
    public int getValue() {
        return getLB();
    }

    @Override
    public void explain(VariableState what, Explanation to) {
        var.explain(VariableState.DOM, to);
    }

    @Override
    public void explain(VariableState what, int val, Explanation to) {
        var.explain(what, val, to);
        var.explain(what, -val, to);
    }

    @Override
    public int getLB() {
        if (var.contains(0)) {
            return 0;
        }
        int elb = var.getLB();
        if (elb > 0) {
            return elb;
        }
        int eub = var.getUB();
        if (eub < 0) {
            return -eub;
        }
        int l = var.previousValue(0);
        int u = var.nextValue(0);
        return Math.min(-l, u);
    }

    @Override
    public int getUB() {
        int elb = var.getLB();
        int eub = var.getUB();
        int mm = -elb;
        if (elb < 0) {
            if (eub > 0 && eub > mm) {
                mm = eub;
            }
        } else {
            mm = eub;
        }
        return mm;
    }

    @Override
    public int nextValue(int v) {
        if (v < 0 && var.contains(0)) {
            return 0;
        }
        int l = var.previousValue(-v);
        if (l == Integer.MIN_VALUE) {
            l = Integer.MAX_VALUE;
        } else {
            l = Math.abs(l);
        }
        int u = var.nextValue(v);
        return Math.min(l, Math.abs(u));
    }

    @Override
    public int previousValue(int v) {
        if (v < 0) {
            return Integer.MIN_VALUE;
        }
        int l = var.nextValue(-v);
        if (l == Integer.MIN_VALUE) {
            l = Integer.MAX_VALUE;
        } else {
            l = Math.abs(l);
        }
        int u = var.previousValue(v);
        return Math.max(l, Math.abs(u));
    }

    @Override
    public String toString() {
        return "|" + this.var.toString() + "| = [" + getLB() + "," + getUB() + "]";
    }

    @Override
    public int getDomainSize() {
        int d = 0;
        int ub = getUB();
        for (int val = getLB(); val <= ub; val = nextValue(val)) {
            d++;
        }
        return d;
    }

    @Override
    public IntVar duplicate() {
        return new AbsView(this.var,getSolver());
    }

    @Override
    public DisposableValueIterator getValueIterator(boolean bottomUp) {
        if (_viterator == null || !_viterator.isReusable()) {
            _viterator = new DisposableValueIterator() {

                DisposableValueIterator u2l;
                DisposableValueIterator l2u;
                int vl2u;
                int vu2l;

                @Override
                public void bottomUpInit() {
                    l2u = var.getValueIterator(true);
                    u2l = var.getValueIterator(false);

                    super.bottomUpInit();
                    while (l2u.hasNext()) {
                        this.vl2u = l2u.next();
                        if (this.vl2u >= 0) break;
                    }
                    while (u2l.hasPrevious()) {
                        this.vu2l = u2l.previous();
                        if (this.vu2l <= 0) break;
                    }
                }

                @Override
                public void topDownInit() {
                    l2u = var.getValueIterator(true);
                    u2l = var.getValueIterator(false);

                    super.topDownInit();
                    if (l2u.hasNext()) {
                        this.vl2u = l2u.next();
                    }
                    if (u2l.hasPrevious()) {
                        this.vu2l = u2l.previous();
                    }
                }

                @Override
                public boolean hasNext() {
                    return this.vl2u < Integer.MAX_VALUE || this.vu2l > -Integer.MAX_VALUE;
                }

                @Override
                public boolean hasPrevious() {
                    return this.vl2u <= 0 || this.vu2l >= 0;
                }

                @Override
                public int next() {
                    int min = this.vl2u < -this.vu2l ? this.vl2u : -this.vu2l;
                    if (this.vl2u == min) {
                        if (this.l2u.hasNext()) {
                            this.vl2u = l2u.next();
                        } else {
                            this.vl2u = Integer.MAX_VALUE;
                        }
                    }
                    if (-this.vu2l == min) {
                        if (this.u2l.hasPrevious()) {
                            this.vu2l = u2l.previous();
                        } else {
                            this.vu2l = -Integer.MAX_VALUE;
                        }
                    }
                    return min;
                }

                @Override
                public int previous() {
                    int max = -this.vl2u > this.vu2l ? -this.vl2u : this.vu2l;
                    if (-this.vl2u == max && this.l2u.hasNext()) {
                        this.vl2u = this.l2u.next();
                    }
                    if (this.vu2l == max && this.u2l.hasPrevious()) {
                        this.vu2l = u2l.previous();
                    }
                    return max;
                }

                @Override
                public void dispose() {
                    super.dispose();
                    l2u.dispose();
                    u2l.dispose();
                }
            };
        }
        if (bottomUp) {
            _viterator.bottomUpInit();
        } else {
            _viterator.topDownInit();
        }
        return _viterator;
    }

    @Override
    public DisposableRangeIterator getRangeIterator(boolean bottomUp) {
        if (_riterator == null || !_riterator.isReusable()) {
            _riterator = new DisposableRangeIterator() {

                DisposableRangeIterator u2l;
                DisposableRangeIterator l2u;
                int ml2u;
                int Ml2u;
                int mu2l;
                int Mu2l;
                int min;
                int max;

                @Override
                public void bottomUpInit() {
                    l2u = var.getRangeIterator(true);
                    u2l = var.getRangeIterator(false);

                    super.bottomUpInit();
                    ml2u = Ml2u = mu2l = Mu2l = Integer.MAX_VALUE;

                    while (l2u.hasNext()) {
                        if (l2u.min() >= 0) {
                            ml2u = l2u.min();
                            Ml2u = l2u.max();
                            l2u.next();
                            break;
                        }
                        if (l2u.max() >= 0) {
                            ml2u = 0;
                            Ml2u = l2u.max();
                            l2u.next();
                            break;
                        }
                        l2u.next();
                    }
                    while (u2l.hasPrevious()) {
                        if (u2l.max() <= 0) {
                            Mu2l = -u2l.min();
                            mu2l = -u2l.max();
                            u2l.previous();
                            break;
                        }
                        if (u2l.min() <= 0) {
                            mu2l = 0;
                            Mu2l = -u2l.min();
                            u2l.previous();
                            break;
                        }
                        u2l.previous();
                    }
                    next();
                }

                @Override
                public void topDownInit() {
                    l2u = var.getRangeIterator(true);
                    u2l = var.getRangeIterator(false);

                    super.topDownInit();
                    ml2u = Ml2u = mu2l = Mu2l = Integer.MAX_VALUE;

                    if (l2u.hasNext()) {
                        if (l2u.max() <= 0) {
                            this.ml2u = -l2u.max();
                            this.Ml2u = -l2u.min();
                        } else if (l2u.min() <= 0) {
                            this.ml2u = 0;
                            this.Ml2u = -l2u.min();
                        }
                        l2u.next();
                    }
                    if (u2l.hasPrevious()) {
                        if (u2l.min() >= 0) {
                            this.mu2l = u2l.min();
                            this.Mu2l = u2l.max();
                        } else if (u2l.max() >= 0) {
                            this.mu2l = 0;
                            this.Mu2l = u2l.max();
                        }
                        u2l.previous();
                    }
                    previous();
                }

                @Override
                public boolean hasNext() {
                    return min < Integer.MAX_VALUE;
                }

                @Override
                public boolean hasPrevious() {
                    return min < Integer.MAX_VALUE;
                }

                @Override
                public void next() {
                    min = max = Integer.MAX_VALUE;
                    // disjoint ranges
                    if (Ml2u < mu2l - 1) {
                        min = ml2u;
                        max = Ml2u;
                        if (l2u.hasNext()) {
                            ml2u = l2u.min();
                            Ml2u = l2u.max();
                            l2u.next();
                        } else {
                            ml2u = Integer.MAX_VALUE;
                            Ml2u = Integer.MAX_VALUE;
                        }
                    } else if (Mu2l < ml2u - 1) {
                        min = mu2l;
                        max = Mu2l;
                        if (u2l.hasPrevious()) {
                            Mu2l = -u2l.min();
                            mu2l = -u2l.max();
                            u2l.previous();
                        } else {
                            mu2l = Integer.MAX_VALUE;
                            Mu2l = Integer.MAX_VALUE;
                        }
                    } else {
                        // we build the current range
                        if (Ml2u + 1 == mu2l) {
                            min = ml2u;
                            max = Mu2l;
                        } else if (Mu2l + 1 == ml2u) {
                            min = mu2l;
                            max = Ml2u;
                        } else {
                            min = ml2u < mu2l ? ml2u : mu2l;
                            max = Ml2u < Mu2l ? Ml2u : Mu2l;
                        }
                        boolean change;
                        do {
                            change = false;
                            if (min <= ml2u && ml2u <= max) {
                                max = max > Ml2u ? max : Ml2u;
                                if (l2u.hasNext()) {
                                    ml2u = l2u.min();
                                    Ml2u = l2u.max();
                                    l2u.next();
                                    change = true;
                                } else {
                                    ml2u = Integer.MAX_VALUE;
                                    Ml2u = Integer.MAX_VALUE;
                                }
                            }
                            if (min <= mu2l && mu2l <= max) {
                                max = max > Mu2l ? max : Mu2l;
                                if (u2l.hasPrevious()) {
                                    Mu2l = -u2l.min();
                                    mu2l = -u2l.max();
                                    u2l.previous();
                                    change = true;
                                } else {
                                    mu2l = Integer.MAX_VALUE;
                                    Mu2l = Integer.MAX_VALUE;
                                }
                            }
                        } while (change);
                    }
                }

                @Override
                public void previous() {
                    min = max = Integer.MAX_VALUE;
                    // disjoint ranges
                    if (ml2u > Mu2l + 1) {
                        min = ml2u;
                        max = Ml2u;
                        ml2u = Integer.MAX_VALUE;
                        Ml2u = Integer.MAX_VALUE;
                        if (l2u.hasNext()) {
                            //la: gerer le 0 et les autres cas
                            if (l2u.max() <= 0) {
                                this.ml2u = -l2u.max();
                                this.Ml2u = -l2u.min();
                            } else if (l2u.min() <= 0) {
                                this.ml2u = 0;
                                this.Ml2u = -l2u.min();
                            }
                            l2u.next();
                        }
                    } else if (Mu2l < ml2u - 1) {
                        min = mu2l;
                        max = Mu2l;
                        mu2l = Integer.MAX_VALUE;
                        Mu2l = Integer.MAX_VALUE;
                        if (u2l.min() >= 0) {
                            this.mu2l = u2l.min();
                            this.Mu2l = u2l.max();
                        } else if (u2l.max() >= 0) {
                            this.mu2l = 0;
                            this.Mu2l = u2l.max();
                        }
                    } else {
                        // we build the current range
                        if (Ml2u + 1 == mu2l) {
                            min = ml2u;
                            max = Mu2l;
                        } else if (Mu2l + 1 == ml2u) {
                            min = mu2l;
                            max = Ml2u;
                        } else {
                            min = ml2u > mu2l ? ml2u : mu2l;
                            max = Ml2u > Mu2l ? Ml2u : Mu2l;
                        }
                        boolean change;
                        do {
                            change = false;
                            if (min <= Ml2u && Ml2u <= max) {
                                min = min < ml2u ? min : ml2u;
                                ml2u = Integer.MAX_VALUE;
                                Ml2u = Integer.MAX_VALUE;
                                if (l2u.hasNext()) {
                                    if (l2u.max() <= 0) {
                                        this.ml2u = -l2u.max();
                                        this.Ml2u = -l2u.min();
                                    } else if (l2u.min() <= 0) {
                                        this.ml2u = 0;
                                        this.Ml2u = -l2u.min();
                                    }
                                    l2u.next();
                                    change = true;
                                }
                            }
                            if (min <= mu2l && mu2l <= max) {
                                min = min < mu2l ? min : mu2l;
                                mu2l = Integer.MAX_VALUE;
                                Mu2l = Integer.MAX_VALUE;
                                if (u2l.hasPrevious()) {
                                    if (u2l.min() >= 0) {
                                        this.mu2l = u2l.min();
                                        this.Mu2l = u2l.max();
                                    } else if (u2l.max() >= 0) {
                                        this.mu2l = 0;
                                        this.Mu2l = u2l.max();
                                    }
                                    u2l.previous();
                                    change = true;
                                }
                            }
                        } while (change);
                    }
                }

                @Override
                public int min() {
                    return min;
                }

                @Override
                public int max() {
                    return max;
                }

                @Override
                public void dispose() {
                    super.dispose();
                    l2u.dispose();
                    u2l.dispose();
                }
            };
        }
        if (bottomUp) {
            _riterator.bottomUpInit();
        } else {
            _riterator.topDownInit();
        }
        return _riterator;
    }

    @Override
    public void transformEvent(EventType evt, ICause cause) throws ContradictionException {
        if ((evt.mask & EventType.BOUND.mask) != 0) {
            if (instantiated()) { // specific case where DOM_SIZE = 2 and LB = -UB
                notifyPropagators(EventType.INSTANTIATE, this);
            } else { // otherwise, we do not know the previous values, so its hard to tell whether it is LB or UB mod
                notifyPropagators(EventType.BOUND, this);
            }
        } else {
            notifyPropagators(evt, this);
        }
    }
}
