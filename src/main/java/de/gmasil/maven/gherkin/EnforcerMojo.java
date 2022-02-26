/**
 * Gherkin Maven Plugin
 * Copyright © 2022 Gmasil
 *
 * This file is part of Gherkin Maven Plugin.
 *
 * Gherkin Maven Plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gherkin Maven Plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Gherkin Maven Plugin. If not, see <https://www.gnu.org/licenses/>.
 */
package de.gmasil.maven.gherkin;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;

@Mojo(name = "enforce")
public class EnforcerMojo extends AbstractMojo {

    @Parameter(property = "gherkin.enforcer.story", defaultValue = "false")
    private boolean enforceStory;

    @Parameter(property = "gherkin.enforcer.scenario", defaultValue = "false")
    private boolean enforceScenario;

    @Parameter(property = "gherkin.enforcer.failbuild", defaultValue = "true")
    private boolean failBuild;

    @Parameter(property = "gherkin.enforcer.surefiredir", defaultValue = "${basedir}/target/surefire-reports")
    private String surefireDir;

    @Parameter(property = "gherkin.enforcer.gherkindir", defaultValue = "${basedir}/target/gherkin")
    private String gherkinDir;

    private ObjectMapper mapper = new ObjectMapper(new XmlFactory());

    public EnforcerMojo() {
    }

    public EnforcerMojo(boolean enforceStory, boolean enforceScenario, boolean failBuild, String surefireDir,
            String gherkinDir) {
        this.enforceStory = enforceStory;
        this.enforceScenario = enforceScenario;
        this.failBuild = failBuild;
        this.surefireDir = surefireDir;
        this.gherkinDir = gherkinDir;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<TestMethod> surefireTestMethods = getSurefireTestMethods();
        Map<String, List<String>> gherkinTestMethods = getGherkinTestMethodsAsMap();
        List<String> nonAuditableTests = new LinkedList<>();
        for (TestMethod method : surefireTestMethods) {
            if (!gherkinTestMethods.containsKey(method.getClassName())
                    || !gherkinTestMethods.get(method.getClassName()).contains(method.getMethodName())) {
                nonAuditableTests.add(method.toString());
            }
        }
        boolean hasFindings = false;
        if (!nonAuditableTests.isEmpty()) {
            hasFindings = true;
            log("");
            log("There are non-auditable tests:");
            Collections.sort(nonAuditableTests);
            nonAuditableTests.stream().map(m -> " - " + m).forEach(this::log);
        }
        if (enforceStory) {
            List<String> missingStorys = getMissingStorys();
            if (!missingStorys.isEmpty()) {
                hasFindings = true;
                log("");
                log("There are missing story annotations:");
                Collections.sort(missingStorys);
                missingStorys.stream().map(s -> " - " + s).forEach(this::log);
            }
        }
        if (enforceScenario) {
            List<TestMethod> missingScenarios = getMissingScenarios();
            if (!missingScenarios.isEmpty()) {
                hasFindings = true;
                log("");
                log("There are missing scenario annotations:");
                Collections.sort(missingScenarios);
                missingScenarios.stream().map(s -> " - " + s).forEach(this::log);
            }
        }
        if (hasFindings && failBuild) {
            throw new MojoFailureException("There are non-auditable tests");
        }
    }

    private Map<String, List<String>> getGherkinTestMethodsAsMap() {
        Map<String, List<String>> map = new HashMap<>();
        List<TestMethod> gherkinTestMethods = getGherkinTestMethods();
        for (TestMethod method : gherkinTestMethods) {
            if (!map.containsKey(method.getClassName())) {
                map.put(method.getClassName(), new LinkedList<>());
            }
            map.get(method.getClassName()).add(method.getMethodName());
        }
        return map;
    }

