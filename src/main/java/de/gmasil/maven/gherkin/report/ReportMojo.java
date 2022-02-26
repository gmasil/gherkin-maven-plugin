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
package de.gmasil.maven.gherkin.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;

import de.gmasil.gherkin.extension.store.ScenarioStore;
import de.gmasil.gherkin.extension.store.StepStore;
import de.gmasil.gherkin.extension.store.StoryStore;

@Mojo(name = "report")
public class ReportMojo extends AbstractMojo {

    @Parameter(property = "gherkin.report.gherkindir", defaultValue = "${basedir}/target/gherkin")
    private String gherkinDir;

    @Parameter(property = "gherkin.report.targetfile", defaultValue = "${basedir}/target/gherkin/gherkin-report.html")
    private String targetFile;

    private ObjectMapper mapper = new ObjectMapper(new XmlFactory());

    public ReportMojo() {
    }

    public ReportMojo(String gherkinDir, String targetFile) {
        this.gherkinDir = gherkinDir;
        this.targetFile = targetFile;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        StringBuilder str = new StringBuilder();
        str.append("<!DOCTYPE html>\n");
        str.append("<html lang=\"en\">\n");
        str.append("    <head>\n");
        str.append("        <meta charset=\"UTF-8\">\n");
        str.append("        <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n");
        str.append("        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        str.append("        <title>Gherkin Report</title>\n");
        str.append("        <style>\n");
        str.append("            table {\n");
        str.append("                border-collapse: collapse;\n");
        str.append("            }\n");
        str.append("            table tr th,\n");
        str.append("            table tr td {\n");
        str.append("                border: 1px solid black;\n");
        str.append("                padding: 0.2em;\n");
        str.append("            }\n");
        str.append("            table th {\n");
        str.append("                text-align: left;\n");
        str.append("                background-color: #f2f2f2;\n");
        str.append("                border-bottom: 2px solid black;\n");
        str.append("            }\n");
        str.append("            table:not(:nth-child(2)) {\n");
        str.append("                margin-top: 1em;\n");
        str.append("            }\n");
        str.append("            span {\n");
        str.append("                font-weight: bold;\n");
        str.append("            }\n");
        str.append("            .success {\n");
        str.append("                color: green;\n");
        str.append("            }\n");
        str.append("            .failed {\n");
        str.append("                color: red;\n");
        str.append("            }");
        str.append("        </style>\n");
        str.append("    </head>\n");
        str.append("    <body>\n");
        List<StoryStore> stories = getStories();
        for (StoryStore story : stories) {
            str.append("        <div>\n");
            str.append("        <h1>" + story.getName() + "</h1>\n");
            for (ScenarioStore scenario : story.getScenarios()) {
                str.append("        <table>\n");
                str.append("            <tr>\n");
                str.append("                <th>Scenario: " + scenario.getName() + "</th>\n");
                str.append("                <th><span class=\"" + (scenario.isFailed() ? "failed" : "success") + "\">"
                        + (scenario.isFailed() ? "FAILED" : "SUCCESS") + "</span></th>\n");
                str.append("            </tr>\n");
                for (StepStore step : scenario.getSteps()) {
                    str.append("            <tr>\n");
                    str.append("                <td>" + step.getReadable() + "</td>\n");
                    str.append("                <td><span class=\"" + step.getStatus().toString().toLowerCase() + "\">"
                            + step.getStatus() + "</span></td>\n");
                    str.append("            </tr>\n");
                }
                str.append("        </table>\n");
            }
            str.append("        </div>\n");
        }
        str.append("    </body>\n");
        str.append("</html>");
        File target = new File(targetFile);
        target.getParentFile().mkdirs();
        try (PrintWriter out = new PrintWriter(target)) {
            out.println(str.toString());
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Error while saving report to " + targetFile, e);
        }
    }

    private List<StoryStore> getStories() {
        File gherkinFolder = new File(gherkinDir);
        List<StoryStore> stories = new LinkedList<>();
        if (!gherkinFolder.exists()) {
            return stories;
        }
        List<File> gherkinReports = Arrays.asList(gherkinFolder.listFiles((dir, name) -> name.endsWith(".xml")));
        for (File gherkinFile : gherkinReports) {
            try {
                stories.add(mapper.readValue(gherkinFile, StoryStore.class));
            } catch (Exception e) {
                throw new IllegalStateException("Error reading file: " + gherkinFile.getAbsolutePath(), e);
            }
        }
        return stories;
    }
}
