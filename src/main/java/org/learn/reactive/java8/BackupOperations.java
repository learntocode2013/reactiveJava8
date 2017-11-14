package org.learn.reactive.java8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BackupOperations {
	static final Logger logger = LoggerFactory.getLogger(BackupOperations.class);

	static public Supplier<Path> compress(Path srcFile) {
		return () -> {
			// Spend some time performing compression operation
			/*logger.info("{} compressing file {}...",
					Thread.currentThread().getName(),
					srcFile.toString());*/
			addLatency(10);
			return srcFile;
		};
	}

	static public Supplier<List<String>> exportRowsFromTable(String tableName) {
		return () -> {
			addLatency(3);
			return IntStream.rangeClosed(1,100)
					.mapToObj(each -> "row-" + each + "-data")
					.collect(Collectors.toList());
		};
	}

	static public Function<List<String>,Path> saveToFile(Path targetFilePath) {
		return rows -> {
			try {
				return Files.write(targetFilePath, rows);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		};
	}

	static public Consumer<Path> moveToStagingDir(Path stagingDir) {
		return cFile -> {
			try {
				Files.copy(cFile,stagingDir.resolve(cFile.getFileName()));
				/*logger.info("{} moved file {} to staging directory {}",
						Thread.currentThread().getName(),
						cFile.toString(),
						stagingDir.toString());*/
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
	}

	static public void addLatency(int durationInSeconds) {
		try
		{
			Thread.sleep(TimeUnit.SECONDS.toMillis(durationInSeconds));
		}
		catch (InterruptedException ignore)
		{
			// we would not do that in production code...
		}
	}
}
