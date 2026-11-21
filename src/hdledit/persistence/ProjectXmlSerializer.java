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
import java.util.ArrayList;
import java.util.List;

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

            if (d instanceof BlockDiagram blockDiagram) {
                Element blocksEl = doc.createElement("blocks");
                diagEl.appendChild(blocksEl);

                for (Block block : blockDiagram.getBlocks()) {
                    Element blockEl = doc.createElement("block");
                    blockEl.setAttribute("id", block.getId());
                    blockEl.setAttribute("name", block.getName());
                    blockEl.setAttribute("x", String.valueOf(block.getX()));
                    blockEl.setAttribute("y", String.valueOf(block.getY()));
                    blockEl.setAttribute("width", String.valueOf(block.getWidth()));
                    blockEl.setAttribute("height", String.valueOf(block.getHeight()));
                    blocksEl.appendChild(blockEl);
                }

                Element connsEl = doc.createElement("connections");
                diagEl.appendChild(connsEl);

                for (Connection conn : blockDiagram.getConnections()) {
                    Element connEl = doc.createElement("connection");
                    connEl.setAttribute("id", conn.getId());
                    connEl.setAttribute("from", conn.getFrom().getId());
                    connEl.setAttribute("to", conn.getTo().getId());
                    connEl.setAttribute("style", conn.getStyle().name());
                    if (conn.getFromSide() != null) {
                        connEl.setAttribute("fromSide", conn.getFromSide().name());
                    }
                    if (conn.getToSide() != null) {
                        connEl.setAttribute("toSide", conn.getToSide().name());
                    }

                    if (!conn.getWaypoints().isEmpty()) {
                        Element wpsEl = doc.createElement("waypoints");
                        connEl.appendChild(wpsEl);
                        for (int[] wp : conn.getWaypoints()) {
                            Element wpEl = doc.createElement("wp");
                            wpEl.setAttribute("x", String.valueOf(wp[0]));
                            wpEl.setAttribute("y", String.valueOf(wp[1]));
                            wpsEl.appendChild(wpEl);
                        }
                    }

                    connsEl.appendChild(connEl);
                }
            }
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
                case BLOCK_DIAGRAM -> {
                    BlockDiagram bd = new BlockDiagram(id, name);

                    java.util.Map<String, Block> blockMap = new java.util.HashMap<>();
                    NodeList blockNodes = el.getElementsByTagName("block");
                    for (int j = 0; j < blockNodes.getLength(); j++) {
                        Element be = (Element) blockNodes.item(j);
                        Block block = new Block(
                                be.getAttribute("id"),
                                be.getAttribute("name"),
                                Integer.parseInt(be.getAttribute("x")),
                                Integer.parseInt(be.getAttribute("y")),
                                Integer.parseInt(be.getAttribute("width")),
                                Integer.parseInt(be.getAttribute("height"))
                        );
                        bd.addBlock(block);
                        blockMap.put(block.getId(), block);
                    }

                    NodeList connNodes = el.getElementsByTagName("connection");
                    for (int k = 0; k < connNodes.getLength(); k++) {
                        Element ce = (Element) connNodes.item(k);
                        Block from = blockMap.get(ce.getAttribute("from"));
                        Block to = blockMap.get(ce.getAttribute("to"));
                        if (from != null && to != null) {
                            Connection.Style style = Connection.Style.DIRECT;
                            try {
                                if (ce.hasAttribute("style")) {
                                    style = Connection.Style.valueOf(ce.getAttribute("style"));
                                }
                            } catch (Exception ignored) {}

                            Block.PortSide fromSide = null;
                            Block.PortSide toSide = null;
                            try {
                                if (ce.hasAttribute("fromSide")) {
                                    fromSide = Block.PortSide.valueOf(ce.getAttribute("fromSide"));
                                }
                                if (ce.hasAttribute("toSide")) {
                                    toSide = Block.PortSide.valueOf(ce.getAttribute("toSide"));
                                }
                            } catch (Exception ignored) {}

                            List<int[]> waypoints = new ArrayList<>();
                            NodeList wpNodes = ce.getElementsByTagName("wp");
                            for (int w = 0; w < wpNodes.getLength(); w++) {
                                Element we = (Element) wpNodes.item(w);
                                waypoints.add(new int[]{
                                        Integer.parseInt(we.getAttribute("x")),
                                        Integer.parseInt(we.getAttribute("y"))
                                });
                            }

                            bd.addConnection(new Connection(
                                    ce.getAttribute("id"), from, to, style, fromSide, toSide, waypoints));
                        }
                    }

                    yield bd;
                }
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
