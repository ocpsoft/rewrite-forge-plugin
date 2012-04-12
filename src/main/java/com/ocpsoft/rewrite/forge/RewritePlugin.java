package com.ocpsoft.rewrite.forge;

import java.io.FileNotFoundException;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.ServletContext;

import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.JavaClass;
import org.jboss.forge.parser.java.Method;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.project.facets.events.InstallFacets;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.resources.java.JavaResource;
import org.jboss.forge.shell.ShellMessages;
import org.jboss.forge.shell.ShellPrompt;
import org.jboss.forge.shell.events.PickupResource;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.forge.shell.plugins.SetupCommand;
import org.jboss.forge.shell.util.Streams;
import org.ocpsoft.rewrite.config.Configuration;
import org.ocpsoft.rewrite.config.ConfigurationBuilder;
import org.ocpsoft.rewrite.servlet.config.HttpConfigurationProvider;

@Alias("rewrite")
@RequiresFacet(RewriteFacet.class)
public class RewritePlugin implements Plugin
{
   @Inject
   private ShellPrompt prompt;

   @Inject
   private Event<InstallFacets> install;

   @Inject
   private Project project;

   @Inject
   private Event<PickupResource> pickup;

   @SetupCommand
   public void setup(final PipeOut out)
   {
      install.fire(new InstallFacets(RewriteFacet.class));

      if (project.hasFacet(RewriteFacet.class))
      {
         ShellMessages.success(out, "Rewrite is installed.");
      }
   }

   @Command("create-config")
   public void createConfig(final PipeOut out, @Option(name = "providerClass") final JavaResource provider)
            throws FileNotFoundException
   {
      ResourceFacet resources = project.getFacet(ResourceFacet.class);

      if (!provider.exists())
      {
         JavaSourceFacet java = project.getFacet(JavaSourceFacet.class);

         provider.createNewFile();

         JavaClass providerClass = JavaParser.create(JavaClass.class);
         providerClass.setName(java.calculateName(provider));
         providerClass.setPackage(java.calculatePackage(provider));

         providerClass.addImport(Configuration.class);
         providerClass.addImport(ConfigurationBuilder.class);
         providerClass.addImport(HttpConfigurationProvider.class);
         providerClass.addImport(ServletContext.class);

         providerClass.setSuperType(HttpConfigurationProvider.class);

         Method<JavaClass> method = providerClass
                  .addMethod("public Configuration getConfiguration(ServletContext context) { return ConfigurationBuilder.begin(); }");
         method.addAnnotation(Override.class);

         Method<JavaClass> priority = providerClass.addMethod("public int priority() { return 0; }");
         priority.addAnnotation(Override.class);

         provider.setContents(providerClass);
      }

      // TODO creating/registering service classes should be a JavaSourceFacet API
      FileResource<?> service =
               resources.getResource("META-INF/services/com.ocpsoft.rewrite.config.ConfigurationProvider");

      if (!service.exists())
         service.createNewFile();

      String contents = Streams.toString(service.getResourceInputStream());
      String qualifiedName = provider.getJavaSource().getQualifiedName();

      if (!contents.contains(qualifiedName)) {
         contents += "\n" + qualifiedName;
         service.setContents(contents);
      }

      ShellMessages.success(out, "Service exists and is registered.");

      pickup.fire(new PickupResource(provider));
   }
}