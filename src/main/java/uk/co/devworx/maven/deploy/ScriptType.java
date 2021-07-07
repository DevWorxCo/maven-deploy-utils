package uk.co.devworx.maven.deploy;

/**
 * Whether the script generated is a local deploy or
 * a remote deploy.
 */
public enum ScriptType
{
	LocalRepoInstall("install:install-file"),
	RemoteRepoDeploy("deploy:deploy-file");

	private final String mvnTarget;

	private ScriptType(String mvnTarget)
	{
		this.mvnTarget = mvnTarget;
	}

	public String getMavenTarget()
	{
		return  mvnTarget;
	}
}
