package org.apache.maven.plugin.jdeps;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.lang.SystemUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Abstract Mojo for JDeps
 * 
 * @author Robert Scholte
 *
 */
public abstract class AbstractJDepsMojo
    extends AbstractMojo
{

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    @Parameter( defaultValue = "${project.build.directory}", readonly = true, required = true )
    private File outputDirectory;

    /**
     * Destination directory for DOT file output
     */
    @Parameter( property = "jdeps.dotOutput" )
    private File dotOutput;
    
//    @Parameter( defaultValue = "false", property = "jdeps.summaryOnly" )
//    private boolean summaryOnly;

    /**
     * <dl>
     *   <dt>package</dt><dd>Print package-level dependencies excluding dependencies within the same archive<dd/>
     *   <dt>class</dt><dd>Print class-level dependencies excluding dependencies within the same archive<dd/>
     *   <dt>&lt;empty&gt;</dt><dd>Print all class level dependencies. Equivalent to -verbose:class -filter:none.<dd/>
     * </dl>
     */
    @Parameter( property = "jdeps.verbose" )
    private String verbose;

//    /**
//     * A comma-separated list to find dependences in the given package (may be given multiple times)
//     */
//    @Parameter( property = "jdeps.pkgnames" )
//    private String packageNames;
//    
//    /**
//     * Finds dependences in packages matching pattern (-p and -e are exclusive)
//     */
//    @Parameter( property = "jdeps.regex" )
//    private String regex;
    
    /**
     * Restrict analysis to classes matching pattern. This option filters the list of classes to be analyzed. It can be
     * used together with <code>-p</code> and <code>-e</code> which apply pattern to the dependences
     */
    @Parameter( property = "jdeps.include" )
    private String include;
    
    /**
     * Restrict analysis to APIs i.e. dependences from the signature of public and protected members of public classes
     * including field type, method parameter types, returned type, checked exception types etc
     */
    @Parameter( defaultValue = "false", property = "jdeps.apionly" )
    private boolean apiOnly;
    
    /**
     * Show profile or the file containing a package
     */
    @Parameter( defaultValue = "false", property = "jdeps.profile" )
    private boolean profile;
    
    /**
     * Recursively traverse all dependencies
     */
    @Parameter( defaultValue = "false", property = "jdeps.recursive" )
    private boolean recursive;

    @Component
    private ToolchainManager toolchainManager;
    
    protected MavenProject getProject()
    {
        return project;
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        String jExecutable;
        try
        {
            jExecutable = getJDepsExecutable();
        }
        catch ( IOException e )
        {
            throw new MojoFailureException( "Unable to find jdeps command: " + e.getMessage(), e );
        }

//      Synopsis
//      jdeps [options] classes ...
        Commandline cmd = new Commandline();
        cmd.setExecutable( jExecutable );
        addJDepsOptions( cmd );
        addJDepsClasses( cmd );
        
        executeJavadocCommandLine( cmd, outputDirectory );
    }

    protected void addJDepsOptions( Commandline cmd )
        throws MojoFailureException
    {
        if ( dotOutput != null )
        {
            cmd.createArg().setValue( "-dotoutput" );
            cmd.createArg().setFile( dotOutput );
        }
        
//        if ( summaryOnly )
//        {
//            cmd.createArg().setValue( "-s" );
//        }
        
        if ( verbose != null )
        {
            if ( "class".equals( verbose ) )
            {
                cmd.createArg().setValue( "-verbose:class" );
            }
            else if ( "package".equals( verbose ) )
            {
                cmd.createArg().setValue( "-verbose:package" );
            }
            else
            {
                cmd.createArg().setValue( "-v" );
            }
        }
        
        try
        {
            cmd.createArg().setValue( "-cp" );
            cmd.createArg().setValue( getClassPath() );
        }
        catch ( DependencyResolutionRequiredException e )
        {
            throw new MojoFailureException( e.getMessage(), e );
        }
        
//        if ( packageNames != null )
//        {
//            for ( String pkgName : packageNames.split( "[,:;]" ) )
//            {
//                cmd.createArg().setValue( "-p" );
//                cmd.createArg().setValue( pkgName );
//            }
//        }
//        
//        if ( regex != null )
//        {
//            cmd.createArg().setValue( "-e" );
//            cmd.createArg().setValue( regex );
//        }

        if ( include != null )
        {
            cmd.createArg().setValue( "-include" );
            cmd.createArg().setValue( include );
        }

        if ( profile )
        {
            cmd.createArg().setValue( "-P" );
        }

        if ( apiOnly )
        {
            cmd.createArg().setValue( "-apionly" );
        }
        
        if ( recursive )
        {
            cmd.createArg().setValue( "-R" );
        }
        
        cmd.createArg().setValue( "-version" );
    }
    
    protected void addJDepsClasses( Commandline cmd )
    {
        // <classes> can be a pathname to a .class file, a directory, a JAR file, or a fully-qualified class name.
        cmd.createArg().setFile( new File( getClassesDirectory() ) );
    }

    private String getJDepsExecutable() throws IOException
    {
        Toolchain tc = getToolchain();

        String jdepsExecutable = null;
        if ( tc != null )
        {
            jdepsExecutable = tc.findTool( "jdeps" );
        }

        String jdepsCommand = "jdeps" + ( SystemUtils.IS_OS_WINDOWS ? ".exe" : "" );

        File jdepsExe;

        if ( StringUtils.isNotEmpty( jdepsExecutable ) )
        {
            jdepsExe = new File( jdepsExecutable );

            if ( jdepsExe.isDirectory() )
            {
                jdepsExe = new File( jdepsExe, jdepsCommand );
            }

            if ( SystemUtils.IS_OS_WINDOWS && jdepsExe.getName().indexOf( '.' ) < 0 )
            {
                jdepsExe = new File( jdepsExe.getPath() + ".exe" );
            }

            if ( !jdepsExe.isFile() )
            {
                throw new IOException( "The jdeps executable '" + jdepsExe
                    + "' doesn't exist or is not a file." );
            }
            return jdepsExe.getAbsolutePath();
        }

        // ----------------------------------------------------------------------
        // Try to find javadocExe from System.getProperty( "java.home" )
        // By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
        // should be in the JDK_HOME
        // ----------------------------------------------------------------------
        // For IBM's JDK 1.2
        if ( SystemUtils.IS_OS_AIX )
        {
            jdepsExe =
                new File( SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "sh", jdepsCommand );
        }
        // For Apple's JDK 1.6.x (and older?) on Mac OSX
        // CHECKSTYLE_OFF: MagicNumber
        else if ( SystemUtils.IS_OS_MAC_OSX && SystemUtils.JAVA_VERSION_FLOAT < 1.7f )
        // CHECKSTYLE_ON: MagicNumber
        {
            jdepsExe = new File( SystemUtils.getJavaHome() + File.separator + "bin", jdepsCommand );
        }
        else
        {
            jdepsExe =
                new File( SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "bin", jdepsCommand );
        }

        // ----------------------------------------------------------------------
        // Try to find javadocExe from JAVA_HOME environment variable
        // ----------------------------------------------------------------------
        if ( !jdepsExe.exists() || !jdepsExe.isFile() )
        {
            Properties env = CommandLineUtils.getSystemEnvVars();
            String javaHome = env.getProperty( "JAVA_HOME" );
            if ( StringUtils.isEmpty( javaHome ) )
            {
                throw new IOException( "The environment variable JAVA_HOME is not correctly set." );
            }
            if ( ( !new File( javaHome ).getCanonicalFile().exists() )
                || ( new File( javaHome ).getCanonicalFile().isFile() ) )
            {
                throw new IOException( "The environment variable JAVA_HOME=" + javaHome
                    + " doesn't exist or is not a valid directory." );
            }

            jdepsExe = new File( javaHome + File.separator + "bin", jdepsCommand );
        }

        if ( !jdepsExe.getCanonicalFile().exists() || !jdepsExe.getCanonicalFile().isFile() )
        {
            throw new IOException( "The jdeps executable '" + jdepsExe
                + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable." );
        }

        return jdepsExe.getAbsolutePath();
    }
    
    private void executeJavadocCommandLine( Commandline cmd, File javadocOutputDirectory ) throws MojoExecutionException
    {
        if ( getLog().isDebugEnabled() )
        {
            // no quoted arguments
            getLog().debug( CommandLineUtils.toString( cmd.getCommandline() ).replaceAll( "'", "" ) );
        }

        CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();
        CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
        try
        {
            int exitCode = CommandLineUtils.executeCommandLine( cmd, out, err );

            String output = ( StringUtils.isEmpty( out.getOutput() ) ? null : '\n' + out.getOutput().trim() );

            if ( exitCode != 0 )
            {
                if ( StringUtils.isNotEmpty( output ) )
                {
                    getLog().info( output );
                }

                StringBuilder msg = new StringBuilder( "\nExit code: " );
                msg.append( exitCode );
                if ( StringUtils.isNotEmpty( err.getOutput() ) )
                {
                    msg.append( " - " ).append( err.getOutput() );
                }
                msg.append( '\n' );
                msg.append( "Command line was: " ).append( cmd ).append( '\n' ).append( '\n' );

                throw new MojoExecutionException( msg.toString() );
            }

            if ( StringUtils.isNotEmpty( output ) )
            {
                getLog().info( output );
            }
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Unable to execute jdeps command: " + e.getMessage(), e );
        }

        // ----------------------------------------------------------------------
        // Handle Javadoc warnings
        // ----------------------------------------------------------------------

        if ( StringUtils.isNotEmpty( err.getOutput() ) && getLog().isWarnEnabled() )
        {
            getLog().warn( "JDeps Warnings" );

            StringTokenizer token = new StringTokenizer( err.getOutput(), "\n" );
            while ( token.hasMoreTokens() )
            {
                String current = token.nextToken().trim();

                getLog().warn( current );
            }
        }
    }
    
    private Toolchain getToolchain()
    {
        Toolchain tc = null;
        if ( toolchainManager != null )
        {
            tc = toolchainManager.getToolchainFromBuildContext( "jdk", session );
        }

        return tc;
    }

    protected abstract String getClassesDirectory();
    
    protected abstract String getClassPath() throws DependencyResolutionRequiredException;
}