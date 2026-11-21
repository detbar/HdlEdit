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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

                    Element portsEl = doc.createElement("ports");
                    blockEl.appendChild(portsEl);
                    for (Port port : block.getPorts()) {
                        Element portEl = doc.createElement("port");
                        portEl.setAttribute("id", port.getId());
                        portEl.setAttribute("side", port.getSide().name());
                        portEl.setAttribute("position", String.valueOf(port.getPosition()));
                        portsEl.appendChild(portEl);
                    }

                    blocksEl.appendChild(blockEl);
                }

                Element connsEl = doc.createElement("connections");
                diagEl.appendChild(connsEl);

                for (Connection conn : blockDiagram.getConnections()) {
                    conn.ensureSegmentCount();
                    Element connEl = doc.createElement("connection");
                    connEl.setAttribute("id", conn.getId());
                    connEl.setAttribute("from", conn.getFrom().getId());
                    connEl.setAttribute("to", conn.getTo().getId());
                    if (conn.getFromPort() != null) {
                        connEl.setAttribute("fromPort", conn.getFromPort().getId());
                    }
                    if (conn.getToPort() != null) {
                        connEl.setAttribute("toPort", conn.getToPort().getId());
                    }
                    connEl.setAttribute("style", conn.getStyle().name());

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

                    Element segsEl = doc.createElement("segments");
                    connEl.appendChild(segsEl);
                    for (SegmentData seg : conn.getSegments()) {
                        Element segEl = doc.createElement("segment");
                        segEl.setAttribute("kind", seg.getKind().name());
                        if (seg.hasControls()) {
                            segEl.setAttribute("c1x", String.valueOf(seg.getC1x()));
                            segEl.setAttribute("c1y", String.valueOf(seg.getC1y()));
                            segEl.setAttribute("c2x", String.valueOf(seg.getC2x()));
                            segEl.setAttribute("c2y", String.valueOf(seg.getC2y()));
                        }
                        segsEl.appendChild(segEl);
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

                    Map<String, Block> blockMap = new HashMap<>();
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

                        NodeList portNodes = be.getElementsByTagName("port");
                        for (int p = 0; p < portNodes.getLength(); p++) {
                            Element pe = (Element) portNodes.item(p);
                            Block.PortSide side = Block.PortSide.valueOf(pe.getAttribute("side"));
                            double pos = Double.parseDouble(pe.getAttribute("position"));
                            block.addPort(new Port(pe.getAttribute("id"), side, pos));
                        }
                        block.ensureDefaultPorts();

                        bd.addBlock(block);
                        blockMap.put(block.getId(), block);
                    }

                    NodeList connNodes = el.getElementsByTagName("connection");
                    for (int k = 0; k < connNodes.getLength(); k++) {
                        Element ce = (Element) connNodes.item(k);
                        Block from = blockMap.get(ce.getAttribute("from"));
                        Block to = blockMap.get(ce.getAttribute("to"));
                        if (from == null || to == null) continue;

                        Port fromPort = ce.hasAttribute("fromPort")
                                ? from.findPort(ce.getAttribute("fromPort")) : null;
                        Port toPort = ce.hasAttribute("toPort")
                                ? to.findPort(ce.getAttribute("toPort")) : null;

                        // Fallback: ersten freien Port nehmen
                        if (fromPort == null && !from.getPorts().isEmpty()) {
                            fromPort = from.getPorts().get(0);
                        }
                        if (toPort == null && !to.getPorts().isEmpty()) {
                            toPort = to.getPorts().get(0);
                        }
                        if (fromPort == null || toPort == null) continue;

                        Connection.Style style = Connection.Style.DIRECT;
                        try {
                            if (ce.hasAttribute("style"))
                                style = Connection.Style.valueOf(ce.getAttribute("style"));
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

                        List<SegmentData> segments = new ArrayList<>();
                        NodeList segNodes = ce.getElementsByTagName("segment");
                        for (int s = 0; s < segNodes.getLength(); s++) {
                            Element se = (Element) segNodes.item(s);
                            SegmentData.Kind kind = SegmentData.Kind.LINE;
                            try { kind = SegmentData.Kind.valueOf(se.getAttribute("kind")); }
                            catch (Exception ignored) {}
                            Integer c1x = null, c1y = null, c2x = null, c2y = null;
                            try {
                                if (se.hasAttribute("c1x")) {
                                    c1x = Integer.parseInt(se.getAttribute("c1x"));
                                    c1y = Integer.parseInt(se.getAttribute("c1y"));
                                    c2x = Integer.parseInt(se.getAttribute("c2x"));
                                    c2y = Integer.parseInt(se.getAttribute("c2y"));
                                }
                            } catch (Exception ignored) {}
                            segments.add(new SegmentData(kind, c1x, c1y, c2x, c2y));
                        }

                        bd.addConnection(new Connection(
                                ce.getAttribute("id"), from, fromPort, to, toPort,
                                style, waypoints, segments));
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
