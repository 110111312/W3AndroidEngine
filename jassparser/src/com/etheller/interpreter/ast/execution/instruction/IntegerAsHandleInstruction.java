package com.etheller.interpreter.ast.execution.instruction;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;

/**
 * A function that returns a handle type may return an integer: the original game reads it as the id of a handle,
 * which maps use to get handles back out of integer tables.
 */
public final class IntegerAsHandleInstruction implements JassInstruction {
	private final HandleJassType type;

	public IntegerAsHandleInstruction(final HandleJassType type) {
		this.type = type;
	}

	@Override
	public void run(final JassThread thread) {
		final JassValue value = thread.stackFrame.peek();
		if (value instanceof IntegerJassValue) {
			thread.stackFrame.pop();
			final int handleId = value.visit(IntegerJassValueVisitor.getInstance());
			final Object handle = thread.globalScope.getHandleIdSource() == null ? null
					: thread.globalScope.getHandleIdSource().handleOfId(handleId, this.type.getName());
			thread.stackFrame.push(new HandleJassValue(this.type, handle));
		}
	}

	@Override
	public int opCost() {
		return 0;
	}
}
