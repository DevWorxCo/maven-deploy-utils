import uk.co.devworx.maven.deploy.GenerateMavenDeployScripts;
import uk.co.devworx.maven.deploy.OSTarget;
import uk.co.devworx.maven.deploy.ScriptType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * The easiest way to execute the Maven Deploy Script generator is
 * to simply take this template file a little and run it through Java
 */
public class Run_MavenDeployScriptsGen
{
	public static void main(String... args)
	{
		//Add in any group replacements you want to do in the pom file.
		//Note - this applies across the board to everything, including dependencies.
		Map<String, String> groupIdReplacements = new HashMap<>();
		groupIdReplacements.put("uk.co.devworx", "repl.uk.co.devworx");

		//Add in any groups you want to filter on. Leave empty if you want to match all
		Set<String> groupFilters = new HashSet<>();
		//groupFilters.add("uk.co.devworx");

		//Add in any groups you want to filter on. Leave empty if you want to match all
		Set<String> versionFilters = new HashSet<>();
		//versionFilters.add("1.0.1");

		//Specify the Path you want to scan over.
		Path jarRootScanDirectory = Paths.get("src/test/resources/mock");

		//Specify the format of the script to generate for
		OSTarget osTarget = OSTarget.Windows;

		//Do an install into the local repo / or do a remote deployment.
		ScriptType scriptType = ScriptType.LocalRepoInstall;

		//Specify the Target Directory to place the JARs, updated poms and deployment scripts.
		Path targetGen = Paths.get("target", "target-gen-example").toAbsolutePath();

		//Specify the location of your maven settings file.
		Path mavenSettingsFile = Paths.get(System.getenv("MAVEN_HOME"), "conf/settings.xml");

		//Specify the repository Id you would like to publish to
		String repositoryId = "MyRepositoryId";

		//Specify the url you would like to publish to
		String repoUrl = "https://my.repo/example/maven2";

		final GenerateMavenDeployScripts gen = new GenerateMavenDeployScripts(jarRootScanDirectory,
																			  osTarget,
																			  scriptType,
																			  groupIdReplacements,
																			  versionFilters,
																			  groupFilters);

		gen.generateScript(targetGen, mavenSettingsFile, repoUrl, repositoryId);

	}




}
