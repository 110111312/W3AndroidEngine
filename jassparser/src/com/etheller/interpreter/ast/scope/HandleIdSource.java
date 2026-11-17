package com.etheller.interpreter.ast.scope;

public interface HandleIdSource {
	int handleIdOf(Object handle);

	/**
	 * The handle that holds the id now, or null when none does. Text tags, lightnings, images and ubersplats are
	 * numbered apart from the other handles, so the type a script reads the id as tells which numbers are meant.
	 */
	Object handleOfId(int handleId, String typeName);
}
