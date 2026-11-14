// src/main/java/hdledit/model/Project.java
package hdledit.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Project {

    private final String id;
    private String name;
    private String description;
    private String author;
    private String version;
    private String projectDirectory;
    private String defaultLibrary;

    private final List<Diagram> diagrams = new ArrayList<>();

    public Project(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.version = "1.0";
        this.defaultLibrary = "work";
    }

    public Project(String id, String name, String description, String author,
                   String version, String projectDirectory, String defaultLibrary) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
        this.projectDirectory = projectDirectory;
        this.defaultLibrary = defaultLibrary;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getProjectDirectory() { return projectDirectory; }
    public void setProjectDirectory(String projectDirectory) { this.projectDirectory = projectDirectory; }

    public String getDefaultLibrary() { return defaultLibrary; }
    public void setDefaultLibrary(String defaultLibrary) { this.defaultLibrary = defaultLibrary; }

    public List<Diagram> getDiagrams() { return diagrams; }

    public void addDiagram(Diagram diagram) {
        diagrams.add(diagram);
    }

    public void removeDiagram(Diagram diagram) {
        diagrams.remove(diagram);
    }
}