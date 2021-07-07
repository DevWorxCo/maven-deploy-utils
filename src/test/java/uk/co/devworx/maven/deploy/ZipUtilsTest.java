package uk.co.devworx.maven.deploy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class ZipUtilsTest
{
	@Test
	public void testRemoveMavenFromJar() throws Exception
	{
		String rndVal = UUID.randomUUID().toString();

		Path tmpTarget = Paths.get("target/" + rndVal + ".jar");
		Path tmpTargetExtr = Paths.get("target/" + rndVal);

		Files.createDirectories(tmpTargetExtr);

		Files.copy(Paths.get("src/test/resources/mock-jars/jar-parser-test.jar"),tmpTarget);
		ZipUtils.removeMavenSubDirFromJar(tmpTarget);

		ZipUtils.extractZip(Files.newInputStream(tmpTarget), tmpTargetExtr);

		Path metaInf = tmpTargetExtr.resolve("META-INF/MANIFEST.MF");
		Path mavenDir = tmpTargetExtr.resolve("META-INF/maven");

		Assertions.assertEquals(true, Files.exists(metaInf));
		Assertions.assertEquals(false, Files.exists(mavenDir));

	}



}
