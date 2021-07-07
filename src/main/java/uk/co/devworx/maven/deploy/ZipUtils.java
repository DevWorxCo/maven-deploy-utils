package uk.co.devworx.maven.deploy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Some useful utilities for dealing with zip files 
 *
 */
class ZipUtils
{
	private static final Logger logger = LogManager.getLogger(ZipUtils.class);


	public static final int DEFAULT_BUFFER = 1024 * 4;
	
	/**
	 * Zip up a directory of items. 
	 * 
	 * @param zipFileOutput
	 * @param zipFileInputDir
	 */
	public static void zipUpDirectory(File zipFileOutput, File zipFileInputDir) 
	{
		try
		{
			FileOutputStream fos = new FileOutputStream(zipFileOutput);
			ZipOutputStream zipOut = new ZipOutputStream(fos);
			zipFile(zipFileInputDir, zipFileInputDir.getName(), zipOut);
			zipOut.close();
			fos.close();
		}
		catch ( IOException e)
		{
			throw new RuntimeException("Could not write to the zip file :" + zipFileOutput);
		}
    }

    public static void removeMavenSubDirFromJar(Path jarFile) throws IOException, URISyntaxException
	{
		Map<String, String> zip_properties = new HashMap<>();
		zip_properties.put("create", "false");
		URI zip_disk = new URI("jar:" + jarFile.toUri());

		logger.info("Creating a zip file system for : " + zip_disk);

		/* Create ZIP file System */
		try (FileSystem zipfs = FileSystems.newFileSystem(zip_disk, zip_properties))
		{
			Path mavenPathInZip = zipfs.getPath("META-INF/maven");

			logger.info("Resolving : " + mavenPathInZip);
			logger.info("Deleting recursively : " + mavenPathInZip);
			FileUtls.deleteDir(mavenPathInZip);

		}

	}


	/**
	 * Extracts the specifed zip stream to the target directoru.
	 *
	 * @param inputZip
	 * @param destDir
	 * @throws IOException
	 */
	public static void extractZip(InputStream inputZip, Path destDir) throws IOException
	{
		extractZip(inputZip, destDir.toFile());
	}
	/**
	 * Extracts the specifed zip stream to the target directoru.
	 * 
	 * @param inputZip
	 * @param destDir
	 * @throws IOException
	 */
	public static void extractZip(InputStream inputZip, File destDir) throws IOException 
	{
        final byte[] buffer = getNewBuffer();
        
        try( InputStream ins = inputZip;
             BufferedInputStream bufr =  new BufferedInputStream(ins);
        	 ZipInputStream zis = new ZipInputStream(bufr);	
        	)
        {
        	ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null)
			{
				File newFile = newFile(	destDir, zipEntry);
				if(newFile.isDirectory() == false)
				{
					newFile.getParentFile().mkdirs();
					
					try(OutputStream fos = Files.newOutputStream(newFile.toPath());
					    BufferedOutputStream bous = new BufferedOutputStream(fos))
					{
						int len;
						while ((len = zis.read(buffer)) > 0)
						{
							fos.write(	buffer, 0, len);
						}
					}
				}
				
				zipEntry = zis.getNextEntry();
			}
			zis.closeEntry();
        }
    }
	
	private static byte[] getNewBuffer() 
	{
		return new byte[DEFAULT_BUFFER];
	}

	private static void zipFile(File fileToZip,
								String fileName,
								ZipOutputStream zipOut)
			throws IOException
	{
		if (fileToZip.isHidden())
		{
			return;
		}
		if (fileToZip.isDirectory())
		{
			if (fileName.endsWith("/"))
			{
				zipOut.putNextEntry(new ZipEntry(fileName));
				zipOut.closeEntry();
			} else
			{
				zipOut.putNextEntry(new ZipEntry(fileName + "/"));
				zipOut.closeEntry();
			}
			File[] children = fileToZip.listFiles();
			for (File childFile : children)
			{
				zipFile(childFile,
						fileName + "/" + childFile.getName(),
						zipOut);
			}
			return;
		}
		try(FileInputStream fis = new FileInputStream(fileToZip))
		{
			ZipEntry zipEntry = new ZipEntry(fileName);
			zipOut.putNextEntry(zipEntry);
			byte[] bytes = new byte[1024];
			int length;
			while ((length = fis.read(bytes)) >= 0)
			{
				zipOut.write(	bytes,
								0,
								length);
			}
		}
	}
	
	/**
	 * Returns an input stream to the first entry in the zip file. 
	 * Useful in cases where you have single entry zip file.
	 * 
	 * @param zipFile
	 * @return
	 */
	public static Optional<InputStream> getFirstZipEntryStream(File zipFile)
	{
		try
		{
			ZipFile zFile = new ZipFile(zipFile);
			Optional<? extends ZipEntry> firstEntry = zFile.stream().findFirst();
			if(firstEntry.isPresent() == false)
			{
				zFile.close();
				return Optional.empty();
			}
			ZipEntry entry = firstEntry.get();
			return Optional.of(new ZipEntryClosingInputStreamWrapper(zFile, zFile.getInputStream(entry)));
		} 
		catch ( IOException e)
		{
			throw new RuntimeException("Could not read the szip file :" + zipFile);
		}
	}

	
	private static class ZipEntryClosingInputStreamWrapper extends InputStream
	{
		private final Closeable zFile;
		private final InputStream underlying;
		
		public ZipEntryClosingInputStreamWrapper(Closeable zFile, InputStream underlying)
		{
			this.zFile = zFile;
			this.underlying = underlying;
		}

		@Override
		public int read() throws IOException
		{
			return underlying.read();
		}

		@Override
		public int hashCode()
		{
			return underlying.hashCode();
		}

		@Override
		public int read(byte[] b) throws IOException
		{
			return underlying.read(b);
		}

		@Override
		public boolean equals(Object obj)
		{
			return underlying.equals(obj);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			return underlying.read(b, off, len);
		}

		@Override
		public long skip(long n) throws IOException
		{
			return underlying.skip(n);
		}

		@Override
		public String toString()
		{
			return underlying.toString();
		}

		@Override
		public int available() throws IOException
		{
			return underlying.available();
		}

		@Override
		public void close() throws IOException
		{
			try
			{
				underlying.close();
			}
			finally
			{
				zFile.close();
			}
		}

		@Override
		public void mark(int readlimit)
		{
			underlying.mark(readlimit);
		}

		@Override
		public void reset() throws IOException
		{
			underlying.reset();
		}

		@Override
		public boolean markSupported()
		{
			return underlying.markSupported();
		}
		
	}

	private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException 
	{
		final String zipEntryName = zipEntry.getName();
		final boolean isDirectory = zipEntry.isDirectory();

		File destFile = new File(destinationDir, zipEntryName);

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) 
		{
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		if(isDirectory == true)
		{
			destFile.mkdirs();
		}

		return destFile;
	}
	
	
}








