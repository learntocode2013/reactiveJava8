package org.learn.reactive.java8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class BackupTask {
	private final List<Path> _sourceFiles;
	private final List<Path> _sourceDirs;
	private final Path _stagingDir;
	private final Path _targetBackupArtifact;
	private final List<String> _dbTables;

	public BackupTask(List<Path> sourceFiles,
	                  List<Path> sourceDirs,
	                  List<String> dbTables,
	                  Path targetBackupArtifact,
	                  Path stagingDir) {
		this._sourceFiles = sourceFiles;
		this._sourceDirs = sourceDirs;
		this._targetBackupArtifact = targetBackupArtifact;
		this._dbTables = dbTables;
		this._stagingDir = stagingDir;
	}

	public Stream<Path> getSourceFileLocation() {
		// hits the file system to verify that file actually exists
		return _sourceFiles.stream().filter(path -> Files.exists(path));
	}

	public Stream<Path> getSourceDirLocation() {
		return _sourceDirs.stream().filter(path -> Files.isDirectory(path));
	}

	public Stream<String> getSourceTables() {
		// It might be a good idea to filter out invalid tables
		return _dbTables.stream();
	}

	public Path getStagingDirLocation() {
		return _stagingDir;
	}

	public Path getTargetArtifactLocation() {
		return _targetBackupArtifact;
	}
}
