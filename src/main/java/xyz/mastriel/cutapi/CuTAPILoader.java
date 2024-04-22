package xyz.mastriel.cutapi;


import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

// This can't be Kotlin because it needs to load the Kotlin standard library.
@SuppressWarnings("UnstableApiUsage")
public class CuTAPILoader implements PluginLoader {
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        // addLibrary(classpathBuilder, "org.jetbrains.kotlin:kotlin-stdlib:1.9.10");
        // addLibrary(classpathBuilder, "org.jetbrains.kotlin:kotlin-reflect:1.9.10");
        // addLibrary(classpathBuilder, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4");
        // addLibrary(classpathBuilder, "com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.6.0");
        // addLibrary(classpathBuilder, "com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.6.0");
        // addLibrary(classpathBuilder, "org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.1");
        // addLibrary(classpathBuilder, "org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1");
        // addLibrary(classpathBuilder, "net.peanuuutz.tomlkt:tomlkt:0.3.7");
    }

    private void addLibrary(PluginClasspathBuilder builder, String notation, RemoteRepository repo) {
        var resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact(notation), null));
        resolver.addRepository(repo);
        builder.addLibrary(resolver);
    }

    private RemoteRepository repo(String name, String url) {
        return new RemoteRepository
                .Builder(name, "default", url)
                .build();
    }

    private void addLibrary(PluginClasspathBuilder builder, String notation) {
        var resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact(notation), null));

        var repo = repo("maven-central", "https://repo.maven.apache.org/maven2/");
        resolver.addRepository(repo);
        builder.addLibrary(resolver);
    }
}
