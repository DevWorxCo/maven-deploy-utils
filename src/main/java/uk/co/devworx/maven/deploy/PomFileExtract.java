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
	private final Optional<Path> jarOrWarFile;

	private final Optional<Path> sourceJarFile;
	private final Optional<Path> pomFile;
	private final String groupId;
	private final String artefactId;
	private final String versionId;

	private final PomFileExtractType pomFileExtractType;

	private final byte[] pomFileData;

	@Override public String toString()
	{
		return "PomFileExtract{" + "jarFile=" + jarOrWarFile + ", groupId='" + groupId + '\'' + ", artefactId='" + artefactId + '\'' + ", versionId='" + versionId + '\'' + '}';
	}

	public static PomFileExtract create(Optional<Path> jarWarFilePathOpt,
										Optional<Path> pomFileOpt)
	{
		if (jarWarFilePathOpt.isPresent() == true && pomFileOpt.isPresent() == true)
		{
			throw new RuntimeException("You cannot specify both a jar/war File and parent pom file.");
		}
		if (jarWarFilePathOpt.isPresent() == false && pomFileOpt.isPresent() == false)
		{
			throw new RuntimeException("You must specify either jar/war File and parent pom file.");
		}

		if (jarWarFilePathOpt.isPresent())
		{
			return create_fromJarWar(jarWarFilePathOpt.get());
		}
		else
		{
			return create_fromPom(pomFileOpt.get());
		}

	}

	public Optional<Path> getSourceJarFile()
	{
		return sourceJarFile;
	}

	private static PomFileExtract create_fromJarWar(Path jarWarFilePath)
	{
		if (Files.exists(jarWarFilePath) == false || Files.isRegularFile(jarWarFilePath) == false || Files.isReadable(jarWarFilePath) == false)
		{
			throw new RuntimeException("Unable to read the file : " + jarWarFilePath);
		}

		try
		{
			Optional<byte[]> dataOpt = ZipUtils.getPomFileDataFromJar(jarWarFilePath);
			byte[] data = dataOpt.orElseThrow(() -> new RuntimeException("Could not extract a pom.xml file from the META-INF directory in the file : " + jarWarFilePath));

			try (BufferedReader pomFileReader = new BufferedReader(new StringReader(new String(data))))
			{
				Document document = PomFileUtils.docBuilder.parse(new InputSource(pomFileReader));
				XPath xpath = PomFileUtils.xPathFactory.newXPath();

				final String version = (String) xpath.compile("/project/version").evaluate(document, XPathConstants.STRING);
				final String groupId = (String) xpath.compile("/project/groupId").evaluate(document, XPathConstants.STRING);
				final String artefactId = (String) xpath.compile("/project/artifactId").evaluate(document, XPathConstants.STRING);

				//Found out the source jar
				final Optional<Path> sourceJar;
				String sourceJarName = jarWarFilePath.getFileName().toString();
				sourceJarName = sourceJarName.substring(0, sourceJarName.length() - 4);
				sourceJarName = sourceJarName + "-sources.jar";
				Path candidateSourceFile = jarWarFilePath.getParent().resolve(sourceJarName);
				if(Files.exists(candidateSourceFile))
				{
					sourceJar = Optional.of(candidateSourceFile);
				}
				else
				{
					sourceJar = Optional.empty();
				}

				return new PomFileExtract(Optional.of(jarWarFilePath), Optional.empty(), sourceJar, groupId, artefactId, version, data);
			}
		}
		catch (IOException | URISyntaxException | SAXException | XPathExpressionException e)
		{
			throw new RuntimeException("Unable to read the source file : " + jarWarFilePath + " - got the exception : " + e, e);
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
									  Optional.empty(),
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

	private PomFileExtract(Optional<Path> jarOrWarFile,
						   Optional<Path> pomFile,
						   Optional<Path> sourceJarFile,
						   String groupId,
						   String artefactId,
						   String versionId,
						   final byte[] pomFileData)
	{
		this.jarOrWarFile = jarOrWarFile;
		this.pomFile = pomFile;
		this.groupId = groupId;
		this.artefactId = artefactId;
		this.versionId = versionId;
		this.sourceJarFile = sourceJarFile;
		this.instanceId = counter.incrementAndGet();
		this.pomFileData = pomFileData;

		if(jarOrWarFile.isPresent() && pomFile.isPresent() == false)
        {
            pomFileExtractType = PomFileExtractType.JAR_FILE;
        }
		else if(jarOrWarFile.isPresent() == false && pomFile.isPresent())
        {
            pomFileExtractType = PomFileExtractType.PARENT_POM;
        }
		else
        {
            throw new RuntimeException("You cannot specify both a JAR File and POM File argument to this constructor. You specified : " + jarOrWarFile + " | " + pomFile);
        }

	}

    public Optional<Path> getPomFile()
    {
        return pomFile;
    }

    public Optional<Path> getJarOrWarFile()
	{
		return jarOrWarFile;
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
