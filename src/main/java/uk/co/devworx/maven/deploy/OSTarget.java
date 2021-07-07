package uk.co.devworx.maven.deploy;

public enum OSTarget
{
    Windows("call ", ".bat"),
    Unix("", ".sh");

    private final String prefix;
    private final String fileExt;

    OSTarget(String prefix, String fileExt)
    {
        this.prefix = prefix;
        this.fileExt = fileExt;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public String getFileExtension()
    {
        return fileExt;
    }
}
