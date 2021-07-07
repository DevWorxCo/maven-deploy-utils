package uk.co.devworx.maven.deploy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Some standalone generic pom file utils
 */
public class PomFileUtils
{
	private static final Logger logger = LogManager.getLogger(PomFileUtils.class);

	static final DocumentBuilderFactory docBuilderFactory;
	static final DocumentBuilder docBuilder;
	static final XPathFactory xPathFactory;

	static
	{
		try
		{
			docBuilderFactory = DocumentBuilderFactory.newInstance();
			docBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // prevent external loading.
			docBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			docBuilder = docBuilderFactory.newDocumentBuilder();
			xPathFactory = XPathFactory.newInstance();
		}
		catch(Exception e)
		{
			throw new RuntimeException("Unable to create an XML document factory. This is most unexpected : " + e);
		}
	}


	static Optional<Path> findEmbeddedPomFile(Path mavenDir)
	{
		final AtomicReference<Path> pathRef = new AtomicReference<>();
		try
		{
			Files.walkFileTree(mavenDir, new SimpleFileVisitor<Path>()
			{
				@Override public FileVisitResult visitFile(final Path file, BasicFileAttributes attrs) throws IOException
				{
					if(file.getFileName().toString().equals("pom.xml"))
					{
						pathRef.set(file);
						return FileVisitResult.TERMINATE;
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException e)
		{
			String msg = "Unable to find the embedded pom file from : " + mavenDir.toAbsolutePath() + " - got exception : " + e;
			logger.error(msg, e);
			throw new RuntimeException(msg, e);
		}
		return Optional.ofNullable(pathRef.get());
	}

}
