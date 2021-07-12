package uk.co.devworx.maven.deploy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.*;

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
                    if (file.getFileName().toString().endsWith(".jar") == false && file.getFileName().toString().endsWith(".pom") == false )
                    {
                        return FileVisitResult.CONTINUE;
                    }

                    String immediateParentName = file.getParent().getFileName().toString();
                    String grandParentName = file.getParent().getParent().getFileName().toString();

                    String expectedPathPom = grandParentName + "-" + immediateParentName + ".pom";
                    String expectedPathJar = grandParentName + "-" + immediateParentName + ".jar";

                        try
                        {
                            PomFileExtract fileExtract = null;
                            String realFileName = file.getFileName().toString();

                            logger.info("||| " + expectedPathJar  + " vs. " + realFileName + " || " + expectedPathJar.equals(realFileName) );

                            if(expectedPathJar.equals(realFileName))
                            {
                                fileExtract = PomFileExtract.create(Optional.of(file), Optional.empty());
                            }
                            else if(expectedPathPom.equals(realFileName))
                            {
                                fileExtract = PomFileExtract.create( Optional.empty(), Optional.of(file));
                            }
                            if(fileExtract != null)
                            {
                                if(!versionFilters.isEmpty() && !versionFilters.contains(fileExtract.getVersionId()))
                                {
                                    return FileVisitResult.CONTINUE;
                                }
                                if(!groupIdFilters.isEmpty() && !groupIdFilters.contains(fileExtract.getGroupId()))
                                {
                                    return FileVisitResult.CONTINUE;
                                }

                                logger.info("INCLUDED : " + file.toAbsolutePath());
                                allMatchingJarFiles.add(fileExtract);
                            }
                        }
                        catch (Exception e)
                        {
                            logger.info("SKIPPED : " + file.toAbsolutePath() + " - got the message : " + e.getMessage());
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

    public void generateScript(final Path outputDir,
                               final Path settingsXml,
                               final String url,
                               final String repositoryId)
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
            generateScriptForPomFileExtract(extract,
                                            outputScript,
                                            outputDir,
                                            settingsXml,
                                            url,
                                            repositoryId);
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
                                                 final Path settingsXml,
                                                 final String url,
                                                 final String repositoryId)
    {
        switch(extract.getPomFileExtractType())
        {

        case PARENT_POM:
            generateScriptForPomFileExtract_parentPom(extract,outputScript,outputDir,settingsXml,url,repositoryId);
            return;
        case JAR_FILE:
            generateScriptForPomFileExtract_jar(extract,outputScript,outputDir,settingsXml,url,repositoryId);
            return;
        default:
            throw new IllegalStateException("Unexpected value: " + extract.getPomFileExtractType() + " - you have not coded for this value.");
        }
    }

    private void generateScriptForPomFileExtract_jar(final PomFileExtract extract,
                                                     final StringBuilder outputScript,
                                                     final Path outputDir,
                                                     final Path settingsXml,
                                                     final String url,
                                                     final String repositoryId)
    {
        try
        {
            final String idPrefix = fileNameFormatter.format(extract.getInstanceId());
            //Generate the mavenless jar & extract the pom.

            final Path jarFile = outputDir.resolve(idPrefix + "-" + extract.getJarFile().get().getFileName());
            final Path pomFile = outputDir.resolve(idPrefix + "-" + extract.getJarFile().get().getFileName() + ".pom.xml");

            logger.info("Copying from " + extract.getJarFile().get().toAbsolutePath() + " to " + jarFile.toAbsolutePath());

            Files.copy(extract.getJarFile().get(), jarFile, StandardCopyOption.REPLACE_EXISTING);

            ZipUtils.removeMavenSubDirFromJar(jarFile);
            ZipUtils.replacePluginXMLInJar(jarFile, groupIdReplacements);

            String pomFileData = new String(extract.getPomFileData());
            Set<Map.Entry<String, String>> entries = groupIdReplacements.entrySet();
            for (Map.Entry<String, String> e : entries)
            {
                pomFileData = pomFileData.replace(">" + e.getKey() + "<", ">" + e.getValue() + "<");
            }

            Files.write(pomFile, pomFileData.getBytes(StandardCharsets.UTF_8));

            outputScript.append("\n");
            outputScript.append(osTarget.getPrefix() + "mvn " + scriptType.getMavenTarget() +
                                        " -Durl=\"" + url + "\"" +
                                        " -DrepositoryId=\"" + repositoryId + "\"" +
                                        " -Dfile=\"" + jarFile.toAbsolutePath() + "\"" +
                                        " -DpomFile=\"" + pomFile.toAbsolutePath() + "\"" +
                                        " -s \"" + settingsXml.toAbsolutePath() + "\"");

            outputScript.append("\n");
        }
        catch(Exception e)
        {
            String msg = "Unable to create a set of scripts for the POM File extract: \n" + extract + "\n" +
                    "Exception was : " + e;

            throw new RuntimeException(msg, e);
        }
    }

    private void generateScriptForPomFileExtract_parentPom(final PomFileExtract extract,
                                                           final StringBuilder outputScript,
                                                           final Path outputDir,
                                                           final Path settingsXml,
                                                           final String url,
                                                           final String repositoryId)
    {
        try
        {
            final String idPrefix = fileNameFormatter.format(extract.getInstanceId());
            final Path pomFile = outputDir.resolve(idPrefix + "-" + extract.getPomFile().get().getFileName());

            String pomFileDataRepl = new String(extract.getPomFileData());
            Set<Map.Entry<String, String>> entries = groupIdReplacements.entrySet();
            for (Map.Entry<String, String> e : entries)
            {
                pomFileDataRepl = pomFileDataRepl.replace(">" + e.getKey() + "<", ">" + e.getValue() + "<");
            }

            Files.write(pomFile, pomFileDataRepl.getBytes(StandardCharsets.UTF_8));

            outputScript.append("\n");
            outputScript.append(osTarget.getPrefix() + "mvn " + scriptType.getMavenTarget() +
                                        " -Durl=\"" + url + "\"" +
                                        " -DrepositoryId=\"" + repositoryId + "\"" +
                                        " -Dfile=\"" + pomFile.toAbsolutePath() + "\"" +
                                        " -DpomFile=\"" + pomFile.toAbsolutePath() + "\"" +
                                        " -s \"" + settingsXml.toAbsolutePath() + "\"");
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


