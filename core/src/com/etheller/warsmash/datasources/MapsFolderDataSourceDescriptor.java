package com.etheller.warsmash.datasources;

import java.nio.file.Paths;
import java.util.Objects;

public class MapsFolderDataSourceDescriptor implements DataSourceDescriptor {
	private static final long serialVersionUID = 6081326115346722305L;
	private final String folderPath;

	public MapsFolderDataSourceDescriptor(final String folderPath) {
		this.folderPath = folderPath;
	}

	@Override
	public DataSource createDataSource() {
		return new MapsFolderDataSource(Paths.get(this.folderPath));
	}

	@Override
	public String getDisplayName() {
		return "Maps of folder: " + this.folderPath;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.folderPath);
	}

	@Override
	public boolean equals(final Object obj) {
		return (obj instanceof MapsFolderDataSourceDescriptor)
				&& Objects.equals(this.folderPath, ((MapsFolderDataSourceDescriptor) obj).folderPath);
	}

	@Override
	public DataSourceDescriptor duplicate() {
		return new MapsFolderDataSourceDescriptor(this.folderPath);
	}
}
