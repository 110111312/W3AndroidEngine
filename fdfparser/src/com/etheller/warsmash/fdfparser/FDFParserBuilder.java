package com.etheller.warsmash.fdfparser;

public interface FDFParserBuilder {
	FDFParser build(String path);

	/** The parsed file; a builder that keeps parse trees returns the same tree for the same file. */
	default FDFParser.ProgramContext buildProgram(final String path) {
		return build(path).program();
	}
}
