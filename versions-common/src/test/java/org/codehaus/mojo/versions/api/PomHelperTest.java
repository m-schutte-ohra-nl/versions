package org.codehaus.mojo.versions.api;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.mojo.versions.utils.ModelNode;
import org.codehaus.stax2.XMLInputFactory2;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.codehaus.mojo.versions.utils.ModifiedPomXMLEventReaderUtils.matches;
import static org.codehaus.stax2.XMLInputFactory2.P_PRESERVE_LOCATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests the methods of {@link PomHelper}.
 */
class PomHelperTest {
    private static final int NUMBER_OF_CHILD_PROJECTS = 30;

    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory2.newInstance();

    @BeforeAll
    static void setUpClass() {
        INPUT_FACTORY.setProperty(P_PRESERVE_LOCATION, Boolean.TRUE);
    }

    /**
     * Tests what happens when changing a long property substitution pattern, e.g.
     * <a href="http://jira.codehaus.org/browse/MVERSIONS-44">MVERSIONS-44</a>
     *
     * @throws Exception if the test fails.
     */
    @Test
    void testLongProperties() throws Exception {
        URL url = getClass().getResource("PomHelperTest.testLongProperties.pom.xml");
        assert url != null;
        File file = new File(url.getPath());
        StringBuilder input = PomHelper.readXmlFile(file);

        XMLInputFactory inputFactory = XMLInputFactory2.newInstance();
        inputFactory.setProperty(P_PRESERVE_LOCATION, Boolean.TRUE);

        ModifiedPomXMLEventReader pom = new ModifiedPomXMLEventReader(input, inputFactory, file.getAbsolutePath());

        String oldVersion = PomHelper.getProjectVersion(pom);

        String newVersion = "1";

        assertTrue(PomHelper.setProjectVersion(pom, newVersion), "The pom has been modified");

        assertEquals(newVersion, PomHelper.getProjectVersion(pom));

        assertNotSame(oldVersion, newVersion);
    }

    @Test
    void testGroupIdNotOnChildPom() throws Exception {
        URL url = getClass().getResource("PomHelperTest.noGroupIdOnChild.pom.xml");
        assert url != null;
        StringBuilder input = PomHelper.readXmlFile(new File(url.getPath()));
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new StringReader(input.toString()));

