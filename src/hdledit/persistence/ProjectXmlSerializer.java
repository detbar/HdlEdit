// src/main/java/hdledit/persistence/ProjectXmlSerializer.java
package hdledit.persistence;

import hdledit.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;

public class ProjectXmlSerializer {

    public void save(Project project, File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();

        Element root = doc.createElement("project");
        root.setAttribute("id", project.getId());
        root.setAttribute("name", project.getName());
        root.setAttribute("description", nullToEmpty(project.getDescription()));
        root.setAttribute("author", nullToEmpty(project.getAuthor()));
        root.setAttribute("version", nullToEmpty(project.getVersion()));
        root.setAttribute("directory", nullToEmpty(project.getProjectDirectory()));
        root.setAttribute("defaultLibrary", nullToEmpty(project.getDefaultLibrary()));
        doc.appendChild(root);

        Element diagramsEl = doc.createElement("diagrams");
        root.appendChild(diagramsEl);

        for (Diagram d : project.getDiagrams()) {
            Element diagEl = doc.createElement("diagram");
            diagEl.setAttribute("id", d.getId());
            diagEl.setAttribute("name", d.getName());
            diagEl.setAttribute("type", d.getType().name());
            diagramsEl.appendChild(diagEl);
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(file)));
    }

    public Project load(File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file);

        Element root = doc.getDocumentElement();

        Project project = new Project(
                root.getAttribute("id"),
                root.getAttribute("name"),
                root.getAttribute("description"),
                root.getAttribute("author"),
                root.getAttribute("version"),
                root.getAttribute("directory"),
                root.getAttribute("defaultLibrary")
        );

        NodeList diagramNodes = root.getElementsByTagName("diagram");
        for (int i = 0; i < diagramNodes.getLength(); i++) {
            Element el = (Element) diagramNodes.item(i);
            String id = el.getAttribute("id");
            String name = el.getAttribute("name");
            DiagramType type = DiagramType.valueOf(el.getAttribute("type"));

            Diagram diagram = switch (type) {
                case BLOCK_DIAGRAM -> new BlockDiagram(id, name);
                case TABLE -> new TableDiagram(id, name);
                case FLOWCHART -> new Flowchart(id, name);
            };
            project.addDiagram(diagram);
        }
        return project;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}