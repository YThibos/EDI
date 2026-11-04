package io.github.ythibos.edi.adapter.git;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import io.github.ythibos.edi.domain.delivery.BuildFile;
import io.github.ythibos.edi.domain.delivery.MavenCoordinates;
import io.github.ythibos.edi.domain.delivery.ParentReference;

final class BuildFiles {

    private static final String NO_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String PARENT = "parent";
    private static final String PARENT_OPENS = "<parent>";
    private static final String PARENT_CLOSES = "</parent>";
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String VERSION = "version";

    private BuildFiles() {
    }

    static Optional<BuildFile> read(String path, String content) {
        return projectOf(content).map(project -> buildFileOf(path, project, parentLinesIn(content)));
    }

    private static Optional<Element> projectOf(String content) {
        try {
            return Optional.of(parser().parse(new InputSource(new StringReader(content))).getDocumentElement());
        } catch (SAXException | IOException | ParserConfigurationException unreadable) {
            return Optional.empty();
        }
    }

    private static DocumentBuilder parser() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(NO_DOCTYPE, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder parser = factory.newDocumentBuilder();
        parser.setErrorHandler(new DefaultHandler());
        return parser;
    }

    private static BuildFile buildFileOf(String path, Element project, Optional<ParentLines> parentLines) {
        Optional<MavenCoordinates> parent = childOf(project, PARENT).map(BuildFiles::coordinatesOf);
        MavenCoordinates own = new MavenCoordinates(
                textOf(project, GROUP_ID).or(() -> parent.map(MavenCoordinates::groupId)).orElse(""),
                textOf(project, ARTIFACT_ID).orElse(""),
                textOf(project, VERSION).or(() -> parent.map(MavenCoordinates::version)).orElse(""));
        return new BuildFile(path, own, parent.flatMap(coordinates -> parentLines.map(lines -> new ParentReference(coordinates, lines.first(), lines.last()))));
    }

    private static MavenCoordinates coordinatesOf(Element element) {
        return new MavenCoordinates(textOf(element, GROUP_ID).orElse(""), textOf(element, ARTIFACT_ID).orElse(""), textOf(element, VERSION).orElse(""));
    }

    private static Optional<String> textOf(Element element, String name) {
        return childOf(element, name).map(child -> child.getTextContent().strip());
    }

    private static Optional<Element> childOf(Element element, String name) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element named && named.getTagName().equals(name)) {
                return Optional.of(named);
            }
        }
        return Optional.empty();
    }

    private static Optional<ParentLines> parentLinesIn(String content) {
        List<String> lines = content.lines().toList();
        return firstLineHolding(lines, PARENT_OPENS, 0)
                .flatMap(opens -> firstLineHolding(lines, PARENT_CLOSES, opens).map(closes -> new ParentLines(opens + 1, closes + 1)));
    }

    private static Optional<Integer> firstLineHolding(List<String> lines, String text, int from) {
        return IntStream.range(from, lines.size()).filter(index -> lines.get(index).contains(text)).boxed().findFirst();
    }

    private record ParentLines(int first, int last) {
    }
}