        assertEquals("org.myorg", PomHelper.getGroupId(model));
    }

    @Test
    void testVersionVersionEqual() throws Exception {
        assertTrue(PomHelper.isVersionOverlap("1.0.8", "1.0.8"));
    }

    @Test
    void testVersionVersionDiffer() throws Exception {
        assertFalse(PomHelper.isVersionOverlap("1.0.8", "1.0.0"));
    }

    @Test
    void testVersionRangeIntersect() throws Exception {
        assertTrue(PomHelper.isVersionOverlap("1.0.8", "[1.0.3,1.1.0]"));
    }

    @Test
    void testVersionRangeDisjoint() throws Exception {
        assertFalse(PomHelper.isVersionOverlap("1.0.8", "[0.0.1,1.0.0]"));
    }

    @Test
    void testVersionLeftOpenRangeDisjoint() throws Exception {
        assertFalse(PomHelper.isVersionOverlap("1.0.8", "[,1.0.0]"));
    }

    @Test
    void testVersionRightOpenRangeDisjoint() throws Exception {
        assertFalse(PomHelper.isVersionOverlap("1.0.8", "[1.1.0,)"));
    }

    @Test
    void testEmptyRange() throws Exception {
        assertTrue(PomHelper.isVersionOverlap("1.0.8", ""));
    }

    @Test
    void testRangeEmpty() throws Exception {
        assertTrue(PomHelper.isVersionOverlap("[1.0.5,1.0.8]", ""));
    }

    @Test
    void testRangeRangeIntersect() throws Exception {
        assertTrue(PomHelper.isVersionOverlap("[1.0.5,1.0.8]", "[1.0.7,1.1.0]"));
    }

    @Test
    void testRangeRangeDisjoint() throws Exception {
        assertFalse(PomHelper.isVersionOverlap("[1.0.5,1.0.6]", "[1.0.7,1.1.0]"));
    }

    @Test
    void testRangeVersionDisjoint() throws Exception {
        assertFalse(PomHelper.isVersionOverlap("[1.0.5,1.0.6]", "1.0.8"));
    }

    @Test
    void testRangeVersionIntersect() throws Exception {
        assertTrue(PomHelper.isVersionOverlap("[1.0.0,2.0.0]", "1.0.8"));
    }

    @Test
    void testSetElementValueExistingValue() throws XMLStreamException {
        ModifiedPomXMLEventReader xmlEventReader = new ModifiedPomXMLEventReader(
                new StringBuilder("<super-parent><parent><child>test</child></parent></super-parent>"),
                INPUT_FACTORY,
                null);

        assertThat(PomHelper.setElementValue(xmlEventReader, "/super-parent/parent", "child", "value"), is(true));
        MatcherAssert.assertThat(
                xmlEventReader, matches("<super-parent><parent><child>value</child></parent></super-parent>"));
    }

    @Test
    void testSetElementValueEmptyChild() throws XMLStreamException {
        ModifiedPomXMLEventReader xmlEventReader = new ModifiedPomXMLEventReader(
                new StringBuilder("<super-parent><parent><child/></parent></super-parent>"), INPUT_FACTORY, null);

        assertThat(PomHelper.setElementValue(xmlEventReader, "/super-parent/parent", "child", "value"), is(true));
        MatcherAssert.assertThat(
                xmlEventReader, matches("<super-parent><parent><child>value</child></parent></super-parent>"));
    }

    @Test
    void testSetElementValueNewValueEmptyParent() throws XMLStreamException {
        ModifiedPomXMLEventReader xmlEventReader = new ModifiedPomXMLEventReader(
                new StringBuilder("<super-parent><parent/></super-parent>"), INPUT_FACTORY, null);

        assertThat(PomHelper.setElementValue(xmlEventReader, "/super-parent/parent", "child", "value"), is(true));
        MatcherAssert.assertThat(
                xmlEventReader, matches("<super-parent><parent><child>value</child></parent></super-parent>"));
    }

    @Test
    void testSetElementValueNewValueNoChild() throws XMLStreamException {
        ModifiedPomXMLEventReader xmlEventReader = new ModifiedPomXMLEventReader(
                new StringBuilder("<super-parent><parent><child2/></parent></super-parent>"), INPUT_FACTORY, null);

        assertThat(PomHelper.setElementValue(xmlEventReader, "/super-parent/parent", "child", "value"), is(true));
        MatcherAssert.assertThat(
                xmlEventReader, matches("<super-parent><parent><child2/><child>value</child></parent></super-parent>"));
    }

    @Test
    void testSetProjectValueNewValueNonEmptyParent() throws XMLStreamException {
        ModifiedPomXMLEventReader xmlEventReader = new ModifiedPomXMLEventReader(
                new StringBuilder("<super-parent><parent><child>test</child></parent></super-parent>"),
                INPUT_FACTORY,
                null);

        assertThat(PomHelper.setElementValue(xmlEventReader, "/super-parent/parent", "child", "value"), is(true));
        MatcherAssert.assertThat(
                xmlEventReader, matches("<super-parent><parent><child>value</child></parent></super-parent>"));
    }

    @Test
    void testIssue505ChildModules() throws Exception {
        MavenProject project = new MavenProject();
        project.setFile(new File("src/test/resources/org/codehaus/mojo/versions/api/issue-505/pom.xml"));
        assertThat(PomHelper.getChildModels(project, new SystemStreamLog()).entrySet(), hasSize(3));
    }

    @Test
    void testChildModelsForMultiLevelProject() throws Exception {
        Path tempDirectory = Files.createTempDirectory("testChildModelsForLargeNumberOfModules");
        ModelWriter modelWriter = new DefaultModelWriter();
        Map<Path, Model> createdModels = new LinkedHashMap<>();

        try {
            Model rootProject = createSimpleModel("root");
            createdModels.put(tempDirectory, rootProject);
            for (int levelOne = 0; levelOne < NUMBER_OF_CHILD_PROJECTS; levelOne++) {
                Model levelOneProject = createSimpleModel("child-" + levelOne);
                Path levelOneProjectDirectory = tempDirectory.resolve(levelOneProject.getArtifactId());
                rootProject.addModule(levelOneProject.getArtifactId());
                createdModels.put(levelOneProjectDirectory, levelOneProject);

                for (int levelTwo = 0; levelTwo < NUMBER_OF_CHILD_PROJECTS; levelTwo++) {
                    Model levelTwoProject = createSimpleModel("child-" + levelOne + "-" + levelTwo);
                    Path levelTwoProjectDirectory = levelOneProjectDirectory.resolve(levelTwoProject.getArtifactId());
                    levelOneProject.addModule(levelTwoProject.getArtifactId());
                    createdModels.put(levelTwoProjectDirectory, levelTwoProject);
                }
            }

            for (Map.Entry<Path, Model> entry : createdModels.entrySet()) {
                modelWriter.write(entry.getKey().resolve("pom.xml").toFile(), Collections.emptyMap(), entry.getValue());
            }

            MavenProject project = new MavenProject();
            project.setFile(tempDirectory.resolve("pom.xml").toFile());

            assertThat(
                    PomHelper.getChildModels(project, new SystemStreamLog()).entrySet(), hasSize(createdModels.size()));
        } finally {
            FileUtils.deleteDirectory(tempDirectory.toFile());
        }
    }

    private Model createSimpleModel(String artifactId) {
        Model module = new Model();
        module.setGroupId("child.test");
        module.setArtifactId(artifactId);
        module.setVersion("1.0.0-SNAPSHOT");
        module.setPackaging("pom");
        module.setModelVersion("4.0.0");
        return module;
    }

    @Test
    void testGetRawModelTree() throws Exception {
        Log log = mock(Log.class);
        XMLInputFactory inputFactory = XMLInputFactory2.newInstance();
        inputFactory.setProperty(XMLInputFactory2.P_PRESERVE_LOCATION, Boolean.TRUE);
        Path path = Paths.get("src/test/resources/org/codehaus/mojo/versions/api/getRawModelTree/pom.xml");
        ModifiedPomXMLEventReader pomReader = new ModifiedPomXMLEventReader(
                new StringBuilder(new String(Files.readAllBytes(path))), inputFactory, path.toString());
        List<ModelNode> rawModelTree =
                PomHelper.getRawModelTree(new ModelNode(PomHelper.getRawModel(pomReader), pomReader), log);
        assertThat(
                rawModelTree.stream()
                        .map(ModelNode::getModel)
                        .map(Model::getArtifactId)
                        .collect(Collectors.joining(" ")),
                is("grandparent childA grandchild childB"));
    }

    @Test
    void testFindProperty() throws Exception {
        Log log = mock(Log.class);
        XMLInputFactory inputFactory = XMLInputFactory2.newInstance();
        inputFactory.setProperty(XMLInputFactory2.P_PRESERVE_LOCATION, Boolean.TRUE);
        Path path = Paths.get("src/test/resources/org/codehaus/mojo/versions/api/findProperty/pom.xml");
        ModifiedPomXMLEventReader pomReader = new ModifiedPomXMLEventReader(
                new StringBuilder(new String(Files.readAllBytes(path))), inputFactory, path.toString());
        List<ModelNode> rawModelTree =
                PomHelper.getRawModelTree(new ModelNode(PomHelper.getRawModel(pomReader), pomReader), log);

        ModelNode grandparent = rawModelTree.get(0);
        assertThat(grandparent.getModel().getArtifactId(), is("grandparent"));
        ModelNode childA = rawModelTree.get(1);
        assertThat(childA.getModel().getArtifactId(), is("childA"));
        ModelNode grandchild = rawModelTree.get(2);
        assertThat(grandchild.getModel().getArtifactId(), is("grandchild"));
        ModelNode childB = rawModelTree.get(3);
        assertThat(childB.getModel().getArtifactId(), is("childB"));
        assertThat(PomHelper.findProperty("a", grandchild).get(), is(childA));
        assertThat(PomHelper.findProperty("a", childA).get(), is(childA));
        assertThat(PomHelper.findProperty("a", grandparent).get(), is(grandparent));
        assertThat(PomHelper.findProperty("a", childB).get(), is(grandparent));
        assertThat(PomHelper.findProperty("b", childB).get(), is(childB));
    }
}
