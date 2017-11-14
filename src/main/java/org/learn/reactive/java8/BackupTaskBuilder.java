package org.learn.reactive.java8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class BackupTaskBuilder {
	private int _fileCount;
	private int _tableCount;
	private int _dirCount;
	private String _filePattern;
	private final Logger logger = LoggerFactory.getLogger(BackupTaskBuilder.class);

	public BackupTaskBuilder() { }

	public BackupTaskBuilder withFileCount(int fileCount) {
		this._fileCount = fileCount;
		return this;
	}

	public BackupTaskBuilder withTableCount(int tableCount) {
		this._tableCount = tableCount;
		return this;
	}

	public BackupTaskBuilder withDirCount(int dirCount) {
		this._dirCount = dirCount;
		return this;
	}

	public BackupTaskBuilder withFilePattern(String pattern) {
		this._filePattern = pattern;
		return this;
	}

	public BackupTask build() {
		Path fileRoot = Paths.get("/","tmp");
		String fileNamePattern = _filePattern.concat("_file-%d.dat");
		String dirNamePattern = _filePattern.concat("_dir-%d");
		String tableNamePattern = _filePattern.concat("_table-%d");
		Path artifactPath = fileRoot.resolve(_filePattern.concat("_backup.tar"));
		Path stagingPath = fileRoot.resolve("staging");

		final List<Path> srcFiles = IntStream.rangeClosed(1, _fileCount)
				.mapToObj(each -> String.format(fileNamePattern, each))
				.map(fName -> fileRoot.resolve(fName))
				.collect(toList());

		System.out.println("=======================================");
		logger.info("Files to be backed up: {}",srcFiles.toString());

		final List<Path> srcDirs = IntStream.rangeClosed(1, _fileCount)
				.mapToObj(each -> String.format(dirNamePattern, each))
				.map(fName -> fileRoot.resolve(fName))
				.collect(toList());

		logger.info("Directories to be backed up: {}",srcDirs.toString());

		final List<String> tables = IntStream.rangeClosed(1, _fileCount)
				.mapToObj(each -> String.format(tableNamePattern, each))
				.collect(toList());

		logger.info("Database tables to be backed up: {}",tables.toString());
		logger.info("Staging directory: {}",stagingPath.toString());
		logger.info("Artifact name: {}",artifactPath.toString());

		System.out.println("=======================================");

		srcDirs.stream().forEach(dir -> {
			try {
				Files.createDirectory(dir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		srcFiles.forEach(file -> {
			try {
				Files.createFile(file);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		try {
			Files.createDirectory(stagingPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new BackupTask(srcFiles,srcDirs,tables,artifactPath,stagingPath);
	}
}
