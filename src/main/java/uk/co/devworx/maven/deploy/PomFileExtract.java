package uk.co.devworx.maven.deploy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a PomFile Extract item.
 */
public class PomFileExtract
{
	private static final Logger logger = LogManager.getLogger(PomFileExtract.class);

	private static final AtomicInteger counter = new AtomicInteger();

	private final int instanceId;
	private final Optional<Path> jarFile;
	private final Optional<Path> pomFile;
	private final String groupId;
	private final String artefactId;
	private final String versionId;

	private final PomFileExtractType pomFileExtractType;

	private final byte[] pomFileData;

	@Override public String toString()
	{
		return "PomFileExtract{" + "jarFile=" + jarFile + ", groupId='" + groupId + '\'' + ", artefactId='" + artefactId + '\'' + ", versionId='" + versionId + '\'' + '}';
	}

	public static PomFileExtract create(Optional<Path> jarFilePathOpt, Optional<Path> pomFileOpt)
	{
		if (jarFilePathOpt.isPresent() == true && pomFileOpt.isPresent() == true)
		{
			throw new RuntimeException("You cannot specify both a jar File and parent pom file.");
		}
		if (jarFilePathOpt.isPresent() == false && pomFileOpt.isPresent() == false)
		{
			throw new RuntimeException("You must specify either jar File and parent pom file.");
		}

		if (jarFilePathOpt.isPresent())
			return create_fromJar(jarFilePathOpt.get());
		else
			return create_fromPom(pomFileOpt.get());

	}

	private static PomFileExtract create_fromJar(Path jarFilePath)
	{
		if (Files.exists(jarFilePath) == false || Files.isRegularFile(jarFilePath) == false || Files.isReadable(jarFilePath) == false)
		{
			throw new RuntimeException("Unable to read the file : " + jarFilePath);
		}


		try
		{
			Optional<byte[]> dataOpt = ZipUtils.getPomFileDataFromJar(jarFilePath);
			byte[] data = dataOpt.orElseThrow(() -> new RuntimeException("Could not extract a pom.xml file from the META-INF directory in the file : " + jarFilePath));

			try (BufferedReader pomFileReader = new BufferedReader(new StringReader(new String(data))))
			{
				Document document = PomFileUtils.docBuilder.parse(new InputSource(pomFileReader));
				XPath xpath = PomFileUtils.xPathFactory.newXPath();

				final String version = (String) xpath.compile("/project/version").evaluate(document, XPathConstants.STRING);
				final String groupId = (String) xpath.compile("/project/groupId").evaluate(document, XPathConstants.STRING);
				final String artefactId = (String) xpath.compile("/project/artifactId").evaluate(document, XPathConstants.STRING);
				return new PomFileExtract(Optional.of(jarFilePath), Optional.empty(), groupId, artefactId, version, data);
			}
		}
		catch (IOException | URISyntaxException | SAXException | XPathExpressionException e)
		{
			throw new RuntimeException("Unable to read the source file : " + jarFilePath + " - got the exception : " + e, e);
		}
	}

    private static PomFileExtract create_fromPom(Path pomFilePath)
    {
        if (Files.exists(pomFilePath) == false || Files.isRegularFile(pomFilePath) == false || Files.isReadable(pomFilePath) == false)
        {
            throw new RuntimeException("Unable to read the file : " + pomFilePath);
        }

        Path tmpDir = null;
        try (InputStream pomFileReader = Files.newInputStream(pomFilePath))
        {
            Document document = PomFileUtils.docBuilder.parse(new InputSource(pomFileReader));
            XPath xpath = PomFileUtils.xPathFactory.newXPath();

            final String version = (String) xpath.compile("/project/version").evaluate(document, XPathConstants.STRING);
            final String groupId = (String) xpath.compile("/project/groupId").evaluate(document, XPathConstants.STRING);
            final String artefactId = (String) xpath.compile("/project/artifactId").evaluate(document, XPathConstants.STRING);
            final String packaging = (String) xpath.compile("/project/packaging").evaluate(document, XPathConstants.STRING);

            if(packaging.equals("pom") == false)
            {
                throw new RuntimeException("Have tried to read the POM file - " + pomFilePath + " - but seems the packaging for it is not of 'pom' - hence it is not a parent pom that needs to be deployed.");
            }

            return new PomFileExtract(Optional.empty(),
                                      Optional.of(pomFilePath),
                                      groupId, artefactId, version,
                                      Files.readAllBytes(pomFilePath));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to read the source file : " + pomFilePath + " - got the exception : " + e);
        }
        catch (SAXException | XPathExpressionException e)
        {
            throw new RuntimeException("Unable to parse the POM XML file : " + pomFilePath + " - got the exception : " + e, e);
        }
    }

	private PomFileExtract(Optional<Path> jarFile, Optional<Path> pomFile, String groupId, String artefactId, String versionId, final byte[] pomFileData)
	{
		this.jarFile = jarFile;
		this.pomFile = pomFile;
		this.groupId = groupId;
		this.artefactId = artefactId;
		this.versionId = versionId;
		this.instanceId = counter.incrementAndGet();
		this.pomFileData = pomFileData;

		if(jarFile.isPresent() && pomFile.isPresent() == false)
        {
            pomFileExtractType = PomFileExtractType.JAR_FILE;
        }
		else if(jarFile.isPresent() == false && pomFile.isPresent())
        {
            pomFileExtractType = PomFileExtractType.PARENT_POM;
        }
		else
        {
            throw new RuntimeException("You cannot specify both a JAR File and POM File argument to this constructor. You specified : " + jarFile + " | " + pomFile);
        }

	}

    public Optional<Path> getPomFile()
    {
        return pomFile;
    }

    public Optional<Path> getJarFile()
	{
		return jarFile;
	}

    public PomFileExtractType getPomFileExtractType()
    {
        return pomFileExtractType;
    }

    public int getInstanceId()
	{
		return instanceId;
	}

	public String getGroupId()
	{
		return groupId;
	}

	public String getArtefactId()
	{
		return artefactId;
	}

	public String getVersionId()
	{
		return versionId;
	}

	public byte[] getPomFileData()
	{
		return pomFileData;
	}
}
