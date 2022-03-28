package kalix;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.testing.MojoRule;

import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class GenerateMojoTest {
    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() {
        }

        @Override
        protected void after() {
        }
    };

    @Test
    public void testGeneration() throws Exception {
        Path projectDirectory = Paths.get("target/test-classes/project-to-test/");
        assertTrue(projectDirectory.toFile().exists());

        FileUtils.deleteDirectory(projectDirectory.resolve("src").toFile());
        FileUtils.deleteDirectory(projectDirectory.resolve("target").toFile());

        GenerateMojo myMojo = (GenerateMojo) rule.lookupConfiguredMojo(projectDirectory.toFile(), "generate");
        myMojo.execute();

        assertTrue(projectDirectory.resolve("src/main/java/com/example/shoppingcart/domain/ShoppingCart.java")
                .toFile().exists());
        assertTrue(projectDirectory.resolve(
                "target/generated-sources/akkaserverless/java/com/example/shoppingcart/domain/AbstractShoppingCart.java")
                .toFile().exists());
// FIXME enable assert again when new unit tests are generated
//        assertTrue(projectDirectory.resolve("src/test/java/com/example/shoppingcart/domain/ShoppingCartTest.java")
//                .toFile().exists());
        assertTrue(projectDirectory.resolve("src/it/java/com/example/shoppingcart/ShoppingCartIntegrationTest.java")
                .toFile().exists());
        assertTrue(projectDirectory.resolve("target/generated-sources/akkaserverless/java/com/lightbend/AkkaServerlessFactory.java")
                .toFile().exists());
        assertTrue(projectDirectory.resolve("src/main/java/com/lightbend/Main.java").toFile().exists());
    }
}
