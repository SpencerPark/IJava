package io.github.spencerpark.gradle

import com.github.jk1.license.*
import com.github.jk1.license.render.ReportRenderer
import groovy.text.SimpleTemplateEngine
import groovy.text.StreamingTemplateEngine

class NewInventoryHtmlReportRenderer implements ReportRenderer {
    private final String name
    private final String fileName
    private final Map<String, DeclaredModuleInfo> declared
    private final Map<String, String> colors = [
            accent   : '#F37726',
            primary  : 'white',
            accentBg : '#616262',
            primaryBg: '#989798',
            darkText : '#4E4E4E',
            lightText: '#e8e5e5',
    ]

    private Writer output
    private int counter

    NewInventoryHtmlReportRenderer(String fileName = 'index.html', String name = null, File declarationsFileName = null, Map<String, String> colors = [:]) {
        this.name = name
        this.fileName = fileName

        if (declarationsFileName)
            declared = InventoryReportRenderer.parseDeclarations(declarationsFileName.newInputStream())
        else
            declared = [:]

        this.colors.putAll(colors)
    }

    @Override
    void render(ProjectData data) {
        this.counter = 0

        def project = data.project
        def name = project.name
        LicenseReportExtension config = project.licenseReport
        def outFile = new File(config.outputDir, fileName)

        def stylesheet = NewInventoryHtmlReportRenderer.class.getResourceAsStream("/license-report.template.css").withReader {
            new SimpleTemplateEngine()
                    .createTemplate(it)
                    .make(this.colors)
                    .writeTo(new StringWriter())
                    .toString()
        }
        def template = NewInventoryHtmlReportRenderer.class.getResourceAsStream("/license-report.template.html").withReader {
            new StreamingTemplateEngine().createTemplate(it)
        }

        def binding = [
                stylesheet             : stylesheet,
                name                   : name,
                project                : project,
                inventory              : InventoryReportRenderer.collectModulesByLicenseName(data, declared),
                externalInventories    : InventoryReportRenderer.collectModulesByLicenseFromImported(data),
                serializeHref          : { String... values ->
                    values.findAll { it != null }.collect { it.replaceAll(/\s/, '_') }.join('_')
                },
                printDependency        : this.&printDependency,
                printImportedDependency: this.&printImportedDependency,
        ]

        outFile.withWriter {
            template.make(binding).writeTo(it)
        }
    }