    private List<TestMethod> getGherkinTestMethods() {
        File gherkinFolder = new File(gherkinDir);
        List<TestMethod> gherkinMethods = new LinkedList<>();
        if (!gherkinFolder.exists()) {
            return gherkinMethods;
        }
        List<File> gherkinReports = Arrays.asList(gherkinFolder.listFiles((dir, name) -> name.endsWith(".xml")));
        for (File gherkinFile : gherkinReports) {
            try {
                JsonNode tree = mapper.readTree(gherkinFile);
                String className = tree.get("className").asText();
                JsonNode scenario = tree.get("scenarios").get("scenario");
                List<JsonNode> nodes = new LinkedList<>();
                if (scenario.isArray()) {
                    scenario.forEach(nodes::add);
                } else {
                    nodes.add(scenario);
                }
                nodes.forEach(node -> {
                    gherkinMethods.add(new TestMethod(className, node.get("methodName").asText()));
                });
            } catch (Exception e) {
                throw new IllegalStateException("Error reading file: " + gherkinFile.getAbsolutePath(), e);
            }
        }
        return gherkinMethods;
    }

    private List<TestMethod> getSurefireTestMethods() {
        File surefireFolder = new File(surefireDir);
        List<TestMethod> methods = new LinkedList<>();
        if (!surefireFolder.exists()) {
            return methods;
        }
        List<File> surefireReports = Arrays
                .asList(surefireFolder.listFiles((dir, name) -> name.startsWith("TEST-") && name.endsWith(".xml")));
        for (File surefireReport : surefireReports) {
            try {
                JsonNode tree = mapper.readTree(surefireReport);
                JsonNode testcase = tree.get("testcase");
                List<JsonNode> nodes = new LinkedList<>();
                if (testcase.isArray()) {
                    testcase.forEach(nodes::add);
                } else {
                    nodes.add(testcase);
                }
                nodes.forEach(node -> {
                    String className = node.findValue("classname").asText();
                    String methodName = fixSurefireMethodName(node.findValue("name").asText());
                    methods.add(new TestMethod(className, methodName));
                });
            } catch (Exception e) {
                throw new IllegalStateException("Error reading file: " + surefireReport.getAbsolutePath(), e);
            }
        }
        return methods;
    }

    private List<String> getMissingStorys() {
        File gherkinFolder = new File(gherkinDir);
        List<String> missingStories = new LinkedList<>();
        if (!gherkinFolder.exists()) {
            return missingStories;
        }
        List<File> gherkinReports = Arrays.asList(gherkinFolder.listFiles((dir, name) -> name.endsWith(".xml")));
        for (File gherkinFile : gherkinReports) {
            try {
                JsonNode tree = mapper.readTree(gherkinFile);
                String storyName = tree.get("name").asText();
                String className = tree.get("className").asText();
                if (storyName.equals(className)) {
                    missingStories.add(className);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Error reading file: " + gherkinFile.getAbsolutePath(), e);
            }
        }
        return missingStories;
    }

    private List<TestMethod> getMissingScenarios() {
        File gherkinFolder = new File(gherkinDir);
        List<TestMethod> missingScenarios = new LinkedList<>();
        if (!gherkinFolder.exists()) {
            return missingScenarios;
        }
        List<File> gherkinReports = Arrays.asList(gherkinFolder.listFiles((dir, name) -> name.endsWith(".xml")));
        for (File gherkinFile : gherkinReports) {
            try {
                JsonNode tree = mapper.readTree(gherkinFile);
                String className = tree.get("className").asText();
                JsonNode scenario = tree.get("scenarios").get("scenario");
                List<JsonNode> nodes = new LinkedList<>();
                if (scenario.isArray()) {
                    scenario.forEach(nodes::add);
                } else {
                    nodes.add(scenario);
                }
                nodes.forEach(node -> {
                    String scenarioName = node.get("name").asText();
                    String methodName = node.get("methodName").asText();
                    if (methodName.equals(scenarioName)) {
                        missingScenarios.add(new TestMethod(className, methodName));
                    }
                });
            } catch (Exception e) {
                throw new IllegalStateException("Error reading file: " + gherkinFile.getAbsolutePath(), e);
            }
        }
        return missingScenarios;
    }

    private String fixSurefireMethodName(String methodName) {
        return methodName.split("\\{")[0];
    }

    private void log(String s) {
        if (failBuild) {
            getLog().error(s);
        } else {
            getLog().info(s);
        }
    }
}
