package com.etheller.interpreter.ast.execution.instruction;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;

/**
 * A function that returns an integer may return a handle: the original game hands back the handle's id, which maps
 * use to keep handles in integer tables.
 */
public final class HandleIdAsIntegerInstruction implements JassInstruction {
	public static final HandleIdAsIntegerInstruction INSTANCE = new HandleIdAsIntegerInstruction();

	@Override
	public void run(final JassThread thread) {
		final JassValue value = thread.stackFrame.peek();
		if (value instanceof HandleJassValue) {
			thread.stackFrame.pop();
			final Object handle = ((HandleJassValue) value).getJavaValue();
			thread.stackFrame.push(IntegerJassValue.of(
					(handle == null) || (thread.globalScope.getHandleIdSource() == null) ? 0
							: thread.globalScope.getHandleIdSource().handleIdOf(handle)));
		}
	}

	@Override
	public int opCost() {
		return 0;
	}
}
