package uk.co.devworx.maven.deploy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZipUtilsTest
{

	public static final String JAR_LOCATION = "src/test/resources/mock-jars/uk/co/devworx/impala-query-parser/1.0-SNAPSHOT/impala-query-parser-1.0-SNAPSHOT.jar";

	@Test
	public void testRemoveMavenFromJar() throws Exception
	{
		String rndVal = UUID.randomUUID().toString();

		Path tmpTarget = Paths.get("target/" + rndVal + ".jar");
		Path tmpTargetExtr = Paths.get("target/" + rndVal);

		Files.createDirectories(tmpTargetExtr);

		Files.copy(Paths.get(JAR_LOCATION), tmpTarget);
		ZipUtils.removeMavenSubDirFromJar(tmpTarget);

		ZipUtils.extractZip(Files.newInputStream(tmpTarget), tmpTargetExtr);

		Path metaInf = tmpTargetExtr.resolve("META-INF/MANIFEST.MF");
		Path mavenDir = tmpTargetExtr.resolve("META-INF/maven");

		Assertions.assertEquals(true, Files.exists(metaInf));

		List<Path> mvnDirList = Files.list(mavenDir).filter(p -> Files.isDirectory(p)).collect(Collectors.toList());
		Assertions.assertEquals( 0, mvnDirList.size(), "The maven sub-dir in the JAR cannot contain any directories.");

	}

	@Test
	public void testReplacePluginXMLMavenInJar() throws Exception
	{
		String rndVal = UUID.randomUUID().toString();

		Path tmpTarget = Paths.get("target/" + rndVal + ".jar");
		Path tmpTargetExtr = Paths.get("target/" + rndVal);

		Files.createDirectories(tmpTargetExtr);

		Files.copy(Paths.get(JAR_LOCATION),tmpTarget);

		Map<String, String> grpReplace = new HashMap<>();
		grpReplace.put("dummy-pre-group-id", "dummy-post-group-id");

		ZipUtils.replacePluginXMLInJar(tmpTarget, grpReplace);
		ZipUtils.extractZip(Files.newInputStream(tmpTarget), tmpTargetExtr);

		Path metaInf = tmpTargetExtr.resolve("META-INF/MANIFEST.MF");
		Path mavenDir = tmpTargetExtr.resolve("META-INF/maven");
		Path pluginXml = tmpTargetExtr.resolve("META-INF/maven/plugin.xml");

		Assertions.assertEquals(true, Files.exists(metaInf));
		Assertions.assertEquals(true, Files.exists(mavenDir));
		Assertions.assertEquals(true, Files.exists(pluginXml));

		final String pluginXmlData = new String(Files.readAllBytes(pluginXml));

		Assertions.assertTrue(pluginXmlData.contains("dummy-post-group-id"));
		Assertions.assertFalse(pluginXmlData.contains("dummy-pre-group-id"));

	}



}
