package io.github.spencerpark.gradle

import com.github.jk1.license.*
import groovy.json.JsonParserType
import groovy.json.JsonSlurper

abstract class InventoryReportRenderer {
    /**
     * Collect declared from a JSON object with maven coordinates as keys and {@link DeclaredModuleInfo}
     * as values
     * @param declarations the stream to parse the json object from
     * @return the collected declarations
     */
    static Map<String, DeclaredModuleInfo> parseDeclarations(InputStream declarations) {
        def rawDeclarations = new JsonSlurper()
                .setType(JsonParserType.LAX)
                .parse(declarations)

        assert rawDeclarations instanceof Map: "Declaration spec must be a json object"

        return rawDeclarations.collectEntries([:]) { coords, spec ->
            assert spec instanceof Map: "Declaration spec for $coords is not an object"

            DeclaredModuleInfo info = new DeclaredModuleInfo()
            spec.forEach { String key, val ->
                assert val instanceof String: "Declaration spec for $coords::$key must be a string"
                assert info.hasProperty(key), "Declaration spec for $coords has unknown key $key"

                info[key] = val
            }
            assert info.projectUrl: "Declaration missing required key: projectUrl"
            assert info.license: "Declaration missing required key: license"
            assert info.licenseUrl: "Declaration missing required key: licenseUrl"

            return [(coords): info]
        }
    }

    static Map<String, List<ModuleData>> collectModulesByLicenseName(ProjectData data, Map<String, DeclaredModuleInfo> declared) {
        Map<String, List<ModuleData>> modulesByLicense = [:]

        def addModule = { String licenseName, ModuleData module ->
            String coords = module.with { "$group:$name:$version" }

            if (licenseName == "Unknown" && declared.containsKey(coords))
                licenseName = declared[coords].license

            modulesByLicense.compute(licenseName) { k, modules ->
                return (modules ?: []) << module
            }
        }

        data.allDependencies.each { module ->
            if (module.poms.isEmpty()) {
                addModule(module.licenseFiles.isEmpty() ? "Unknown" : "Embedded", module)
                return
            }

            PomData pom = module.poms.first()
            if (pom.licenses.isEmpty()) {
                addModule(module.licenseFiles.isEmpty() ? "Unknown" : "Embedded", module)
            } else {
                pom.licenses.each { License license ->
                    addModule(license.name, module)
                }
            }
        }

        return modulesByLicense
    }

    // imported modules are things declared as dependencies but not actually included via gradle means. For
    // example a javascript dependency.
    static Map<String, Map<String, List<ImportedModuleData>>> collectModulesByLicenseFromImported(ProjectData data) {
        Map<String, Map<String, List<ImportedModuleData>>> externalByModulesByLicense = [:]

        data.importedModules.each { ImportedModuleBundle module ->
            Map<String, List<ImportedModuleData>> modulesByLicense = [:]

            module.modules.each { ImportedModuleData moduleData ->
                modulesByLicense.compute(moduleData.license) { k, modules ->
                    return (modules ?: []) << moduleData
                }
            }

            externalByModulesByLicense[module.name] = modulesByLicense
        }

        return externalByModulesByLicense
    }
}
