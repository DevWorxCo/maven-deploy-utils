package uk.co.devworx.maven.deploy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GenerateMavenDeployScriptsTest
{
	private static final Logger logger = LogManager.getLogger(GenerateMavenDeployScriptsTest.class);

	private final Map<String, String> groupIdReplacements = new HashMap<>();
	{
		groupIdReplacements.put("uk.co.devworx", "group-id-replaced");
	}
	private final Set<String> groupFilters = new HashSet<>();
	{
		groupFilters.add("uk.co.devworx");
	}

	private final Path jarRootScanDirectory = Paths.get("src/test/resources/mock-jars");

	private final GenerateMavenDeployScripts gen = new GenerateMavenDeployScripts(jarRootScanDirectory,
																	OSTarget.Windows,
																	ScriptType.LocalRepoInstall,
																	groupIdReplacements,
																	Collections.emptySet(),
																	groupFilters);

	@Test
	public void testGenerateScripts() throws Exception
	{
		String outputPathStr = "GenerateMavenDeployScriptsTest-" + UUID.randomUUID();
		Path outputPath = Paths.get("target",outputPathStr);
		Files.createDirectories(outputPath);
		Path settings = outputPath.resolve("settings.xml");

		gen.generateScript(outputPath, settings);

		Assertions.assertEquals(true, Files.exists(outputPath.resolve("000-execute-maven-script" + OSTarget.Windows.getFileExtension())));
	}

	@Test
	public void testPomExtractParsing() throws Exception
	{
		final List<PomFileExtract> extracts = gen.getPomFileExtracts();
		Assertions.assertEquals(1, extracts.size());

		Assertions.assertEquals("impala-query-parser", extracts.get(0).getArtefactId());
		Assertions.assertEquals("1.0-SNAPSHOT", extracts.get(0).getVersionId());
		Assertions.assertEquals("uk.co.devworx", extracts.get(0).getGroupId());
	}




}
