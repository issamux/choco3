/**
 *  Copyright (c) 1999-2011, Ecole des Mines de Nantes
 *  All rights reserved.
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the Ecole des Mines de Nantes nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package memory.copy.store;

import memory.IStorage;
import memory.structure.Operation;

/**
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 14/05/13
 */
public class StoredOperationCopy implements IStorage {

    /**
     * Stack of values (former values that need be restored upon backtracking).
     */

    private Operation[] valueStack;


    /**
     * Points the level of the last entry.
     */

    private int currentLevel;


    /**
     * A stack of pointers (for each start of a world).
     */

    private int[] worldStartLevels;


    public StoredOperationCopy() {
        currentLevel = 0;
        valueStack = new Operation[64];
        worldStartLevels = new int[64];
    }

    @Override
    public void worldPush(int worldIndex) {
        if (worldIndex == worldStartLevels.length) {
            final int[] tmp = new int[worldIndex * 3 / 2 + 1];
            System.arraycopy(worldStartLevels, 0, tmp, 0, worldStartLevels.length);
            worldStartLevels = tmp;
        }
        worldStartLevels[worldIndex] = currentLevel;
    }

    @Override
    public void worldPop(int worldIndex) {
        final int wsl = worldStartLevels[worldIndex];
        while (currentLevel > wsl) {
            currentLevel--;
            valueStack[currentLevel].undo();
        }
    }

    @Override
    public void worldCommit(int worldIndex) {
    }

    /**
     * Reacts when a StoredInt is modified: push the former value & timestamp
     * on the stacks.
     */
    public void savePreviousState(Operation oldValue) {
        valueStack[currentLevel] = oldValue;
        currentLevel++;
        if (currentLevel == valueStack.length) {
            resizeUpdateCapacity();
        }
    }

    private void resizeUpdateCapacity() {
        final int newCapacity = ((valueStack.length * 3) / 2);
        // First, copy the stack of former values
        final Operation[] tmp2 = new Operation[newCapacity];
        System.arraycopy(valueStack, 0, tmp2, 0, valueStack.length);
        valueStack = tmp2;
    }
}
