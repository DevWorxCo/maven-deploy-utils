package uk.co.devworx.maven.deploy;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A lightweight utility, with no dependencies that contains some useful file manipulation 
 * code
 *
 */
class FileUtls
{
	private FileUtls() {}
	
	/**
	 * Recursively delete the directory and all its contents.
	 */
	public static void deleteDir(File directory) throws IOException
	{
		deleteDir(directory.toPath());
	}
	
	/**
	 * Recursively delete the directory and all its contents.
	 */
	public static void deleteDir(Path directory) throws IOException
	{
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	/**
	 * Recursively copies the contents of the given directory to the target directory.
	 */
	public static void copyDirContents(Path directory, Path targetDir) throws IOException
	{
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException 
			{
				String subPath = directory.relativize(file).toString();
				Path target = targetDir.resolve(  subPath );
				if(Files.exists(target.getParent()) == false )
				{
					Files.createDirectories(target.getParent());
				}
				Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
			
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException 
			{
				return FileVisitResult.CONTINUE;
			}
			
		});

	}
	
}














