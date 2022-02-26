# Gherkin Maven Plugin

Maven plugin to work with Gherkin reports from [Gherkin Extension Jupiter](https://github.com/gmasil/gherkin-extension-jupiter).

## Goals

### Report
The report goal can create a simple HTML report containing all auditable tests.

### Enforce
The enforce goal makes sure that all tests are setup correctly and fail the build if not.

## Example Config

The maven plugin reads the gherkin reports created during unit tests, so you must make sure that the tests are executed before the plugin runs. In the example below the surefire plugin is explicitly mentioned before the gherkin plugin which is tied to the same goal as the tests. Since the gherkin plugin is listed after the surefire plugin, the order will be correct.

For the same reason the report goal is stated first, so that the report will be created even if the enforce goal later fails the build.

```xml
<build>
    <plugins>
        <!-- surefire -->
        <plugin>
            <artifactId>maven-surefire-plugin</artifactId>
        </plugin>
        <!-- gherkin -->
        <plugin>
            <groupId>de.gmasil</groupId>
            <artifactId>gherkin-maven-plugin</artifactId>
            <version>${gherkin-plugin.version}</version>
            <configuration>
                <failBuild>false</failBuild>
                <enforceStory>true</enforceStory>
                <enforceScenario>true</enforceScenario>
            </configuration>
            <executions>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>enforce</id>
                    <phase>test</phase>
                    <goals>
                        <goal>enforce</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