    void tag(Map<String, String> attrs = [:], String name, def children) {
        output << "<$name ${attrs.collect { k, v -> "$k=\"$v\"" }.join(" ")}>\n"
        if (children.respondsTo("call"))
            children.call()
        else
            text(children)
        output << "</$name>\n"
    }

    void div(Map<String, String> attrs = [:], def children) {
        tag(attrs, "div", children)
    }

    void p(Map<String, String> attrs = [:], def children) {
        tag(attrs, "p", children)
    }

    void a(Map<String, String> attrs = [:], def children) {
        tag(attrs, "a", children)
    }

    void strong(Map<String, String> attrs = [:], def children) {
        tag(attrs, "strong", children)
    }

    void ul(Map<String, String> attrs = [:], def children) {
        tag(attrs, "ul", children)
    }

    void li(Map<String, String> attrs = [:], def children) {
        tag(attrs, "li", children)
    }

    void text(def contents) {
        if (contents != null)
            output << String.valueOf(contents)
    }

    void renderDependencyProperty(String label, def children) {
        div(class: "dependency-prop") {
            tag("label") { text(label) }
            div(class: "dependency-value", children)
        }
    }

    void renderDependencyTitle(ModuleData data) {
        p(class: "title") {
            strong(class: "index", "${++counter}.")

            if (data.group) {
                strong("Group: ")
                text(data.group)
            }

            if (data.name) {
                strong("Name: ")
                text(data.name)
            }

            if (data.version) {
                strong("Version: ")
                text(data.version)
            }
        }
    }

    void renderDependencyTitle(ImportedModuleData data) {
        p(class: "title") {
            strong(class: "index", ++counter)

            if (data.name) {
                strong("Name: ")
                text(data.name)
            }

            if (data.version) {
                strong("Version: ")
                text(data.version)
            }
        }
    }

    void renderDependencyProjectUrl(ModuleData data) {
        String coords = data.with { "$group:$name:$version" }

        ManifestData manifest = data.manifests.isEmpty() ? null : data.manifests.first()
        PomData pomData = data.poms.isEmpty() ? null : data.poms.first()

        if (manifest?.url && pomData?.projectUrl && manifest.url == pomData.projectUrl) {
            renderDependencyProperty("Project URL") {
                a(href: manifest.url, { text(manifest.url) })
            }
        } else if (manifest?.url || pomData?.projectUrl) {
            if (manifest?.url) {
                renderDependencyProperty("Manifest Project URL") {
                    a(href: manifest.url, { text(manifest.url) })
                }
            }

            if (pomData?.projectUrl) {
                renderDependencyProperty("POM Project URL") {
                    a(href: pomData.projectUrl, { text(pomData.projectUrl) })
                }
            }
        } else if (declared.containsKey(coords)) {
            renderDependencyProperty("Project URL") {
                a(href: declared[coords].projectUrl, { text(declared[coords].projectUrl) })
            }
        }
    }

    void renderReferencedLicenses(ModuleData data) {
        String coords = data.with { "$group:$name:$version" }

        ManifestData manifest = data.manifests.isEmpty() ? null : data.manifests.first()
        PomData pomData = data.poms.isEmpty() ? null : data.poms.first()

        if (manifest?.license || pomData?.licenses) {
            if (manifest?.license) {
                if (manifest.license.startsWith("http")) {
                    renderDependencyProperty("Manifest license URL") {
                        a(href: manifest.license, { text(manifest.license) })
                    }
                } else if (manifest.hasPackagedLicense) {
                    renderDependencyProperty("Packaged License File") {
                        a(href: manifest.license, { text(manifest.url) })
                    }
                } else {
                    renderDependencyProperty("Manifest License") {
                        text("${manifest.license} (Not Packaged)")
                    }
                }
            }

            if (pomData?.licenses) {
                pomData.licenses.each { License license ->
                    if (license.url) {
                        renderDependencyProperty("POM License") {
                            text("${license.name} - ")
                            if (license.url.startsWith("http"))
                                a(href: license.url, { text(license.url) })
                            else
                                text(license.url)
                        }
                    } else {
                        renderDependencyProperty("POM License", { text(license.name) })
                    }
                }
            }
        } else if (declared.containsKey(coords)) {
            renderDependencyProperty("License URL") {
                a(href: declared[coords].licenseUrl, { text(declared[coords].license) })
            }
        }
    }

    void renderIncludedLicenses(ModuleData data) {
        if (!data.licenseFiles.isEmpty() && !data.licenseFiles.first().fileDetails.isEmpty()) {
            renderDependencyProperty("Embedded license files") {
                ul {
                    data.licenseFiles.first().fileDetails.each {
                        def file = it.file
                        li {
                            a(href: file, { text(file) })
                        }
                    }
                }
            }
        }
    }

    private void printDependency(Writer out, ModuleData data) {
        this.output = out
        div(class: "dependency") {
            renderDependencyTitle(data)
            renderDependencyProjectUrl(data)
            renderReferencedLicenses(data)
            renderIncludedLicenses(data)
        }
    }

    private printImportedDependency(Writer out, ImportedModuleData data) {
        this.output = out
        div(class: "dependency") {
            renderDependencyTitle(data)
            renderDependencyProperty("Project URL") {
                a(href: data.projectUrl, { text(data.projectUrl) })
            }
            renderDependencyProperty("License URL") {
                a(href: data.licenseUrl, { text(data.license) })
            }
        }
    }
}
