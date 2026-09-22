package com.devops

class ConfigParser implements Serializable {
    static Map<String, String> parseConfig(String fileContent) {
        Map<String, String> configMap = [:]
        fileContent.eachLine { line ->
            line = line.trim()
            if (line && !line.startsWith("#") && line.contains("=")) {
                def parts = line.split("=", 2)
                configMap[parts[0].trim()] = parts[1].trim()
            }
        }
        return configMap
    }
}
