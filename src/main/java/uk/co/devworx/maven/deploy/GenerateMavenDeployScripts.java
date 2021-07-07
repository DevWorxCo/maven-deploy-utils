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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class that will generate a command line set of scripts for a
 * set of JAR files for Maven (with optional group id replacements)
 *
 */
public class GenerateMavenDeployScripts
{
    private static final Logger logger = LogManager.getLogger(GenerateMavenDeployScripts.class);

    private final Path jarRootScanDirectory;
    private final OSTarget osTarget;
    private final ScriptType scriptType;
    private final Map<String, String> groupIdReplacements;
    private final Set<String> versionFilters;
    private final Set<String> groupIdFilters;

    public GenerateMavenDeployScripts(Path jarRootScanDirectory,
                                      OSTarget osTarget,
                                      ScriptType scriptType,
                                      Map<String, String> groupIdReplacements,
                                      Set<String> versionFilters,
                                      Set<String> groupIdFilters)
    {
        this.jarRootScanDirectory = jarRootScanDirectory;
        this.osTarget = osTarget;
        this.scriptType = scriptType;
        this.groupIdReplacements = groupIdReplacements;
        this.versionFilters = versionFilters;
        this.groupIdFilters = groupIdFilters;
    }

    public List<PomFileExtract> getPomFileExtracts()
    {
        final List<PomFileExtract> allMatchingJarFiles = new ArrayList<>();
        try
        {
            Files.walkFileTree(jarRootScanDirectory, new SimpleFileVisitor<Path>()
            {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    if (file.getFileName().toString().endsWith(".jar"))
                    {
                        try
                        {
                            PomFileExtract fileExtract = PomFileExtract.create(file);
                            if(!versionFilters.isEmpty() && !versionFilters.contains(fileExtract.getVersionId()))
                            {
                                return FileVisitResult.CONTINUE;
                            }
                            if(!groupIdFilters.isEmpty() && !groupIdFilters.contains(fileExtract.getGroupId()))
                            {
                                return FileVisitResult.CONTINUE;
                            }
                            allMatchingJarFiles.add(fileExtract);
                        }
                        catch (Exception e)
                        {
                            logger.info("Skipping : " + file.toAbsolutePath() + " - got the message : " + e.getMessage());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            logger.info("allMatchingJarFiles - total of " + allMatchingJarFiles.size() + " found.");

        }
        catch(Exception e)
        {
            String msg = "Unable to read the JAR Directory - got exception : " + e;
            throw new RuntimeException(msg, e);
        }

        return Collections.unmodifiableList(allMatchingJarFiles);
    }

    public void generateScript(final Path outputDir, final Path settingsXml)
    {
        try
        {
            if (Files.exists(outputDir) == false)
            {
                Files.createDirectories(outputDir);
            }
            if (Files.exists(outputDir) && !Files.isDirectory(outputDir))
            {
                throw new RuntimeException("The file you have specified : " + outputDir + " - already exists and is not a directory.");
            }
        }
        catch(IOException e)
        {
            throw new RuntimeException("Unable to create the output directory - " + outputDir + " - got the exception : " + e);
        }

        final List<PomFileExtract> pomFileExtracts = getPomFileExtracts();
        final StringBuilder outputScript = new StringBuilder();

        for(PomFileExtract extract : pomFileExtracts)
        {
            generateScriptForPomFileExtract(extract, outputScript, outputDir, settingsXml);
        }

        try
        {
            Path script = outputDir.resolve("000-execute-maven-script" + osTarget.getFileExtension());
            Files.write(script, outputScript.toString().getBytes(StandardCharsets.UTF_8));
            if (osTarget.equals(OSTarget.Unix))
            {
                script.toFile().setExecutable(true);
            }

            logger.info("Finished Writing the Deploy Pack to : " + script.toAbsolutePath());
        }
        catch(IOException e)
        {
            throw new RuntimeException("Unable to write the final execution script : " + e, e);
        }
    }

    private static final DecimalFormat fileNameFormatter = new DecimalFormat("000");

    private void generateScriptForPomFileExtract(final PomFileExtract extract,
                                                 final StringBuilder outputScript,
                                                 final Path outputDir,
                                                 final Path settingsXml)
    {
        try
        {
            final String idPrefix = fileNameFormatter.format(extract.getInstanceId());
           //Generate the mavenless jar & extract the pom.
            final Path jarFile = outputDir.resolve(idPrefix + "-" + extract.getJarFile().getFileName());
            final Path pomFile = outputDir.resolve(idPrefix + "-" + extract.getJarFile().getFileName() + ".pom.xml");

            logger.info("Copying from " + extract.getJarFile().toAbsolutePath() + " to " + jarFile.toAbsolutePath());

            Files.copy(extract.getJarFile(), jarFile, StandardCopyOption.REPLACE_EXISTING);

            ZipUtils.removeMavenSubDirFromJar(jarFile);

            String pomFileData = new String(extract.getPomFileData());
            Set<Map.Entry<String, String>> entries = groupIdReplacements.entrySet();
            for (Map.Entry<String, String> e : entries)
            {
                pomFileData = pomFileData.replace(">" + e.getKey() + "<", ">" + e.getValue() + "<");
            }

            Files.write(pomFile, pomFileData.getBytes(StandardCharsets.UTF_8));

            outputScript.append("\n");
            outputScript.append(osTarget.getPrefix() + "mvn " + scriptType.getMavenTarget() + " -Dfile=\"" + jarFile.toAbsolutePath() + "\" -DpomFile=\"" + pomFile.toAbsolutePath() + "\" -s " + settingsXml.toAbsolutePath());
            outputScript.append("\n");

        }
        catch(Exception e)
        {
            String msg = "Unable to create a set of scripts for the POM File extract: \n" + extract + "\n" +
                         "Exception was : " + e;

            throw new RuntimeException(msg, e);
        }
    }

}


class PomFileExtract
{
    private static final Logger logger = LogManager.getLogger(PomFileExtract.class);

    private static final AtomicInteger counter = new AtomicInteger();

    private final int instanceId;
    private final Path jarFile;
    private final String groupId;
    private final String artefactId;
    private final String versionId;

    private final byte[] pomFileData;

    @Override public String toString()
    {
        return "PomFileExtract{" + "jarFile=" + jarFile + ", groupId='" + groupId + '\'' + ", artefactId='" + artefactId + '\'' + ", versionId='" + versionId + '\'' + '}';
    }

    public static PomFileExtract create(Path jarFilePath)
    {
        if(Files.exists(jarFilePath) == false || Files.isRegularFile(jarFilePath) == false || Files.isReadable(jarFilePath) == false)
        {
            throw new RuntimeException("Unable to read the file : " + jarFilePath);
        }

        Path tmpDir = null;
        try(InputStream ins = Files.newInputStream(jarFilePath))
        {
            String tmpDirStr = System.getProperty("java.io.tmpdir");
            tmpDir = Paths.get(tmpDirStr, GenerateMavenDeployScripts.class.getSimpleName(),
                                          jarFilePath.getFileName().toString() + "-extract");

            if(Files.exists(tmpDir) == true)
            {
                logger.info("Deleting the previous temp directory : " + tmpDir);
                FileUtls.deleteDir(tmpDir);
            }
            logger.info("Creating the temp directory : " + tmpDir);

            Files.createDirectories(tmpDir);

            logger.info("Extracting " + jarFilePath + " to the temp directory : " + tmpDir);

            ZipUtils.extractZip(ins, tmpDir);

            Path mavenDir = tmpDir.resolve("META-INF/maven");
            if(Files.exists(mavenDir) == false)
            {
                throw new RuntimeException("The file you have specified - " + jarFilePath + " - does not have a META-INF/maven directory - hence is not a valid Maven file.");
            }

            Optional<Path> pomFileOpt = PomFileUtils.findEmbeddedPomFile(mavenDir);
            Path pomFile = pomFileOpt.orElseThrow(() -> new RuntimeException("Unable to locate the maven pom file from the specified JAR file : " + jarFilePath.toAbsolutePath()));

            try(BufferedReader pomFileReader = Files.newBufferedReader(pomFile))
            {
                Document document = PomFileUtils.docBuilder.parse(new InputSource(pomFileReader));
                XPath xpath = PomFileUtils.xPathFactory.newXPath();

                final String version = (String) xpath.compile("/project/version").evaluate(document, XPathConstants.STRING);
                final String groupId = (String) xpath.compile("/project/groupId").evaluate(document, XPathConstants.STRING);
                final String artefactId = (String) xpath.compile("/project/artifactId").evaluate(document, XPathConstants.STRING);

                return new PomFileExtract(jarFilePath, groupId, artefactId,version, Files.readAllBytes(pomFile));
            }
            catch(SAXException | XPathExpressionException e )
            {
                throw new RuntimeException("Unable to parse the POM XML file : " + pomFile  + " - got the exception : " + e, e);
            }

        }
        catch(IOException e)
        {
            throw new RuntimeException("Unable to read the source file : " + jarFilePath + " - got the exception : " + e);
        }
        finally
        {
            if(Files.exists(tmpDir) == true)
            {
                try
                {
                    FileUtls.deleteDir(tmpDir);
                }
                catch (IOException e)
                {
                    logger.warn("Unable to delete the temp directory : " + tmpDir + " | got the exception : " + e);
                }
            }
        }

    }


    private PomFileExtract(Path jarFile, String groupId, String artefactId, String versionId,  final byte[] pomFileData)
    {
        this.jarFile = jarFile;
        this.groupId = groupId;
        this.artefactId = artefactId;
        this.versionId = versionId;
        this.instanceId = counter.incrementAndGet();
        this.pomFileData = pomFileData;
    }

    public Path getJarFile()
    {
        return jarFile;
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

